package pt.lersembarreiras.studio.audio

import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
import javax.sound.sampled.Mixer
import javax.sound.sampled.TargetDataLine

data class AudioDevice(
    val id: String,
    val name: String,
    val mixerInfo: Mixer.Info,
) {
    val isUsb: Boolean
        get() {
            val n = name.lowercase()
            return n.contains("usb") || n.contains("hyperx") ||
                (n.contains("microfone") && !n.contains("principal"))
        }
}

data class NegotiatedFormat(
    val sampleRate: Int,
    val bitsPerSample: Int,
    val channels: Int,
    val audioFormat: AudioFormat,
    val preferred: Boolean,
    val fallbackReason: String?,
) {
    val description: String
        get() = "${sampleRate / 1000f} kHz / $bitsPerSample bits / ${if (channels == 1) "mono" else "$channels canais"} PCM"
}

object AudioDevices {
    fun listInputs(): List<AudioDevice> {
        return AudioSystem.getMixerInfo().mapNotNull { info ->
            val mixer = runCatching { AudioSystem.getMixer(info) }.getOrNull() ?: return@mapNotNull null
            val hasTarget = mixer.targetLineInfo.any { it.lineClass == TargetDataLine::class.java }
            if (!hasTarget) null else AudioDevice(id = info.name + "|" + info.vendor, name = info.name, mixerInfo = info)
        }
    }

    fun negotiate(device: AudioDevice?): NegotiatedFormat? {
        val mixer = if (device == null) null else runCatching { AudioSystem.getMixer(device.mixerInfo) }.getOrNull()
        val candidates = listOf(
            Triple(48_000, 24, 1),
            Triple(48_000, 24, 2),
            Triple(48_000, 16, 1),
            Triple(48_000, 16, 2),
            Triple(44_100, 24, 1),
            Triple(44_100, 16, 1),
            Triple(44_100, 16, 2),
            Triple(16_000, 16, 1),
        )
        candidates.forEachIndexed { index, (rate, bits, channels) ->
            val format = AudioFormat(rate.toFloat(), bits, channels, true, false)
            val info = DataLine.Info(TargetDataLine::class.java, format)
            val supported = if (mixer != null) {
                mixer.isLineSupported(info)
            } else {
                AudioSystem.isLineSupported(info)
            }
            if (supported) {
                val preferred = rate == 48_000 && bits == 24
                val reason = if (preferred) {
                    null
                } else {
                    "O dispositivo não ofereceu 48 kHz / 24 bits. A usar ${rate / 1000} kHz / $bits bits."
                }
                return NegotiatedFormat(rate, bits, channels, format, preferred, reason)
            }
            if (index == 0 && mixer != null) {
                // continue fallbacks
            }
        }
        return null
    }

    fun openLine(device: AudioDevice?, format: AudioFormat): TargetDataLine {
        val info = DataLine.Info(TargetDataLine::class.java, format)
        val line = if (device == null) {
            AudioSystem.getLine(info) as TargetDataLine
        } else {
            AudioSystem.getMixer(device.mixerInfo).getLine(info) as TargetDataLine
        }
        val bufferBytes = (format.frameSize * format.sampleRate * 0.2f).toInt().coerceAtLeast(format.frameSize * 4096)
        line.open(format, bufferBytes)
        return line
    }
}
