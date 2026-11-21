package pt.lersembarreiras.mobile

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.util.Log
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.modules.core.DeviceEventManagerModule
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread
import kotlin.math.min
import kotlin.math.sqrt

class LsbAudioModule(private val ctx: ReactApplicationContext) : ReactContextBaseJavaModule(ctx) {
    private val recording = AtomicBoolean(false)
    private val playing = AtomicBoolean(false)
    private val paused = AtomicBoolean(false)
    private var recorder: AudioRecord? = null
    private var recordFile: File? = null
    private var track: AudioTrack? = null
    private var playSamples: ShortArray = ShortArray(0)
    private var playSampleRate = 16_000
    private var playOffset = 0
    private var pendingSeekMs = 0
    private var writerLatch: CountDownLatch? = null
    @Volatile private var lastRms = 0.0
    @Volatile private var lastPeak = 0.0
    @Volatile private var lastElapsedMs = 0.0

    override fun getName(): String = "LsbAudio"

    @ReactMethod
    fun addListener(eventName: String) = Unit

    @ReactMethod
    fun removeListeners(count: Int) = Unit

    @ReactMethod
    fun getLevel(promise: Promise) {
        val map = Arguments.createMap()
        map.putBoolean("recording", recording.get())
        map.putDouble("rms", lastRms)
        map.putDouble("peak", lastPeak)
        map.putDouble("elapsedMs", lastElapsedMs)
        promise.resolve(map)
    }

    private fun root(): File = File(ctx.filesDir, "lsb").apply { mkdirs() }

    @ReactMethod
    fun startRecording(path: String, promise: Promise) {
        if (recording.get()) {
            promise.reject("AUDIO", "Já existe uma gravação em curso.")
            return
        }
        try {
            stopPlayer()
            val file = PathSafety.resolve(root(), path)
            file.parentFile?.mkdirs()
            val capture = openCapture()
            recorder = capture.recorder
            recordFile = file
            lastRms = 0.0
            lastPeak = 0.0
            lastElapsedMs = 0.0
            writerLatch = CountDownLatch(1)
            recording.set(true)
            capture.recorder.startRecording()
            if (capture.recorder.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                recording.set(false)
                capture.recorder.release()
                recorder = null
                promise.reject("AUDIO", "O microfone não entrou em gravação neste emulador.")
                return
            }
            Log.i(TAG, "Gravação iniciada ${capture.sampleRate} Hz, ${capture.channels} canal(is), source=${capture.source}")
            thread(name = "lsb-wav-record") {
                try {
                    writeWav(file, capture)
                } finally {
                    writerLatch?.countDown()
                }
            }
            promise.resolve(null)
        } catch (error: SecurityException) {
            recording.set(false)
            promise.reject("AUDIO", "É necessária permissão de microfone para gravar.", error)
        } catch (error: Exception) {
            recording.set(false)
            promise.reject("AUDIO", error.message, error)
        }
    }

    @ReactMethod
    fun stopRecording(promise: Promise) {
        recording.set(false)
        val file = recordFile
        val latch = writerLatch
        runCatching { recorder?.stop() }
        runCatching { latch?.await(3, TimeUnit.SECONDS) }
        runCatching { recorder?.release() }
        recorder = null
        if (file == null || !file.isFile || file.length() < 1000) {
            promise.reject("AUDIO", "A gravação não produziu áudio utilizável. Confirme o microfone do computador.")
            return
        }
        val wav = runCatching { WavPcm.readMonoPcm(file) }.getOrNull()
        val duration = if (wav == null || wav.sampleRate <= 0) {
            0.0
        } else {
            wav.samples.size * 1000.0 / wav.sampleRate
        }
        val map = Arguments.createMap()
        map.putDouble("durationMs", duration)
        map.putString("path", file.relativeTo(root()).invariantSeparatorsPath)
        promise.resolve(map)
    }

    @ReactMethod
    fun play(path: String, promise: Promise) {
        try {
            stopPlayer()
            val file = PathSafety.resolve(root(), path)
            val wav = WavPcm.readMonoPcm(file)
            if (wav.samples.isEmpty()) {
                promise.reject("AUDIO", "O áudio está vazio.")
                return
            }
            val nativeRate = AudioTrack.getNativeOutputSampleRate(AudioManager.STREAM_MUSIC).let { rate ->
                if (rate > 0) rate else 48_000
            }
            playSamples = WavPcm.resample(wav.samples, wav.sampleRate, nativeRate)
            playSampleRate = nativeRate
            playOffset = ((pendingSeekMs.toLong() * nativeRate) / 1000L).toInt()
                .coerceIn(0, playSamples.lastIndex)
            pendingSeekMs = 0
            paused.set(false)
            playing.set(true)
            startPlayback()
            promise.resolve(null)
        } catch (error: Exception) {
            playing.set(false)
            promise.reject("AUDIO", error.message, error)
        }
    }

