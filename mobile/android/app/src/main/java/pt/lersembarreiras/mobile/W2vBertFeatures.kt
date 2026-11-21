package pt.lersembarreiras.mobile

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * SeamlessM4T / w2v-BERT 2.0 log-mel features, matching onnx-asr
 * `W2vBertPreprocessorNumpy`: Povey window, hop 160, two-frame stack.
 */
internal object W2vBertFeatures {
    private const val WIN = 400
    private const val HOP = 160
    private const val FFT = 512
    private const val BINS = 257
    private const val MELS = 80
    private const val SCALE = 32768.0
    private const val PREEMPH = 0.97
    private const val MEL_FLOOR = 1.192092955078125e-07
    private const val NORM_EPS = 1e-7

    private val window = DoubleArray(WIN) { index ->
        val hann = 0.5 - 0.5 * cos(2.0 * Math.PI * index / (WIN - 1))
        hann.pow(0.85)
    }

    fun extract(samples: FloatArray, mel: Array<DoubleArray>): FloatArray {
        if (samples.size < WIN) {
            return FloatArray(0)
        }
        val frames = 1 + (samples.size - WIN) / HOP
        val melFrames = Array(frames) { frame ->
            val start = frame * HOP
            val scaled = DoubleArray(WIN) { samples[start + it].toDouble() * SCALE }
            var mean = 0.0
            for (value in scaled) mean += value
            mean /= WIN
            for (i in scaled.indices) scaled[i] -= mean
            val emphasised = DoubleArray(WIN)
            emphasised[0] = scaled[0] - PREEMPH * scaled[0]
            for (i in 1 until WIN) emphasised[i] = scaled[i] - PREEMPH * scaled[i - 1]
            for (i in emphasised.indices) emphasised[i] *= window[i]
            val power = rfftPower(emphasised)
            DoubleArray(MELS) { melIndex ->
                var sum = 0.0
                for (bin in 0 until BINS) sum += power[bin] * mel[bin][melIndex]
                ln(max(sum, MEL_FLOOR))
            }
        }
        var mean = DoubleArray(MELS)
        for (frame in melFrames) {
            for (melIndex in 0 until MELS) mean[melIndex] += frame[melIndex]
        }
        for (melIndex in 0 until MELS) mean[melIndex] /= frames
        val variance = DoubleArray(MELS)
        for (frame in melFrames) {
            for (melIndex in 0 until MELS) {
                val delta = frame[melIndex] - mean[melIndex]
                variance[melIndex] += delta * delta
            }
        }
        val scale = DoubleArray(MELS) { melIndex ->
            sqrt(variance[melIndex] / frames + NORM_EPS)
        }
        val usable = frames / 2
        val features = FloatArray(usable * 160)
        for (pair in 0 until usable) {
            val left = melFrames[pair * 2]
            val right = melFrames[pair * 2 + 1]
            val offset = pair * 160
            for (melIndex in 0 until MELS) {
                features[offset + melIndex] = ((left[melIndex] - mean[melIndex]) / scale[melIndex]).toFloat()
                features[offset + MELS + melIndex] = ((right[melIndex] - mean[melIndex]) / scale[melIndex]).toFloat()
            }
        }
        return features
    }

    fun loadMel(bytes: ByteArray): Array<DoubleArray> {
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer()
        return Array(BINS) { DoubleArray(MELS) { buffer.get().toDouble() } }
    }

    private fun rfftPower(frame: DoubleArray): DoubleArray {
        val real = DoubleArray(FFT)
        val imag = DoubleArray(FFT)
        for (i in frame.indices) real[i] = frame[i]
        fft(real, imag)
        return DoubleArray(BINS) { bin -> real[bin] * real[bin] + imag[bin] * imag[bin] }
    }

    private fun fft(real: DoubleArray, imag: DoubleArray) {
        val count = real.size
        var j = 0
        for (i in 1 until count) {
            var bit = count shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j xor bit
            if (i < j) {
                val swapReal = real[i]
                real[i] = real[j]
                real[j] = swapReal
                val swapImag = imag[i]
                imag[i] = imag[j]
                imag[j] = swapImag
            }
        }
        var length = 2
        while (length <= count) {
            val angle = -2.0 * Math.PI / length
            val stepReal = cos(angle)
            val stepImag = sin(angle)
            var start = 0
            val half = length / 2
            while (start < count) {
                var turnReal = 1.0
                var turnImag = 0.0
                for (k in 0 until half) {
                    val evenReal = real[start + k]
                    val evenImag = imag[start + k]
                    val oddReal = real[start + k + half] * turnReal - imag[start + k + half] * turnImag
                    val oddImag = real[start + k + half] * turnImag + imag[start + k + half] * turnReal
                    real[start + k] = evenReal + oddReal
                    imag[start + k] = evenImag + oddImag
                    real[start + k + half] = evenReal - oddReal
                    imag[start + k + half] = evenImag - oddImag
                    val nextReal = turnReal * stepReal - turnImag * stepImag
                    turnImag = turnReal * stepImag + turnImag * stepReal
                    turnReal = nextReal
                }
                start += length
            }
            length = length shl 1
        }
    }
}
