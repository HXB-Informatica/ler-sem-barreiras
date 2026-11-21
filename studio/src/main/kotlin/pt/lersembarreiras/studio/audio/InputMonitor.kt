package pt.lersembarreiras.studio.audio

import java.util.concurrent.atomic.AtomicBoolean
import javax.sound.sampled.LineUnavailableException
import javax.sound.sampled.TargetDataLine
import kotlin.concurrent.thread

/** Lê o microfone só para o medidor, sem gravar. Fecha a linha antes da captura real. */
class InputMonitor {
    private val running = AtomicBoolean(false)
    @Volatile private var thread: Thread? = null
    @Volatile private var line: TargetDataLine? = null
    var onLevel: (LevelReading) -> Unit = {}

    fun start(device: AudioDevice?) {
        stop()
        val negotiated = AudioDevices.negotiate(device) ?: return
        val opened = try {
            AudioDevices.openLine(device, negotiated.audioFormat)
        } catch (_: LineUnavailableException) {
            return
        } catch (_: SecurityException) {
            return
        }
        running.set(true)
        line = opened
        opened.start()
        thread = thread(name = "lsb-studio-meter", isDaemon = true) {
            val buffer = ByteArray(opened.format.frameSize * 1024)
            try {
                while (running.get()) {
                    val read = opened.read(buffer, 0, buffer.size)
                    if (read <= 0) continue
                    onLevel(
                        LevelMeter.read(
                            buffer,
                            read,
                            negotiated.bitsPerSample,
                            negotiated.channels,
                        ),
                    )
                }
            } catch (_: Exception) {
            } finally {
                runCatching { opened.stop() }
                runCatching { opened.close() }
            }
        }
    }

    fun stop() {
        running.set(false)
        runCatching { line?.close() }
        thread?.join(500)
        thread = null
        line = null
    }
}
