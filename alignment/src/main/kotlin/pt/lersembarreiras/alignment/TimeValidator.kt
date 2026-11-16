package pt.lersembarreiras.alignment

import pt.lersembarreiras.core.model.WordTiming

object TimeValidator {
    fun validate(words: List<WordTiming>, audioDurationMs: Long): List<WordTiming> {
        if (words.isEmpty()) return words
        val duration = audioDurationMs.coerceAtLeast(0)
        var previousEnd = 0L
        return words.map { word ->
            if (!word.isTimed) {
                word.copy(needsReview = true)
            } else {
                var start = word.startMs!!.coerceAtLeast(0)
                var end = word.endMs!!.coerceAtLeast(start)
                if (duration > 0) {
                    start = start.coerceAtMost(duration)
                    end = end.coerceAtMost(duration)
                }
                if (end < start) {
                    val swapped = start
                    start = end
                    end = swapped
                }
                val invalidOrder = start + 8 < previousEnd
                val tooShort = end - start < 20 && word.text.length > 2
                if (word.isTimed) {
                    previousEnd = maxOf(previousEnd, end)
                }
                word.copy(
                    startMs = start,
                    endMs = end,
                    needsReview = word.needsReview || invalidOrder || tooShort || end == start,
                )
            }
        }
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
