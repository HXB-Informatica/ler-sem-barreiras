package pt.lersembarreiras.mobile

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.roundToInt

internal object WavPcm {
    data class PcmWav(
        val sampleRate: Int,
        val samples: ShortArray,
    )

    fun readMonoPcm(file: File): PcmWav {
        val bytes = file.readBytes()
        if (bytes.size < 44) {
            throw IllegalArgumentException("O áudio está incompleto.")
        }
        fun ascii(offset: Int) = String(bytes, offset, 4, Charsets.US_ASCII)
        if (ascii(0) != "RIFF" || ascii(8) != "WAVE") {
            throw IllegalArgumentException("O áudio tem de ser WAV PCM.")
        }
        var offset = 12
        var sampleRate = 0
        var channels = 0
        var bits = 0
        var dataOffset = -1
        var dataBytes = 0
        while (offset + 8 <= bytes.size) {
            val id = ascii(offset)
            val size = ByteBuffer.wrap(bytes, offset + 4, 4).order(ByteOrder.LITTLE_ENDIAN).int
            offset += 8
            when (id) {
                "fmt " -> {
                    val buf = ByteBuffer.wrap(bytes, offset, size.coerceAtLeast(16)).order(ByteOrder.LITTLE_ENDIAN)
                    val format = buf.short.toInt()
                    if (format != 1) {
                        throw IllegalArgumentException("O áudio tem de ser PCM sem compressão.")
                    }
                    channels = buf.short.toInt()
                    sampleRate = buf.int
                    buf.int
                    buf.short
                    bits = buf.short.toInt()
                }
                "data" -> {
                    dataOffset = offset
                    dataBytes = size.coerceAtLeast(0)
                    break
                }
            }
            offset += size + size % 2
        }
        if (sampleRate <= 0 || channels <= 0 || bits != 16 || dataOffset < 0) {
            throw IllegalArgumentException("A narração tem de ser WAV PCM 16-bit.")
        }
        val sampleCount = (dataBytes / 2).coerceAtMost((bytes.size - dataOffset) / 2)
        val pcm = ByteBuffer.wrap(bytes, dataOffset, sampleCount * 2).order(ByteOrder.LITTLE_ENDIAN)
        val interleaved = ShortArray(sampleCount) { pcm.short }
        val mono = toMono(interleaved, channels)
        return PcmWav(sampleRate, mono)
    }

    fun readMonoFloats16k(file: File): FloatArray {
        val wav = readMonoPcm(file)
        val samples = resample(wav.samples, wav.sampleRate, 16_000)
        return FloatArray(samples.size) { samples[it] / 32768.0f }
    }

    fun toMono(samples: ShortArray, channels: Int): ShortArray {
        if (channels <= 1) {
            return samples
        }
        val out = ShortArray(samples.size / channels)
        var i = 0
        var o = 0
        while (i + channels - 1 < samples.size && o < out.size) {
            out[o] = samples[i]
            i += channels
            o += 1
        }
        return out
    }

    fun resample(input: ShortArray, srcRate: Int, destRate: Int): ShortArray {
        if (srcRate == destRate || input.isEmpty()) {
            return input
        }
        val outLen = (input.size.toLong() * destRate / srcRate).toInt().coerceAtLeast(1)
        val out = ShortArray(outLen)
        if (destRate < srcRate) {
            val step = srcRate.toDouble() / destRate
            for (i in 0 until outLen) {
                val start = (i * step).toInt().coerceIn(0, input.lastIndex)
                val end = ((i + 1) * step).toInt().coerceIn(start + 1, input.size)
                var sum = 0L
                for (j in start until end) {
                    sum += input[j]
                }
                out[i] = (sum / (end - start)).toInt().toShort()
            }
            return out
        }
        for (i in 0 until outLen) {
            val srcPos = i.toDouble() * srcRate / destRate
            val i0 = srcPos.toInt().coerceIn(0, input.lastIndex)
            val i1 = (i0 + 1).coerceAtMost(input.lastIndex)
            val frac = srcPos - i0
            out[i] = ((1.0 - frac) * input[i0] + frac * input[i1]).roundToInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                .toShort()
        }
        return out
    }

    fun normalize(samples: ShortArray, target: Double = 0.72): ShortArray {
        var peak = 1
        for (sample in samples) {
            val abs = abs(sample.toInt())
            if (abs > peak) peak = abs
        }
        val gain = (32767.0 * target) / peak
        if (gain <= 1.05) {
            return samples
        }
        val limited = gain.coerceAtMost(12.0)
        return ShortArray(samples.size) { index ->
            (samples[index].toInt() * limited).roundToInt().coerceIn(-32767, 32767).toShort()
        }
    }
}
