package pt.lersembarreiras.mobile

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import android.util.Log
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.modules.core.DeviceEventManagerModule
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.net.InetSocketAddress
import java.net.Socket
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
    private var playThread: Thread? = null
    private var playGeneration = 0
    private var hostSocket: Socket? = null
    private val stateLock = Any()
    @Volatile private var lastRms = 0.0
    @Volatile private var lastPeak = 0.0
    @Volatile private var lastElapsedMs = 0.0
    @Volatile private var playbackAnchorMs = 0L

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

    override fun invalidate() {
        recording.set(false)
        stopPlayer()
        finishWriter(2)
        super.invalidate()
    }

    @ReactMethod
    fun startRecording(path: String, promise: Promise) {
        try {
            val capture: CaptureConfig
            val file: File
            synchronized(stateLock) {
                stopPlayer()
                if (recording.get() || recorder != null || hostSocket != null) {
                    finishWriterLocked()
                }
                file = PathSafety.resolve(root(), path)
                file.parentFile?.mkdirs()
                if (startHostMicrophone(file)) {
                    promise.resolve(null)
                    return
                }
                capture = openCapture()
                recorder = capture.recorder
                recordFile = file
                lastRms = 0.0
                lastPeak = 0.0
                lastElapsedMs = 0.0
                writerLatch = CountDownLatch(1)
                recording.set(true)
                capture.recorder.startRecording()
                disableProcessing(capture.recorder.audioSessionId)
                if (capture.recorder.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                    recording.set(false)
                    capture.recorder.release()
                    recorder = null
                    writerLatch?.countDown()
                    writerLatch = null
                    promise.reject("AUDIO", "O microfone não entrou em gravação neste emulador.")
                    return
                }
            }
            Log.i(
                TAG,
                "Gravação iniciada pedido=${capture.sampleRate} Hz real=${capture.recorder.sampleRate} Hz, ${capture.channels} canal(is), source=${capture.source}",
            )
            thread(name = "lsb-wav-record") {
                try {
                    writeWav(file, capture)
                } catch (error: Exception) {
                    Log.e(TAG, "Falha a gravar WAV", error)
                } finally {
                    recording.set(false)
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
        val file: File?
        synchronized(stateLock) {
            recording.set(false)
            runCatching { recorder?.stop() }
            runCatching { hostSocket?.close() }
            hostSocket = null
            file = recordFile
            finishWriterLocked()
        }
        if (file == null || !file.isFile || file.length() < 1000) {
            promise.reject("AUDIO", "A gravação não produziu áudio utilizável. Confirme o microfone do computador.")
            return
        }
        val wav = runCatching { WavPcm.readMonoPcm(file) }.getOrNull()
        if (wav != null && captureWentSilent(wav.samples, wav.sampleRate)) {
            promise.reject(
                "AUDIO",
                "O emulador cortou o microfone ao fim de um instante. O ficheiro fica em silêncio e a reprodução não tem voz. O HyperX no computador está bem; falha o áudio do emulador.",
            )
            return
        }
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
            playOffset = if (playSamples.isEmpty()) {
                0
            } else {
                ((pendingSeekMs.toLong() * nativeRate) / 1000L).toInt().coerceIn(0, playSamples.lastIndex)
            }
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
        val elapsed = if (playing.get() && playbackAnchorMs > 0L) {
            (System.currentTimeMillis() - playbackAnchorMs).toDouble()
        } else {
            playOffset * 1000.0 / rate
        }
        val duration = playSamples.size * 1000.0 / rate
        map.putDouble("positionMs", elapsed.coerceIn(0.0, duration))
        map.putDouble("durationMs", playSamples.size * 1000.0 / rate)
        map.putBoolean("playing", playing.get() && !paused.get())
        promise.resolve(map)
    }

    private fun startPlayback() {
        val samples = playSamples
        val rate = playSampleRate
        val generation = ++playGeneration
        val started = thread(name = "lsb-wav-play") {
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
            raisePlaybackVolume()
            audio.setVolume(1f)
            playbackAnchorMs = System.currentTimeMillis()
            audio.play()
            val frames = (minBuf / 4).coerceAtLeast(2048)
            val mono = ShortArray(frames)
            val stereo = ShortArray(frames * 2)
            var idleWrites = 0
            while (playing.get() && playGeneration == generation && playOffset < samples.size) {
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
                    idleWrites = 0
                    playOffset += written / 2
                } else if (written < 0) {
                    Log.w(TAG, "AudioTrack.write=$written")
                    break
                } else {
                    idleWrites += 1
                    if (idleWrites > 20) {
                        break
                    }
                    Thread.sleep(10)
                }
            }
            if (playGeneration == generation && playing.get()) {
                var deadline = playbackAnchorMs + samples.size * 1000L / rate.coerceAtLeast(1)
                while (playing.get() && playGeneration == generation && System.currentTimeMillis() < deadline) {
                    if (paused.get()) {
                        val pausedAt = System.currentTimeMillis()
                        while (paused.get() && playing.get() && playGeneration == generation) {
                            Thread.sleep(20)
                        }
                        deadline += System.currentTimeMillis() - pausedAt
                        continue
                    }
                    Thread.sleep(30)
                }
            }
            if (playGeneration == generation) {
                playing.set(false)
            }
            runCatching { audio.stop() }
            runCatching { audio.release() }
            if (track === audio && playGeneration == generation) {
                track = null
            }
        }
        playThread = started
    }

    private fun raisePlaybackVolume() {
        val manager = ctx.getSystemService(AudioManager::class.java) ?: return
        val stream = AudioManager.STREAM_MUSIC
        runCatching { manager.setStreamVolume(stream, manager.getStreamMaxVolume(stream), 0) }
    }

    private fun stopPlayer() {
        playing.set(false)
        paused.set(false)
        val thread = playThread
        runCatching { track?.pause() }
        thread?.join(1_000)
        runCatching { track?.stop() }
        runCatching { track?.release() }
        track = null
        playThread = null
        playOffset = 0
    }

    private fun finishWriter(seconds: Long = 8) {
        synchronized(stateLock) {
            recording.set(false)
            runCatching { recorder?.stop() }
            finishWriterLocked(seconds)
        }
    }

    private fun finishWriterLocked(seconds: Long = 8) {
        val latch = writerLatch
        runCatching { latch?.await(seconds, TimeUnit.SECONDS) }
        runCatching { recorder?.release() }
        recorder = null
        writerLatch = null
    }

    private fun captureWentSilent(samples: ShortArray, sampleRate: Int): Boolean {
        if (sampleRate <= 0 || samples.size < sampleRate / 2) {
            return false
        }
        val window = sampleRate / 10
        var windows = 0
        var dead = 0
        var index = 0
        while (index < samples.size) {
            val end = min(samples.size, index + window)
            var peak = 0
            for (i in index until end) {
                val abs = kotlin.math.abs(samples[i].toInt())
                if (abs > peak) peak = abs
            }
            if (peak < 40) dead += 1
            windows += 1
            index = end
        }
        return windows >= 8 && dead * 100 / windows >= 70
    }

    private fun disableProcessing(sessionId: Int) {
        runCatching { if (AcousticEchoCanceler.isAvailable()) AcousticEchoCanceler.create(sessionId)?.enabled = false }
        runCatching { if (NoiseSuppressor.isAvailable()) NoiseSuppressor.create(sessionId)?.enabled = false }
        runCatching { if (AutomaticGainControl.isAvailable()) AutomaticGainControl.create(sessionId)?.enabled = false }
    }

    private fun startHostMicrophone(file: File): Boolean {
        if (!isEmulator()) {
            return false
        }
        val socket = Socket()
        return try {
            socket.connect(InetSocketAddress(HOST_MIC_HOST, HOST_MIC_PORT), 1000)
            socket.tcpNoDelay = true
            hostSocket = socket
            recordFile = file
            lastRms = 0.0
            lastPeak = 0.0
            lastElapsedMs = 0.0
            writerLatch = CountDownLatch(1)
            recording.set(true)
            Log.i(TAG, "Gravação pelo microfone do computador em $HOST_MIC_HOST:$HOST_MIC_PORT")
            thread(name = "lsb-wav-host") {
                try {
                    writeHostPcm(file, socket.getInputStream())
                } catch (error: Exception) {
                    Log.e(TAG, "Falha no microfone do computador", error)
                } finally {
                    recording.set(false)
                    runCatching { socket.close() }
                    writerLatch?.countDown()
                }
            }
            true
        } catch (error: Exception) {
            Log.w(TAG, "Microfone do computador indisponível: ${error.message}")
            runCatching { socket.close() }
            hostSocket = null
            false
        }
    }

    private fun writeHostPcm(file: File, input: InputStream) {
        val raw = ByteArrayOutputStream()
        val chunk = ByteArray(4096)
        val startedAt = System.currentTimeMillis()
        var lastEmit = 0L
        var peakSeen = 0
        while (recording.get()) {
            val read = try {
                input.read(chunk)
            } catch (error: Exception) {
                break
            }
            if (read <= 0) {
                break
            }
            val samples = read / 2
            if (samples <= 0) {
                continue
            }
            raw.write(chunk, 0, samples * 2)
            var sumSquares = 0.0
            var peak = 0
            val bytes = ByteBuffer.wrap(chunk, 0, samples * 2).order(ByteOrder.LITTLE_ENDIAN)
            repeat(samples) {
                val sample = bytes.short.toInt()
                val abs = kotlin.math.abs(sample)
                if (abs > peak) peak = abs
                val norm = sample / 32768.0
                sumSquares += norm * norm
            }
            if (peak > peakSeen) peakSeen = peak
            val now = System.currentTimeMillis()
            if (now - lastEmit >= 50) {
                lastEmit = now
                emitLevel(sqrt(sumSquares / samples), peak / 32768.0, now - startedAt)
            }
        }
        val pcm = shortsFromBytes(raw.toByteArray())
        val normalized = WavPcm.normalize(pcm)
        file.writeBytes(wavBytes(normalized, HOST_MIC_RATE))
        Log.i(TAG, "Gravação do computador: pico=$peakSeen amostras=${normalized.size}")
    }

    private fun isEmulator(): Boolean {
        return android.os.Build.HARDWARE == "ranchu" ||
            android.os.Build.HARDWARE == "goldfish" ||
            android.os.Build.FINGERPRINT.contains("generic") ||
            android.os.Build.MODEL.contains("sdk_gphone", ignoreCase = true)
    }

    private fun openCapture(): CaptureConfig {
        // No emulador o MIC a 44,1 kHz entrega um estouro e depois silêncio digital.
        // 48 kHz é a taxa do anfitrião; VOICE_RECOGNITION evita o cancelamento de eco que zera o sinal.
        val emulator = isEmulator()
        val rates = if (emulator) intArrayOf(48_000, 16_000, 44_100) else intArrayOf(44_100, 48_000, 16_000)
        val channels = intArrayOf(AudioFormat.CHANNEL_IN_MONO, AudioFormat.CHANNEL_IN_STEREO)
        val sources = if (emulator) {
            intArrayOf(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                MediaRecorder.AudioSource.UNPROCESSED,
                MediaRecorder.AudioSource.CAMCORDER,
                MediaRecorder.AudioSource.MIC,
                MediaRecorder.AudioSource.DEFAULT,
            )
        } else {
            intArrayOf(
                MediaRecorder.AudioSource.MIC,
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                MediaRecorder.AudioSource.DEFAULT,
                MediaRecorder.AudioSource.CAMCORDER,
                MediaRecorder.AudioSource.UNPROCESSED,
            )
        }
        for (source in sources) {
            for (channel in channels) {
                for (rate in rates) {
                    val minBuf = AudioRecord.getMinBufferSize(rate, channel, AudioFormat.ENCODING_PCM_16BIT)
                    if (minBuf <= 0) {
                        continue
                    }
                    val candidate =
                        runCatching {
                            AudioRecord.Builder()
                                .setAudioSource(source)
                                .setAudioFormat(
                                    AudioFormat.Builder()
                                        .setSampleRate(rate)
                                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                                        .setChannelMask(channel)
                                        .build(),
                                )
                                .setBufferSizeInBytes(minBuf * 8)
                                .build()
                        }.getOrNull()
                    if (candidate == null || candidate.state != AudioRecord.STATE_INITIALIZED) {
                        candidate?.release()
                        continue
                    }
                    val nch = if (channel == AudioFormat.CHANNEL_IN_STEREO) 2 else 1
                    return CaptureConfig(candidate, rate, nch, source)
                }
            }
        }
        throw IllegalStateException("Não foi possível iniciar o microfone neste dispositivo.")
    }

    private fun writeWav(file: File, capture: CaptureConfig) {
        val rate = capture.recorder.sampleRate.coerceAtLeast(1)
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
        var consecutiveErrors = 0
        while (recording.get()) {
            val read = capture.recorder.read(buffer, 0, buffer.size)
            if (read < 0) {
                consecutiveErrors += 1
                Log.w(TAG, "AudioRecord.read=$read ($consecutiveErrors)")
                if (consecutiveErrors >= 8) {
                    recording.set(false)
                    break
                }
                Thread.sleep(20)
                continue
            }
            consecutiveErrors = 0
            if (read == 0) {
                Thread.sleep(10)
                continue
            }
            consume(read)
        }
        for (drain in 0 until 4) {
            val leftover = capture.recorder.read(buffer, 0, buffer.size, AudioRecord.READ_NON_BLOCKING)
            if (leftover <= 0) {
                break
            }
            consume(leftover)
        }
        val pcm = shortsFromBytes(raw.toByteArray())
        val mono = WavPcm.toMono(pcm, capture.channels)
        val normalized = WavPcm.normalize(mono)
        file.writeBytes(wavBytes(normalized, rate))
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
        private const val HOST_MIC_HOST = "10.0.2.2"
        private const val HOST_MIC_PORT = 27183
        private const val HOST_MIC_RATE = 48_000

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
