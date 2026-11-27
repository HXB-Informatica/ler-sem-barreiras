package pt.lersembarreiras.mobile

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.res.AssetManager
import java.io.File
import java.nio.FloatBuffer
import java.nio.LongBuffer
import kotlin.math.exp

internal data class HeardWord(
    val text: String,
    val startMs: Long,
    val endMs: Long,
    val probability: Float,
)

internal class EbranchCtc(
    private val modelFile: File,
    private val vocab: Map<Int, String>,
    private val mel: Array<DoubleArray>,
) {
    private val environment = OrtEnvironment.getEnvironment()
    private val session: OrtSession = environment.createSession(
        modelFile.absolutePath,
        OrtSession.SessionOptions().apply {
            val cpus = Runtime.getRuntime().availableProcessors()
            setIntraOpNumThreads(cpus.coerceIn(1, 2))
            setInterOpNumThreads(1)
            setMemoryPatternOptimization(true)
            setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
        },
    )

    fun close() {
        runCatching { session.close() }
    }

    fun recognize(samples: FloatArray): List<HeardWord> {
        val features = W2vBertFeatures.extract(samples, mel)
        if (features.isEmpty()) return emptyList()
        val frames = features.size / 160
        val featureTensor = OnnxTensor.createTensor(
            environment,
            FloatBuffer.wrap(features),
            longArrayOf(1, frames.toLong(), 160),
        )
        val lengthTensor = OnnxTensor.createTensor(
            environment,
            LongBuffer.wrap(longArrayOf(frames.toLong())),
            longArrayOf(1),
        )
        val outputs = session.run(mapOf("features" to featureTensor, "features_lens" to lengthTensor))
        featureTensor.close()
        lengthTensor.close()
        outputs.use { result ->
            val logprobs = result[0] as OnnxTensor
            val lengths = result[1] as OnnxTensor
            val time = lengths.longBuffer.get(0).toInt()
            val shape = logprobs.info.shape
            val vocabSize = shape[2].toInt()
            val buffer = logprobs.floatBuffer
            return decode(buffer, time, vocabSize)
        }
    }

    private fun decode(logprobs: FloatBuffer, time: Int, vocabSize: Int): List<HeardWord> {
        val pieces = ArrayList<Piece>(time)
        var previous = BLANK
        for (frame in 0 until time) {
            val base = frame * vocabSize
            var best = 0
            var bestValue = logprobs.get(base)
            for (token in 1 until vocabSize) {
                val value = logprobs.get(base + token)
                if (value > bestValue) {
                    best = token
                    bestValue = value
                }
            }
            if (best == BLANK || best == previous) {
                previous = best
                continue
            }
            previous = best
            val text = vocab[best] ?: continue
            if (text.isEmpty() || text.startsWith("<") || text.startsWith("_")) continue
            pieces.add(Piece(text, frame * FRAME_MS, exp(bestValue.toDouble()).toFloat()))
        }
        if (pieces.isEmpty()) return emptyList()
        val words = ArrayList<HeardWord>()
        val builder = StringBuilder()
        var start = pieces[0].timeMs
        var probability = 0.0
        var count = 0
        fun flush(end: Long) {
            val text = builder.toString().trim()
            if (text.isNotEmpty() && end > start) {
                words.add(HeardWord(text, start, end, (probability / count.coerceAtLeast(1)).toFloat()))
            }
            builder.setLength(0)
            probability = 0.0
            count = 0
        }
        for (piece in pieces) {
            if (builder.isNotEmpty() && piece.text.startsWith(" ")) {
                flush(piece.timeMs)
                start = piece.timeMs
            }
            if (builder.isEmpty()) start = piece.timeMs
            builder.append(piece.text.trim())
            probability += piece.probability
            count += 1
        }
        flush(pieces.last().timeMs + FRAME_MS)
        return words
    }

    private data class Piece(val text: String, val timeMs: Long, val probability: Float)

    companion object {
        private const val BLANK = 0
        private const val FRAME_MS = 80L

        fun loadVocab(text: String): Map<Int, String> {
            val vocab = HashMap<Int, String>()
            for (line in text.split('\n')) {
                if (line.isEmpty()) continue
                val split = line.lastIndexOf(' ')
                if (split <= 0) continue
                val token = line.substring(0, split).replace('\u2581', ' ')
                val id = line.substring(split + 1).toInt()
                vocab[id] = token
            }
            return vocab
        }

        fun install(assets: AssetManager, directories: List<File>): Triple<File, Map<Int, String>, Array<DoubleArray>> {
            val ready = directories.firstOrNull { directory ->
                File(directory, "model.onnx").length() > 1_000_000 &&
                    File(directory, "model.int8.onnx.data").length() > 100_000_000
            }
            val directory = ready ?: directories.last().also { target ->
                target.mkdirs()
                copy(assets, target, "model.onnx", 1_000_000)
                copy(assets, target, "model.int8.onnx.data", 100_000_000)
            }
            val vocabText = assets.open("ctc/vocab.txt").bufferedReader().use { it.readText() }
            val melBytes = assets.open("ctc/w2vbert-mel.bin").use { it.readBytes() }
            return Triple(File(directory, "model.onnx"), loadVocab(vocabText), W2vBertFeatures.loadMel(melBytes))
        }

        private fun copy(assets: AssetManager, directory: File, name: String, minBytes: Long): File {
            val out = File(directory, name)
            if (out.isFile && out.length() >= minBytes) return out
            assets.open("ctc/$name").use { input ->
                out.outputStream().use { output -> input.copyTo(output) }
            }
            return out
        }
    }
}
