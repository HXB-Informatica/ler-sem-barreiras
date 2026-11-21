package pt.lersembarreiras.audio

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.roundToInt

data class WavInfo(
    val sampleRate: Int,
    val channels: Int,
    val bits: Int,
    val dataOffset: Long,
    val dataSize: Int,
) {
    val frameCount: Int get() = if (channels <= 0 || bits < 8) 0 else dataSize / (channels * (bits / 8))
}

object WavPcm {
    const val WHISPER_SAMPLE_RATE = 16_000
    private const val BITS_PER_SAMPLE = 16
    private const val CHANNELS = 1

    fun writeWav(file: File, pcm16Mono: ShortArray, sampleRate: Int = WHISPER_SAMPLE_RATE) {
        file.parentFile?.mkdirs()
        val dataSize = pcm16Mono.size * 2
        val buffer = ByteBuffer.allocate(44 + dataSize).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put("RIFF".toByteArray(Charsets.US_ASCII))
        buffer.putInt(36 + dataSize)
        buffer.put("WAVE".toByteArray(Charsets.US_ASCII))
        buffer.put("fmt ".toByteArray(Charsets.US_ASCII))
        buffer.putInt(16)
        buffer.putShort(1)
        buffer.putShort(CHANNELS.toShort())
        buffer.putInt(sampleRate)
        buffer.putInt(sampleRate * CHANNELS * BITS_PER_SAMPLE / 8)
        buffer.putShort((CHANNELS * BITS_PER_SAMPLE / 8).toShort())
        buffer.putShort(BITS_PER_SAMPLE.toShort())
        buffer.put("data".toByteArray(Charsets.US_ASCII))
        buffer.putInt(dataSize)
        pcm16Mono.forEach { buffer.putShort(it) }
        file.writeBytes(buffer.array())
    }

    fun inspectWav(file: File): WavInfo {
        RandomAccessFile(file, "r").use { raf ->
            require(raf.length() >= 44) { "Ficheiro WAV demasiado pequeno." }
            val riff = ByteArray(4).also { raf.readFully(it) }
            require(String(riff, Charsets.US_ASCII) == "RIFF") { "Formato WAV inválido." }
            raf.skipBytes(4)
            val wave = ByteArray(4).also { raf.readFully(it) }
            require(String(wave, Charsets.US_ASCII) == "WAVE") { "Formato WAV inválido." }
            var sampleRate = WHISPER_SAMPLE_RATE
            var bits = BITS_PER_SAMPLE
            var channels = CHANNELS
            while (raf.filePointer + 8 <= raf.length()) {
                val id = ByteArray(4).also { raf.readFully(it) }
                val size = readLittleEndianInt(raf)
                val chunk = String(id, Charsets.US_ASCII)
                when (chunk) {
                    "fmt " -> {
                        val fmt = ByteArray(size)
                        raf.readFully(fmt)
                        val buffer = ByteBuffer.wrap(fmt).order(ByteOrder.LITTLE_ENDIAN)
                        buffer.short
                        channels = buffer.short.toInt()
                        sampleRate = buffer.int
                        buffer.int
                        buffer.short
                        bits = buffer.short.toInt()
                        if (size % 2 == 1) raf.skipBytes(1)
                    }
                    "data" -> {
                        return WavInfo(sampleRate, channels, bits, raf.filePointer, size)
                    }
                    else -> {
                        raf.skipBytes(size + size % 2)
                    }
                }
            }
            error("Chunk de dados PCM não encontrado.")
        }
    }

    private fun readLittleEndianInt(raf: RandomAccessFile): Int {
        val bytes = ByteArray(4)
        raf.readFully(bytes)
        return ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).int
    }

    fun readPcm16Mono(file: File): Pair<Int, ShortArray> {
        val bytes = file.readBytes()
        require(bytes.size >= 44) { "Ficheiro WAV demasiado pequeno." }
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val riff = ByteArray(4).also { buffer.get(it) }
        require(String(riff, Charsets.US_ASCII) == "RIFF") { "Formato WAV inválido." }
        buffer.int
        val wave = ByteArray(4).also { buffer.get(it) }
        require(String(wave, Charsets.US_ASCII) == "WAVE") { "Formato WAV inválido." }
        var sampleRate = WHISPER_SAMPLE_RATE
        var bits = BITS_PER_SAMPLE
        var channels = CHANNELS
        var dataOffset = -1
        var dataSize = 0
        while (buffer.remaining() >= 8) {
            val id = ByteArray(4).also { buffer.get(it) }
            val size = buffer.int
            val chunk = String(id, Charsets.US_ASCII)
            when (chunk) {
                "fmt " -> {
                    buffer.short
                    channels = buffer.short.toInt()
                    sampleRate = buffer.int
                    buffer.int
                    buffer.short
                    bits = buffer.short.toInt()
                    if (size > 16) buffer.position(buffer.position() + (size - 16))
                }
                "data" -> {
                    dataOffset = buffer.position()
                    dataSize = size
                    break
                }
                else -> buffer.position(buffer.position() + size)
            }
        }
        require(dataOffset >= 0) { "Chunk de dados PCM não encontrado." }
        require(bits == 16) { "Apenas PCM de 16 bits é suportado internamente." }
        val pcmBytes = bytes.copyOfRange(dataOffset, (dataOffset + dataSize).coerceAtMost(bytes.size))
        val shorts = ShortArray(pcmBytes.size / 2)
        ByteBuffer.wrap(pcmBytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shorts)
        val mono = if (channels <= 1) shorts else toMono(shorts, channels)
        return sampleRate to mono
    }

    fun resampleTo16k(pcm: ShortArray, sourceSampleRate: Int): ShortArray {
        if (sourceSampleRate == WHISPER_SAMPLE_RATE || pcm.isEmpty()) return pcm
        val ratio = sourceSampleRate.toDouble() / WHISPER_SAMPLE_RATE
        val length = (pcm.size / ratio).roundToInt().coerceAtLeast(1)
        val output = ShortArray(length)
        for (i in output.indices) {
            val src = i * ratio
            val index = src.toInt().coerceAtMost(pcm.lastIndex)
            val next = (index + 1).coerceAtMost(pcm.lastIndex)
            val fraction = src - index
            output[i] = ((1 - fraction) * pcm[index] + fraction * pcm[next]).roundToInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                .toShort()
        }
        return output
    }

    fun pcm16ToFloat(pcm: ShortArray): FloatArray = FloatArray(pcm.size) { index ->
        pcm[index] / 32768.0f
    }

    fun durationMs(sampleCount: Int, sampleRate: Int = WHISPER_SAMPLE_RATE): Long {
        if (sampleRate <= 0) return 0
        return (sampleCount * 1000L) / sampleRate
    }

    fun appendPcm(chunks: List<ShortArray>): ShortArray {
        val stream = ByteArrayOutputStream()
        chunks.forEach { chunk ->
            val buffer = ByteBuffer.allocate(chunk.size * 2).order(ByteOrder.LITTLE_ENDIAN)
            chunk.forEach { buffer.putShort(it) }
            stream.write(buffer.array())
        }
        val bytes = stream.toByteArray()
        val result = ShortArray(bytes.size / 2)
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(result)
        return result
    }

    private fun toMono(pcm: ShortArray, channels: Int): ShortArray {
        val frames = pcm.size / channels
        return ShortArray(frames) { frame ->
            var sum = 0
            for (channel in 0 until channels) {
                sum += pcm[frame * channels + channel]
            }
            (sum / channels).toShort()
        }
    }
}
