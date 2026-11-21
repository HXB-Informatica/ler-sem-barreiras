package pt.lersembarreiras.alignment

import pt.lersembarreiras.core.model.IntervalIssue
import pt.lersembarreiras.core.model.IntervalIssueType
import pt.lersembarreiras.core.model.IntervalIssueType.INVALID_ORDER
import pt.lersembarreiras.core.model.IntervalIssueType.MISSING_TIME
import pt.lersembarreiras.core.model.IntervalIssueType.OUTSIDE_AUDIO
import pt.lersembarreiras.core.model.IntervalIssueType.OVERLAP
import pt.lersembarreiras.core.model.IntervalIssueType.TOO_SHORT
import pt.lersembarreiras.core.model.IntervalIssueType.ZERO_LENGTH
import pt.lersembarreiras.core.model.TimestampOrigin
import pt.lersembarreiras.core.model.WordState
import pt.lersembarreiras.core.model.WordTiming

data class IntervalValidation(
    val words: List<WordTiming>,
    val issues: List<IntervalIssue>,
)

object TimeValidator {
    fun validate(words: List<WordTiming>, audioDurationMs: Long): List<WordTiming> {
        return inspect(words, audioDurationMs).words
    }

    fun inspect(words: List<WordTiming>, audioDurationMs: Long): IntervalValidation {
        if (words.isEmpty()) return IntervalValidation(words, emptyList())
        val duration = audioDurationMs.coerceAtLeast(0)
        val issues = mutableListOf<IntervalIssue>()
        var previousEnd = 0L
        var previousTimedIndex = -1
        val validated = words.mapIndexed { index, word ->
            val id = word.id.ifBlank { WordIdentity.idFor(index, word.startChar, word.endChar) }
            val protectedWord = word.isProtectedFromResync()
            if (!word.isTimed) {
                issues += IntervalIssue(index, id, MISSING_TIME, "Palavra sem intervalo temporal.")
                if (protectedWord) {
                    word.copy(id = id, index = if (word.index >= 0) word.index else index)
                } else {
                    word.copy(
                        id = id,
                        index = if (word.index >= 0) word.index else index,
                        needsReview = true,
                        state = WordState.REVIEW,
                        timestampOrigin = word.timestampOrigin ?: TimestampOrigin.NONE,
                    )
                }
            } else {
                var start = word.startMs!!.coerceAtLeast(0)
                var end = word.endMs!!.coerceAtLeast(start)
                var outside = false
                if (duration > 0) {
                    if (word.startMs!! > duration || word.endMs!! > duration) outside = true
                    start = start.coerceAtMost(duration)
                    end = end.coerceAtMost(duration)
                }
                if (end < start) {
                    val swapped = start
                    start = end
                    end = swapped
                }
                var repairedOverlap = false
                if (!protectedWord && previousTimedIndex >= 0 && start < previousEnd) {
                    start = previousEnd
                    repairedOverlap = true
                    if (end < start + 20) {
                        val limit = if (duration > 0) duration else start + 20
                        end = (start + 20).coerceAtMost(limit)
                    }
                }
                if (!protectedWord && duration > 0 && start >= duration) {
                    issues += IntervalIssue(index, id, OUTSIDE_AUDIO, "Intervalo fora da duração do áudio.")
                    word.copy(
                        id = id,
                        index = if (word.index >= 0) word.index else index,
                        startMs = null,
                        endMs = null,
                        needsReview = true,
                        state = WordState.REVIEW,
                        timestampOrigin = TimestampOrigin.NONE,
                    )
                } else {
                val overlap = previousTimedIndex >= 0 && start < previousEnd
                val invalidOrder = previousTimedIndex >= 0 && start + 8 < previousEnd
                val tooShort = end - start < 20 && word.text.length > 2
                val zeroLength = end == start
                if (outside) {
                    issues += IntervalIssue(index, id, OUTSIDE_AUDIO, "Intervalo fora da duração do áudio.")
                }
                if (overlap) {
                    issues += IntervalIssue(index, id, OVERLAP, "Sobreposição com a palavra anterior.")
                }
                if (invalidOrder) {
                    issues += IntervalIssue(index, id, INVALID_ORDER, "Início anterior ao fim da palavra precedente.")
                }
                if (tooShort) {
                    issues += IntervalIssue(index, id, TOO_SHORT, "Intervalo demasiado curto para a palavra.")
                }
                if (zeroLength) {
                    issues += IntervalIssue(index, id, ZERO_LENGTH, "Início e fim coincidem.")
                }
                previousEnd = maxOf(previousEnd, end)
                previousTimedIndex = index
                val invalid = invalidOrder || tooShort || zeroLength || overlap || outside || repairedOverlap
                if (protectedWord) {
                    word.copy(
                        id = id,
                        index = if (word.index >= 0) word.index else index,
                        startMs = start,
                        endMs = end,
                        needsReview = false,
                        manuallyAdjusted = true,
                        state = word.resolvedState(),
                        timestampOrigin = word.resolvedOrigin(),
                    )
                } else {
                    val review = word.needsReview || word.resolvedState() == WordState.REVIEW || invalid
                    word.copy(
                        id = id,
                        index = if (word.index >= 0) word.index else index,
                        startMs = start,
                        endMs = end,
                        needsReview = review,
                        state = if (review) WordState.REVIEW else WordState.AUTO,
                        timestampOrigin = word.resolvedOrigin(),
                    )
                }
                }
            }
        }
        return IntervalValidation(validated, issues)
    }

    fun currentWordIndex(words: List<WordTiming>, positionMs: Long): Int {
        if (words.isEmpty()) return -1
        var lastTimed = -1
        words.forEachIndexed { index, word ->
            val start = word.startMs
            val end = word.endMs
            if (start != null && end != null) {
                lastTimed = index
                if (positionMs in start until end) {
                    return index
                }
                if (positionMs < start) {
                    return if (index == 0) 0 else lastTimed.coerceAtLeast(0)
                }
            }
        }
        return lastTimed
    }
}
