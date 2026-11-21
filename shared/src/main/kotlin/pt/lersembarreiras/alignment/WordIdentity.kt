package pt.lersembarreiras.alignment

import pt.lersembarreiras.core.model.TextToken
import pt.lersembarreiras.core.model.TimestampOrigin
import pt.lersembarreiras.core.model.WordState
import pt.lersembarreiras.core.model.WordTiming
import pt.lersembarreiras.core.text.WordTokenizer

object WordIdentity {
    fun idFor(index: Int, startChar: Int, endChar: Int): String = "w-$index-$startChar-$endChar"

    fun ensure(sourceText: String, words: List<WordTiming>): List<WordTiming> {
        val tokens = WordTokenizer.tokenize(sourceText)
        if (tokens.isEmpty()) return emptyList()
        if (words.size == tokens.size) {
            return tokens.mapIndexed { index, token -> populate(words[index], token, index) }
        }
        return tokens.mapIndexed { index, token ->
            val previous = words.getOrNull(index)?.takeIf { matches(it, token) }
                ?: words.firstOrNull { it.index == index && matches(it, token) }
            if (previous != null) populate(previous, token, index) else unmatched(token, index)
        }
    }

    fun fromToken(
        token: TextToken,
        index: Int,
        startMs: Long?,
        endMs: Long?,
        confidence: Float,
        state: WordState,
        origin: TimestampOrigin,
        needsReview: Boolean,
        existingId: String = "",
    ): WordTiming {
        val review = needsReview || startMs == null || endMs == null
        val resolvedState = if (state == WordState.AUTO && review) WordState.REVIEW else state
        return WordTiming(
            text = token.original,
            startChar = token.startChar,
            endChar = token.endChar,
            startMs = startMs,
            endMs = endMs,
            confidence = confidence,
            manuallyAdjusted = resolvedState == WordState.MANUAL || resolvedState == WordState.VERIFIED,
            needsReview = resolvedState == WordState.REVIEW,
            id = existingId.ifBlank { idFor(index, token.startChar, token.endChar) },
            index = index,
            state = resolvedState,
            timestampOrigin = origin,
        )
    }

    fun unmatched(token: TextToken, index: Int, existingId: String = ""): WordTiming = fromToken(
        token = token,
        index = index,
        startMs = null,
        endMs = null,
        confidence = 0f,
        state = WordState.REVIEW,
        origin = TimestampOrigin.NONE,
        needsReview = true,
        existingId = existingId,
    )

    fun populate(word: WordTiming, token: TextToken, index: Int): WordTiming {
        val state = word.resolvedState()
        val origin = word.resolvedOrigin()
        return fromToken(
            token = token,
            index = index,
            startMs = word.startMs,
            endMs = word.endMs,
            confidence = word.confidence,
            state = state,
            origin = origin,
            needsReview = word.needsReview || state == WordState.REVIEW,
            existingId = word.id,
        )
    }

    fun matches(word: WordTiming, token: TextToken): Boolean {
        if (word.text == token.original) return true
        return WordTokenizer.normalizeForMatch(word.text) == token.normalized
    }
}
