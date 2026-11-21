package pt.lersembarreiras.studio.audio

import java.io.File
import java.io.RandomAccessFile
import kotlin.math.abs
import kotlin.math.max

data class WaveformPeaks(
    val peaks: FloatArray,
    val durationMs: Long,
) {
    override fun equals(other: Any?): Boolean =
        other is WaveformPeaks && durationMs == other.durationMs && peaks.contentEquals(other.peaks)

    override fun hashCode(): Int = 31 * peaks.contentHashCode() + durationMs.hashCode()
}

object DesktopWaveform {
    const val DEFAULT_BARS = 480

    fun fromWav(file: File, barCount: Int = DEFAULT_BARS): WaveformPeaks {
        val props = StreamingWavWriter.inspect(file)
        val bars = barCount.coerceAtLeast(1)
        val bytesPerSample = (props.bitsPerSample / 8).coerceAtLeast(1)
        val frameSize = bytesPerSample * props.channels.coerceAtLeast(1)
        val frames = if (frameSize == 0) 0 else (props.dataBytes / frameSize).toInt().coerceAtLeast(1)
        val samplesPerBar = max(1, frames / bars)
        val peaks = FloatArray(bars)
        val maxAmp = if (props.bitsPerSample >= 24) 8_388_607f else 32_767f
        RandomAccessFile(file, "r").use { raf ->
            raf.seek(dataOffset(raf))
            val buffer = ByteArray(frameSize * 1024)
            var frame = 0
            var bar = 0
            var peak = 0
            while (frame < frames && bar < bars) {
                val remaining = minOf(1024, frames - frame)
                val read = raf.read(buffer, 0, remaining * frameSize)
                if (read <= 0) break
                var offset = 0
                repeat(read / frameSize) {
                    var sum = 0
                    repeat(props.channels.coerceAtLeast(1)) {
                        sum += sampleAt(buffer, offset, bytesPerSample)
                        offset += bytesPerSample
                    }
                    val mono = abs(sum / props.channels.coerceAtLeast(1))
                    if (mono > peak) peak = mono
                    frame++
                    if (frame % samplesPerBar == 0 || frame == frames) {
                        if (bar < bars) {
                            peaks[bar] = (peak / maxAmp).coerceIn(0f, 1f)
                            bar++
                        }
                        peak = 0
                    }
                }
            }
        }
        return WaveformPeaks(peaks, props.durationMs)
    }

    private fun sampleAt(buffer: ByteArray, offset: Int, bytesPerSample: Int): Int {
        return when {
            bytesPerSample >= 3 -> {
                val b0 = buffer[offset].toInt() and 0xFF
                val b1 = buffer[offset + 1].toInt() and 0xFF
                val b2 = buffer[offset + 2].toInt()
                (b2 shl 16) or (b1 shl 8) or b0
            }
            else -> {
                val lo = buffer[offset].toInt() and 0xFF
                val hi = buffer[offset + 1].toInt()
                (hi shl 8) or lo
            }
        }
    }

    private fun dataOffset(raf: RandomAccessFile): Long {
        raf.seek(12)
        val id = ByteArray(4)
        val size = ByteArray(4)
        while (raf.filePointer + 8 <= raf.length()) {
            raf.readFully(id)
            raf.readFully(size)
            val chunk = String(id, Charsets.US_ASCII)
            val n = java.nio.ByteBuffer.wrap(size).order(java.nio.ByteOrder.LITTLE_ENDIAN).int
            if (chunk == "data") return raf.filePointer
            raf.skipBytes(n + n % 2)
        }
        return 44
    }
}
