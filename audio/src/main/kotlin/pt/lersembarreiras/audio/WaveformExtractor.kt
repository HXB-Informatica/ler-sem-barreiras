package pt.lersembarreiras.audio

import java.io.File
import java.io.RandomAccessFile
import kotlin.math.abs
import kotlin.math.max

data class WaveformPeaks(
    val peaks: FloatArray,
    val durationMs: Long,
    val sampleRate: Int,
    val frameCount: Int,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is WaveformPeaks) return false
        return durationMs == other.durationMs &&
            sampleRate == other.sampleRate &&
            frameCount == other.frameCount &&
            peaks.contentEquals(other.peaks)
    }

    override fun hashCode(): Int {
        var result = peaks.contentHashCode()
        result = 31 * result + durationMs.hashCode()
        result = 31 * result + sampleRate
        result = 31 * result + frameCount
        return result
    }
}

/**
 * Reduz PCM a um número fixo de picos para desenho. Não devolve as amostras originais.
 */
object WaveformExtractor {
    const val DEFAULT_BARS = 480

    fun fromWav(file: File, barCount: Int = DEFAULT_BARS): WaveformPeaks {
        val info = WavPcm.inspectWav(file)
        require(info.bits == 16) { "Apenas PCM de 16 bits é suportado para a forma de onda." }
        val bars = barCount.coerceAtLeast(1)
        val frames = info.frameCount.coerceAtLeast(1)
        val samplesPerBar = max(1, frames / bars)
        val peaks = FloatArray(bars)
        RandomAccessFile(file, "r").use { raf ->
            raf.seek(info.dataOffset)
            val frameBytes = info.channels * 2
            val buffer = ByteArray(frameBytes * 1024)
            var frame = 0
            var bar = 0
            var peak = 0
            while (frame < frames && bar < bars) {
                val remainingFrames = minOf(1024, frames - frame)
                val read = raf.read(buffer, 0, remainingFrames * frameBytes)
                if (read <= 0) break
                val framesRead = read / frameBytes
                var offset = 0
                repeat(framesRead) {
                    var sum = 0
                    repeat(info.channels) {
                        val lo = buffer[offset].toInt() and 0xFF
                        val hi = buffer[offset + 1].toInt() and 0xFF
                        offset += 2
                        val signed = ((hi shl 8) or lo).toShort().toInt()
                        sum += signed
                    }
                    val mono = abs(sum / info.channels)
                    if (mono > peak) peak = mono
                    frame++
                    if (frame % samplesPerBar == 0 || frame == frames) {
                        if (bar < bars) {
                            peaks[bar] = (peak / 32768f).coerceIn(0f, 1f)
                            bar++
                        }
                        peak = 0
                    }
                }
            }
        }
        return WaveformPeaks(
            peaks = peaks,
            durationMs = WavPcm.durationMs(frames, info.sampleRate),
            sampleRate = info.sampleRate,
            frameCount = frames,
        )
    }

    fun fromSamples(samples: ShortArray, sampleRate: Int, barCount: Int = DEFAULT_BARS): WaveformPeaks {
        val bars = barCount.coerceAtLeast(1)
        val peaks = FloatArray(bars)
        if (samples.isEmpty()) {
            return WaveformPeaks(peaks, 0, sampleRate, 0)
        }
        val samplesPerBar = max(1, samples.size / bars)
        for (bar in 0 until bars) {
            val start = bar * samplesPerBar
            val end = if (bar == bars - 1) samples.size else minOf(samples.size, start + samplesPerBar)
            var peak = 0
            for (index in start until end) {
                val value = abs(samples[index].toInt())
                if (value > peak) peak = value
            }
            peaks[bar] = (peak / 32768f).coerceIn(0f, 1f)
        }
        return WaveformPeaks(
            peaks = peaks,
            durationMs = WavPcm.durationMs(samples.size, sampleRate),
            sampleRate = sampleRate,
            frameCount = samples.size,
        )
    }
}
