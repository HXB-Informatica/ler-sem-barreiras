package pt.lersembarreiras.studio.audio

import java.io.BufferedOutputStream
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Escreve WAV PCM de forma contínua, actualizando o cabeçalho no fecho.
 * Não acumula a gravação na memória.
 */
class StreamingWavWriter(
    val file: File,
    val sampleRate: Int,
    val bitsPerSample: Int,
    val channels: Int,
) : AutoCloseable {
    private val bytesPerSample = (bitsPerSample / 8).coerceAtLeast(1)
    private val blockAlign = bytesPerSample * channels
    private val stream = BufferedOutputStream(file.outputStream())
    private var dataBytes: Long = 0
    private var closed = false

    init {
        file.parentFile?.mkdirs()
        stream.write(placeholderHeader())
        stream.flush()
    }

    @Synchronized
    fun write(bytes: ByteArray, offset: Int, length: Int) {
        check(!closed) { "O escritor WAV já foi fechado." }
        if (length <= 0) return
        stream.write(bytes, offset, length)
        dataBytes += length
    }

    fun durationMs(): Long {
        if (sampleRate <= 0 || blockAlign <= 0) return 0
        return (dataBytes * 1000L) / (sampleRate.toLong() * blockAlign)
    }

    @Synchronized
    override fun close() {
        if (closed) return
        stream.flush()
        stream.close()
        patchHeader()
        closed = true
    }

    private fun placeholderHeader(): ByteArray = header(0)

    private fun patchHeader() {
        RandomAccessFile(file, "rw").use { raf ->
            raf.seek(0)
            raf.write(header(dataBytes.toInt().coerceAtLeast(0)))
        }
    }

    private fun header(dataSize: Int): ByteArray {
        val buffer = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put("RIFF".toByteArray(Charsets.US_ASCII))
        buffer.putInt(36 + dataSize)
        buffer.put("WAVE".toByteArray(Charsets.US_ASCII))
        buffer.put("fmt ".toByteArray(Charsets.US_ASCII))
        buffer.putInt(16)
        buffer.putShort(1)
        buffer.putShort(channels.toShort())
        buffer.putInt(sampleRate)
        buffer.putInt(sampleRate * blockAlign)
        buffer.putShort(blockAlign.toShort())
        buffer.putShort(bitsPerSample.toShort())
        buffer.put("data".toByteArray(Charsets.US_ASCII))
        buffer.putInt(dataSize)
        return buffer.array()
    }

    companion object {
        fun inspect(file: File): WavProperties {
            RandomAccessFile(file, "r").use { raf ->
                require(raf.length() >= 44) { "WAV demasiado pequeno." }
                val id = ByteArray(4)
                raf.readFully(id)
                require(String(id, Charsets.US_ASCII) == "RIFF")
                raf.skipBytes(4)
                raf.readFully(id)
                require(String(id, Charsets.US_ASCII) == "WAVE")
                var sampleRate = 0
                var bits = 0
                var channels = 0
                var dataSize = 0
                while (raf.filePointer + 8 <= raf.length()) {
                    raf.readFully(id)
                    val sizeBytes = ByteArray(4)
                    raf.readFully(sizeBytes)
                    val size = ByteBuffer.wrap(sizeBytes).order(ByteOrder.LITTLE_ENDIAN).int
                    when (String(id, Charsets.US_ASCII)) {
                        "fmt " -> {
                            val fmt = ByteArray(size)
                            raf.readFully(fmt)
                            val buf = ByteBuffer.wrap(fmt).order(ByteOrder.LITTLE_ENDIAN)
                            buf.short
                            channels = buf.short.toInt()
                            sampleRate = buf.int
                            buf.int
                            buf.short
                            bits = buf.short.toInt()
                            if (size % 2 == 1) raf.skipBytes(1)
                        }
                        "data" -> {
                            dataSize = size
                            break
                        }
                        else -> raf.skipBytes(size + size % 2)
                    }
                }
                val frameBytes = (bits / 8) * channels.coerceAtLeast(1)
                val frames = if (frameBytes == 0) 0 else dataSize / frameBytes
                val durationMs = if (sampleRate == 0) 0 else frames * 1000L / sampleRate
                return WavProperties(sampleRate, bits, channels, dataSize.toLong(), durationMs, file.length())
            }
        }
    }
}

data class WavProperties(
    val sampleRate: Int,
    val bitsPerSample: Int,
    val channels: Int,
    val dataBytes: Long,
    val durationMs: Long,
    val fileBytes: Long,
)