    @ReactMethod
    fun pause(promise: Promise) {
        paused.set(true)
        runCatching { track?.pause() }
        promise.resolve(null)
    }

    @ReactMethod
    fun stop(promise: Promise) {
        stopPlayer()
        promise.resolve(null)
    }

    @ReactMethod
    fun seek(positionMs: Double, promise: Promise) {
        pendingSeekMs = positionMs.toInt().coerceAtLeast(0)
        if (playSampleRate > 0 && playSamples.isNotEmpty()) {
            playOffset = ((pendingSeekMs.toLong() * playSampleRate) / 1000L).toInt()
                .coerceIn(0, playSamples.lastIndex)
        }
        promise.resolve(null)
    }

    @ReactMethod
    fun status(promise: Promise) {
        val map = Arguments.createMap()
        val rate = playSampleRate.coerceAtLeast(1)
        map.putDouble("positionMs", playOffset * 1000.0 / rate)
        map.putDouble("durationMs", playSamples.size * 1000.0 / rate)
        map.putBoolean("playing", playing.get() && !paused.get())
        promise.resolve(map)
    }

    private fun startPlayback() {
        thread(name = "lsb-wav-play") {
            val samples = playSamples
            val rate = playSampleRate
            val minBuf = AudioTrack.getMinBufferSize(
                rate,
                AudioFormat.CHANNEL_OUT_STEREO,
                AudioFormat.ENCODING_PCM_16BIT,
            )
            val audio = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setLegacyStreamType(AudioManager.STREAM_MUSIC)
                        .build(),
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(rate)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                        .build(),
                )
                .setBufferSizeInBytes((minBuf * 4).coerceAtLeast(16_384))
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
            track = audio
            audio.play()
            val frames = (minBuf / 4).coerceAtLeast(2048)
            val mono = ShortArray(frames)
            val stereo = ShortArray(frames * 2)
            while (playing.get() && playOffset < samples.size) {
                if (paused.get()) {
                    runCatching { audio.pause() }
                    while (paused.get() && playing.get()) {
                        Thread.sleep(20)
                    }
                    if (!playing.get()) {
                        break
                    }
                    runCatching { audio.play() }
                }
                val n = min(mono.size, samples.size - playOffset)
                System.arraycopy(samples, playOffset, mono, 0, n)
                var s = 0
                for (i in 0 until n) {
                    stereo[s] = mono[i]
                    stereo[s + 1] = mono[i]
                    s += 2
                }
                val written = audio.write(stereo, 0, n * 2, AudioTrack.WRITE_BLOCKING)
                if (written > 0) {
                    playOffset += written / 2
                } else if (written < 0) {
                    break
                }
            }
            playing.set(false)
            runCatching { audio.stop() }
            runCatching { audio.release() }
            if (track === audio) {
                track = null
            }
        }
    }

    private fun stopPlayer() {
        playing.set(false)
        paused.set(false)
        runCatching { track?.pause() }
        runCatching { track?.stop() }
        runCatching { track?.release() }
        track = null
        playOffset = 0
    }

    private fun openCapture(): CaptureConfig {
        val rates = intArrayOf(44_100, 48_000, 16_000)
        val channels = intArrayOf(AudioFormat.CHANNEL_IN_STEREO, AudioFormat.CHANNEL_IN_MONO)
        val sources = intArrayOf(
            MediaRecorder.AudioSource.MIC,
            MediaRecorder.AudioSource.DEFAULT,
            MediaRecorder.AudioSource.UNPROCESSED,
            MediaRecorder.AudioSource.CAMCORDER,
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
        )
        for (source in sources) {
            for (channel in channels) {
                for (rate in rates) {
                    val minBuf = AudioRecord.getMinBufferSize(rate, channel, AudioFormat.ENCODING_PCM_16BIT)
                    if (minBuf <= 0) {
                        continue
                    }
                    val candidate = AudioRecord(source, rate, channel, AudioFormat.ENCODING_PCM_16BIT, minBuf * 8)
                    if (candidate.state == AudioRecord.STATE_INITIALIZED) {
                        val nch = if (channel == AudioFormat.CHANNEL_IN_STEREO) 2 else 1
                        return CaptureConfig(candidate, rate, nch, source)
                    }
                    candidate.release()
                }
            }
        }
        throw IllegalStateException("Não foi possível iniciar o microfone neste dispositivo.")
    }

    private fun writeWav(file: File, capture: CaptureConfig) {
        val raw = ByteArrayOutputStream()
        val buffer = ShortArray(4096)
        var lastEmit = 0L
        val startedAt = System.currentTimeMillis()
        var peakSeen = 0
        fun consume(read: Int) {
            if (read <= 0) {
                return
            }
            val bytes = ByteBuffer.allocate(read * 2).order(ByteOrder.LITTLE_ENDIAN)
            var sumSquares = 0.0
            var peak = 0
            for (i in 0 until read) {
                val sample = buffer[i]
                bytes.putShort(sample)
                val abs = kotlin.math.abs(sample.toInt())
                if (abs > peak) peak = abs
                val norm = sample / 32768.0
                sumSquares += norm * norm
            }
            raw.write(bytes.array())
            if (peak > peakSeen) peakSeen = peak
            val now = System.currentTimeMillis()
            if (now - lastEmit >= 50) {
                lastEmit = now
                val rms = sqrt(sumSquares / read)
                emitLevel(rms, peak / 32768.0, now - startedAt)
            }
        }
        while (recording.get()) {
            consume(capture.recorder.read(buffer, 0, buffer.size))
        }
        for (drain in 0 until 4) {
            val leftover = capture.recorder.read(buffer, 0, buffer.size)
            if (leftover <= 0) {
                break
            }
            consume(leftover)
        }
        val pcm = shortsFromBytes(raw.toByteArray())
        val mono = WavPcm.toMono(pcm, capture.channels)
        val normalized = WavPcm.normalize(mono)
        file.writeBytes(wavBytes(normalized, capture.sampleRate))
        Log.i(
            TAG,
            "Gravação gravada: pico=$peakSeen amostras=${normalized.size} rate=${capture.sampleRate} ch=${capture.channels}",
        )
    }

    private fun emitLevel(rms: Double, peak: Double, elapsedMs: Long) {
        lastRms = rms.coerceIn(0.0, 1.0)
        lastPeak = peak.coerceIn(0.0, 1.0)
        lastElapsedMs = elapsedMs.toDouble()
        ctx.runOnUiQueueThread {
            if (!ctx.hasActiveReactInstance()) {
                return@runOnUiQueueThread
            }
            val payload = Arguments.createMap()
            payload.putDouble("rms", lastRms)
            payload.putDouble("peak", lastPeak)
            payload.putDouble("elapsedMs", lastElapsedMs)
            ctx.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
                .emit("LsbAudioLevel", payload)
        }
    }

    private data class CaptureConfig(
        val recorder: AudioRecord,
        val sampleRate: Int,
        val channels: Int,
        val source: Int,
    )

    companion object {
        private const val TAG = "LsbAudio"

        private fun shortsFromBytes(bytes: ByteArray): ShortArray {
            val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
            val out = ShortArray(buffer.remaining())
            buffer.get(out)
            return out
        }

        private fun wavBytes(samples: ShortArray, sampleRate: Int): ByteArray {
            val dataBytes = samples.size * 2
            val buffer = ByteBuffer.allocate(44 + dataBytes).order(ByteOrder.LITTLE_ENDIAN)
            buffer.put("RIFF".toByteArray(Charsets.US_ASCII))
            buffer.putInt(36 + dataBytes)
            buffer.put("WAVE".toByteArray(Charsets.US_ASCII))
            buffer.put("fmt ".toByteArray(Charsets.US_ASCII))
            buffer.putInt(16)
            buffer.putShort(1)
            buffer.putShort(1)
            buffer.putInt(sampleRate)
            buffer.putInt(sampleRate * 2)
            buffer.putShort(2)
            buffer.putShort(16)
            buffer.put("data".toByteArray(Charsets.US_ASCII))
            buffer.putInt(dataBytes)
            samples.forEach { buffer.putShort(it) }
            return buffer.array()
        }
    }
}
