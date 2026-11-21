package pt.lersembarreiras.studio.audio

import kotlin.math.abs
import kotlin.math.sqrt

data class LevelReading(
    val peak: Float,
    val rms: Float,
    val clipping: Boolean,
)

object LevelMeter {
    fun read(buffer: ByteArray, length: Int, bitsPerSample: Int, channels: Int): LevelReading {
        if (length <= 0) return LevelReading(0f, 0f, false)
        val bytesPerSample = (bitsPerSample / 8).coerceAtLeast(1)
        val frameSize = bytesPerSample * channels.coerceAtLeast(1)
        var peak = 0
        var sumSquares = 0.0
        var count = 0
        var clipped = 0
        val clipThreshold = when (bitsPerSample) {
            24 -> (0.99 * 8_388_607).toInt()
            else -> (0.99 * 32_767).toInt()
        }
        var offset = 0
        while (offset + frameSize <= length) {
            var mono = 0
            repeat(channels.coerceAtLeast(1)) {
                val sample = when (bytesPerSample) {
                    3 -> read24(buffer, offset)
                    4 -> read24From32(buffer, offset)
                    else -> read16(buffer, offset)
                }
                offset += bytesPerSample
                mono += sample
            }
            val value = abs(mono / channels.coerceAtLeast(1))
            if (value > peak) peak = value
            if (value >= clipThreshold) clipped++
            sumSquares += value.toDouble() * value
            count++
        }
        val max = if (bitsPerSample >= 24) 8_388_607f else 32_767f
        val peakNorm = (peak / max).coerceIn(0f, 1f)
        val rms = if (count == 0) 0f else (sqrt(sumSquares / count) / max).toFloat().coerceIn(0f, 1f)
        return LevelReading(peakNorm, rms, clipped >= 3)
    }

    private fun read16(buffer: ByteArray, offset: Int): Int {
        val lo = buffer[offset].toInt() and 0xFF
        val hi = buffer[offset + 1].toInt()
        return (hi shl 8) or lo
    }

    private fun read24(buffer: ByteArray, offset: Int): Int {
        val b0 = buffer[offset].toInt() and 0xFF
        val b1 = buffer[offset + 1].toInt() and 0xFF
        val b2 = buffer[offset + 2].toInt()
        return (b2 shl 16) or (b1 shl 8) or b0
    }

    private fun read24From32(buffer: ByteArray, offset: Int): Int = read24(buffer, offset)
}
