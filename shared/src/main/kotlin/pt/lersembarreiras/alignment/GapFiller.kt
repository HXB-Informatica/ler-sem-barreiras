package pt.lersembarreiras.alignment

import pt.lersembarreiras.core.model.TimestampOrigin
import pt.lersembarreiras.core.model.WordState
import pt.lersembarreiras.core.model.WordTiming
import kotlin.math.max

/**
 * Preenche lacunas **curtas** entre duas palavras com timestamp ASR.
 * Palavras nas pontas ou após pausas longas ficam sem tempo (não se inventa fala).
 * Os intervalos interpolados ficam em REVIEW com origem ALIGNED.
 */
object GapFiller {
    const val MIN_MS_PER_WORD = 40L
    const val MAX_MS_PER_WORD = 500L

    fun fill(words: List<WordTiming>): List<WordTiming> {
        if (words.size < 3) return words
        val out = words.toMutableList()
        var i = 0
        while (i < out.size) {
            if (out[i].isTimed) {
                i++
                continue
            }
            var j = i
            while (j < out.size && !out[j].isTimed) j++
            val left = out.getOrNull(i - 1)
            val right = out.getOrNull(j)
            val gapStart = left?.endMs
            val gapEnd = right?.startMs
            val missing = j - i
            if (
                left != null &&
                right != null &&
                left.isTimed &&
                right.isTimed &&
                !left.isProtectedFromResync() &&
                !right.isProtectedFromResync() &&
                gapStart != null &&
                gapEnd != null &&
                missing > 0
            ) {
                val startBound: Long = gapStart
                val endBound: Long = gapEnd
                val gap = endBound - startBound
                if (gap >= missing * MIN_MS_PER_WORD && gap <= missing * MAX_MS_PER_WORD) {
                    val weights = (i until j).map { idx ->
                        max(2, out[idx].text.count { it.isLetterOrDigit() })
                    }
                    val total = weights.sum().coerceAtLeast(1)
                    var cursor = startBound
                    for (offset in 0 until missing) {
                        val idx = i + offset
                        if (out[idx].isProtectedFromResync()) continue
                        val span = max(MIN_MS_PER_WORD, gap * weights[offset] / total)
                        val end = if (offset == missing - 1) {
                            endBound
                        } else {
                            (cursor + span).coerceAtMost(endBound)
                        }
                        val stop = end.coerceAtLeast(cursor + 20)
                        out[idx] = out[idx].copy(
                            startMs = cursor,
                            endMs = stop,
                            confidence = 0.25f,
                            needsReview = true,
                            state = WordState.REVIEW,
                            timestampOrigin = TimestampOrigin.ALIGNED,
                        )
                        cursor = stop
                    }
                }
            }
            i = j
        }
        return out
    }
}
