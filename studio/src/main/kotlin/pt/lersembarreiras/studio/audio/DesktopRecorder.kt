package pt.lersembarreiras.studio.audio

import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.LineEvent
import javax.sound.sampled.LineListener
import javax.sound.sampled.LineUnavailableException
import javax.sound.sampled.TargetDataLine
import kotlin.concurrent.thread

enum class RecorderState { IDLE, RECORDING, PAUSED, STOPPING }

data class RecorderStatus(
    val state: RecorderState = RecorderState.IDLE,
    val durationMs: Long = 0,
    val level: LevelReading = LevelReading(0f, 0f, false),
    val clipped: Boolean = false,
    val error: String? = null,
    val deviceName: String? = null,
    val format: NegotiatedFormat? = null,
)

class DesktopRecorder {
    private val recording = AtomicBoolean(false)
    private val paused = AtomicBoolean(false)
    private val disconnected = AtomicBoolean(false)
    @Volatile private var captureThread: Thread? = null
    @Volatile private var line: TargetDataLine? = null
    @Volatile private var writer: StreamingWavWriter? = null
    @Volatile var status: RecorderStatus = RecorderStatus()
        private set
    var onStatus: (RecorderStatus) -> Unit = {}

    fun start(device: AudioDevice?, destination: File) {
        if (recording.get()) return
        disconnected.set(false)
        paused.set(false)
        val negotiated = AudioDevices.negotiate(device)
            ?: throw LineUnavailableException("Nenhum formato PCM suportado neste microfone.")
        val target = File(destination.parentFile, destination.name + ".part")
        if (target.exists()) target.delete()
        val wav = StreamingWavWriter(
            file = target,
            sampleRate = negotiated.sampleRate,
            bitsPerSample = (negotiated.audioFormat.frameSize / negotiated.channels) * 8,
            channels = negotiated.channels,
        )
        val opened = try {
            AudioDevices.openLine(device, negotiated.audioFormat)
        } catch (error: SecurityException) {
            wav.close()
            target.delete()
            throw LineUnavailableException("O Windows bloqueou o microfone. Autorize o acesso ao microfone nas definições de privacidade.")
        } catch (error: LineUnavailableException) {
            wav.close()
            target.delete()
            throw error
        }
        val listener = LineListener { event ->
            if (event.type == LineEvent.Type.CLOSE || event.type == LineEvent.Type.STOP && recording.get() && !paused.get()) {
                if (event.type == LineEvent.Type.CLOSE) disconnected.set(true)
            }
        }
        opened.addLineListener(listener)
        writer = wav
        line = opened
        recording.set(true)
        opened.start()
        publish(
            RecorderStatus(
                state = RecorderState.RECORDING,
                deviceName = device?.name ?: "Predefinição do sistema",
                format = negotiated,
            ),
        )
        captureThread = thread(name = "lsb-studio-capture", isDaemon = true) {
            val buffer = ByteArray(opened.bufferSize.coerceAtLeast(opened.format.frameSize * 1024))
            var everClipped = false
            try {
                while (recording.get()) {
                    if (paused.get()) {
                        Thread.sleep(40)
                        continue
                    }
                    val read = opened.read(buffer, 0, buffer.size)
                    if (read < 0 || disconnected.get()) {
                        publish(status.copy(error = "O microfone foi desligado durante a gravação.", state = RecorderState.STOPPING))
                        break
                    }
                    if (read == 0) continue
                    wav.write(buffer, 0, read)
                    val level = LevelMeter.read(buffer, read, wav.let { negotiated.bitsPerSample }, negotiated.channels)
                    if (level.clipping) everClipped = true
                    publish(
                        status.copy(
                            state = if (paused.get()) RecorderState.PAUSED else RecorderState.RECORDING,
                            durationMs = wav.durationMs(),
                            level = level,
                            clipped = everClipped,
                            format = negotiated,
                            deviceName = status.deviceName,
                        ),
                    )
                }
            } catch (error: Exception) {
                publish(status.copy(error = error.message ?: "Falha na captura.", state = RecorderState.STOPPING))
            } finally {
                runCatching { opened.stop() }
                runCatching { opened.close() }
                wav.close()
                recording.set(false)
                paused.set(false)
                line = null
                writer = null
            }
        }
    }

    fun pause() {
        if (!recording.get()) return
        paused.set(true)
        line?.stop()
        publish(status.copy(state = RecorderState.PAUSED))
    }

    fun resume() {
        if (!recording.get()) return
        paused.set(false)
        line?.start()
        publish(status.copy(state = RecorderState.RECORDING))
    }

    fun stop(finalFile: File): File? {
        recording.set(false)
        captureThread?.join(8_000)
        captureThread = null
        val part = File(finalFile.parentFile, finalFile.name + ".part")
        if (!part.exists() || part.length() < 44) {
            part.delete()
            publish(RecorderStatus(state = RecorderState.IDLE, error = status.error ?: "A gravação está vazia."))
            return null
        }
        if (finalFile.exists()) {
            val backup = File(finalFile.parentFile, "audio-original.previous.wav")
            backup.delete()
            finalFile.renameTo(backup)
        }
        if (!part.renameTo(finalFile)) {
            part.copyTo(finalFile, overwrite = true)
            part.delete()
        }
        publish(
            status.copy(
                state = RecorderState.IDLE,
                durationMs = StreamingWavWriter.inspect(finalFile).durationMs,
            ),
        )
        return finalFile
    }

    fun cancel() {
        recording.set(false)
        captureThread?.join(2_000)
        line?.let { runCatching { it.close() } }
        writer?.let { runCatching { it.close() } }
        writer = null
        publish(RecorderStatus(state = RecorderState.IDLE))
    }

    val isRecording: Boolean get() = recording.get()
    val isPaused: Boolean get() = paused.get()

    private fun publish(value: RecorderStatus) {
        status = value
        onStatus(value)
    }

    companion object {
        fun javaSoundAvailable(): Boolean = runCatching { AudioSystem.getMixerInfo() }.isSuccess
    }
}
