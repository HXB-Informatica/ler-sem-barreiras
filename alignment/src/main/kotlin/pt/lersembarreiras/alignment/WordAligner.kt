package pt.lersembarreiras.alignment

import pt.lersembarreiras.core.model.AlignmentResult
import pt.lersembarreiras.core.model.RecognizedWord
import pt.lersembarreiras.core.model.TextToken
import pt.lersembarreiras.core.model.WordTiming
import pt.lersembarreiras.core.text.WordTokenizer
import kotlin.math.max
import kotlin.math.min

/**
 * Alinha o texto original com palavras reconhecidas pelo motor de fala.
 *
 * Usa programação dinâmica (Needleman–Wunsch) para lidar com omissões,
 * hesitações, pontuação e erros de reconhecimento. Palavras sem correspondência
 * fiável ficam sem timestamps — não são inventados tempos com aparência de precisão.
 */
class WordAligner(
    private val matchScore: Int = 4,
    private val mismatchScore: Int = -2,
    private val gapScore: Int = -1,
) {
    fun align(
        sourceText: String,
        recognized: List<RecognizedWord>,
        audioDurationMs: Long,
    ): AlignmentResult {
        val source = WordTokenizer.tokenize(sourceText)
        return align(source, recognized, audioDurationMs)
    }

    fun align(
        source: List<TextToken>,
        recognized: List<RecognizedWord>,
        audioDurationMs: Long,
    ): AlignmentResult {
        if (source.isEmpty()) {
            return AlignmentResult(emptyList(), 0, 0, recognized.size)
        }

        val normalizedRecognized = recognized.map {
            it.copy(text = WordTokenizer.normalizeForMatch(it.text))
        }

        val n = source.size
        val m = normalizedRecognized.size
        val score = Array(n + 1) { IntArray(m + 1) }
        val pointer = Array(n + 1) { IntArray(m + 1) }

        for (i in 1..n) {
            score[i][0] = i * gapScore
            pointer[i][0] = UP
        }
        for (j in 1..m) {
            score[0][j] = j * gapScore
            pointer[0][j] = LEFT
        }

        for (i in 1..n) {
            for (j in 1..m) {
                val diagonal = score[i - 1][j - 1] + similarity(source[i - 1].normalized, normalizedRecognized[j - 1].text)
                val up = score[i - 1][j] + gapScore
                val left = score[i][j - 1] + gapScore
                when {
                    diagonal >= up && diagonal >= left -> {
                        score[i][j] = diagonal
                        pointer[i][j] = DIAG
                    }
                    up >= left -> {
                        score[i][j] = up
                        pointer[i][j] = UP
                    }
                    else -> {
                        score[i][j] = left
                        pointer[i][j] = LEFT
                    }
                }
            }
        }

        val pairs = MutableList<Int?>(n) { null }
        var i = n
        var j = m
        var unusedRecognized = 0
        while (i > 0 || j > 0) {
            when (pointer[i][j]) {
                DIAG -> {
                    val sim = similarity(source[i - 1].normalized, normalizedRecognized[j - 1].text)
                    if (sim > 0) {
                        pairs[i - 1] = j - 1
                    }
                    i--
                    j--
                }
                UP -> {
                    i--
                }
                else -> {
                    unusedRecognized++
                    j--
                }
            }
        }

        val words = source.mapIndexed { index, token ->
            val recognizedIndex = pairs[index]
            if (recognizedIndex == null) {
                WordTiming(
                    text = token.original,
                    startChar = token.startChar,
                    endChar = token.endChar,
                    startMs = null,
                    endMs = null,
                    confidence = 0f,
                    needsReview = true,
                )
            } else {
                val rec = recognized[recognizedIndex]
                val sim = similarity(token.normalized, normalizedRecognized[recognizedIndex].text)
                val confidence = (rec.probability * (sim.toFloat() / matchScore.toFloat())).coerceIn(0f, 1f)
                WordTiming(
                    text = token.original,
                    startChar = token.startChar,
                    endChar = token.endChar,
                    startMs = rec.startMs.coerceAtLeast(0),
                    endMs = rec.endMs.coerceAtLeast(rec.startMs),
                    confidence = confidence,
                    needsReview = confidence < MIN_CONFIDENCE || sim < matchScore,
                )
            }
        }

        val validated = TimeValidator.validate(words, audioDurationMs)
        val alignedCount = validated.count { it.isTimed }
        val reviewCount = validated.count { it.needsReview || !it.isTimed }
        return AlignmentResult(
            words = validated,
            alignedCount = alignedCount,
            reviewCount = reviewCount,
            unusedRecognizedCount = unusedRecognized,
        )
    }

    internal fun similarity(source: String, recognized: String): Int {
        if (source.isEmpty() || recognized.isEmpty()) return mismatchScore
        if (source == recognized) return matchScore
        val distance = WordTokenizer.levenshtein(source, recognized)
        val longest = max(source.length, recognized.length)
        val shortest = min(source.length, recognized.length)
        return when {
            distance == 1 && longest >= 3 -> matchScore - 1
            distance == 2 && longest >= 6 -> 1
            shortest >= 4 && (source.startsWith(recognized) || recognized.startsWith(source)) -> 1
            else -> mismatchScore
        }
    }

    companion object {
        private const val DIAG = 1
        private const val UP = 2
        private const val LEFT = 3
        const val MIN_CONFIDENCE = 0.35f
    }
}
