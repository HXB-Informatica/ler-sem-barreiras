package pt.lersembarreiras.lsb

import java.nio.ByteBuffer
import java.nio.ByteOrder

internal object WavProbe {
    fun durationMs(bytes: ByteArray): Long {
        if (bytes.size < 44) throw LsbException("O áudio do pacote está incompleto.")
        val ascii = { offset: Int -> bytes.copyOfRange(offset, offset + 4).toString(Charsets.US_ASCII) }
        if (ascii(0) != "RIFF" || ascii(8) != "WAVE") {
            throw LsbException("O áudio do pacote não é um WAV PCM válido.")
        }
        var offset = 12
        var sampleRate = 0
        var channels = 0
        var bits = 0
        var dataBytes = 0
        while (offset + 8 <= bytes.size) {
            val id = bytes.copyOfRange(offset, offset + 4).toString(Charsets.US_ASCII)
            val size = ByteBuffer.wrap(bytes, offset + 4, 4).order(ByteOrder.LITTLE_ENDIAN).int
            offset += 8
            when (id) {
                "fmt " -> {
                    if (offset + 16 > bytes.size) break
                    val buf = ByteBuffer.wrap(bytes, offset, 16).order(ByteOrder.LITTLE_ENDIAN)
                    val format = buf.short.toInt()
                    if (format != 1) throw LsbException("O áudio tem de ser PCM sem compressão.")
                    channels = buf.short.toInt()
                    sampleRate = buf.int
                    buf.int
                    buf.short
                    bits = buf.short.toInt()
                }
                "data" -> {
                    dataBytes = size.coerceAtLeast(0)
                    break
                }
            }
            offset += size + size % 2
        }
        val frame = (bits / 8) * channels.coerceAtLeast(1)
        if (sampleRate <= 0 || frame <= 0) throw LsbException("O cabeçalho WAV está danificado.")
        return dataBytes.toLong() * 1000L / (sampleRate.toLong() * frame)
    }
}
