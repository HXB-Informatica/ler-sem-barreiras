package pt.lersembarreiras.studio.audio

import java.io.File
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioInputStream
import javax.sound.sampled.AudioSystem

object DesktopAudioImport {
    fun importToProject(source: File, originalDest: File, recognitionDest: File) {
        require(source.isFile) { "O ficheiro de áudio não existe." }
        val decoded = decodePcm(source)
        decoded.use { stream ->
            val format = stream.format
            val bits = if (format.sampleSizeInBits <= 0) 16 else format.sampleSizeInBits
            val rate = format.sampleRate.toInt().coerceAtLeast(8_000)
            StreamingWavWriter(originalDest, rate, bits.coerceAtMost(24), format.channels.coerceAtLeast(1)).use { writer ->
                val buffer = ByteArray(8192)
                while (true) {
                    val read = stream.read(buffer)
                    if (read <= 0) break
                    writer.write(buffer, 0, read)
                }
            }
        }
        RecognitionAudio.writeMono16k(originalDest, recognitionDest)
    }

    private fun decodePcm(source: File): AudioInputStream {
        val original = AudioSystem.getAudioInputStream(source)
        val sourceFormat = original.format
        val pcm = AudioFormat(
            AudioFormat.Encoding.PCM_SIGNED,
            sourceFormat.sampleRate,
            16,
            sourceFormat.channels.coerceAtLeast(1),
            2 * sourceFormat.channels.coerceAtLeast(1),
            sourceFormat.sampleRate,
            false,
        )
        return if (sourceFormat.encoding == AudioFormat.Encoding.PCM_SIGNED && sourceFormat.sampleSizeInBits in setOf(16, 24)) {
            original
        } else {
            runCatching { AudioSystem.getAudioInputStream(pcm, original) }.getOrElse {
                original.close()
                throw IllegalArgumentException(
                    "Formato não suportado (${sourceFormat}). Importe WAV, AIFF ou AU em PCM.",
                    it,
                )
            }
        }
    }
}
