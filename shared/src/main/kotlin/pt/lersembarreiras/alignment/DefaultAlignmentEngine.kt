package pt.lersembarreiras.alignment

import pt.lersembarreiras.core.model.AlignmentResult
import pt.lersembarreiras.core.model.TimestampOrigin
import pt.lersembarreiras.core.model.WordTiming
import pt.lersembarreiras.core.text.WordTokenizer
import pt.lersembarreiras.shared.AlignmentEngine
import pt.lersembarreiras.shared.AlignmentRequest

/**
 * Motor de alinhamento temporal: parte sempre do texto original e aplica
 * a transcrição já obtida. Não invoca reconhecimento de fala.
 *
 * Palavras MANUAL/VERIFIED com o mesmo índice e texto sobrevivem a nova sincronização.
 */
class DefaultAlignmentEngine(
    private val aligner: WordAligner = WordAligner(),
) : AlignmentEngine {
    override fun align(request: AlignmentRequest): AlignmentResult {
        val tokens = WordTokenizer.tokenize(request.sourceText)
        val automatic = aligner.align(tokens, request.transcription.words, request.audioDurationMs)
        val identified = WordIdentity.ensure(request.sourceText, automatic.words)
        val merged = mergeProtected(identified, request.previousWords)
        val validated = TimeValidator.inspect(merged, request.audioDurationMs)
        return AlignmentResult(
            words = validated.words,
            alignedCount = validated.words.count { it.isTimed },
            reviewCount = validated.words.count { it.requiresReview() },
            unusedRecognizedCount = automatic.unusedRecognizedCount,
            intervalIssues = validated.issues,
        )
    }

    internal fun mergeProtected(
        automatic: List<WordTiming>,
        previous: List<WordTiming>,
    ): List<WordTiming> {
        if (previous.isEmpty()) return automatic
        return automatic.mapIndexed { index, auto ->
            val prev = previous.getOrNull(index)
                ?: previous.firstOrNull { it.id.isNotBlank() && it.id == auto.id }
            if (prev != null && prev.isProtectedFromResync() && sameLexicalWord(prev, auto)) {
                auto.copy(
                    id = prev.id.ifBlank { auto.id },
                    index = auto.index,
                    startMs = prev.startMs,
                    endMs = prev.endMs,
                    confidence = prev.confidence,
                    manuallyAdjusted = true,
                    needsReview = false,
                    state = prev.resolvedState(),
                    timestampOrigin = TimestampOrigin.MANUAL.takeIf {
                        prev.resolvedOrigin() == TimestampOrigin.NONE
                    } ?: prev.resolvedOrigin(),
                )
            } else {
                auto.copy(id = prev?.id?.takeIf { it.isNotBlank() } ?: auto.id)
            }
        }
    }

    private fun sameLexicalWord(previous: WordTiming, automatic: WordTiming): Boolean {
        if (previous.text == automatic.text) return true
        return WordTokenizer.normalizeForMatch(previous.text) ==
            WordTokenizer.normalizeForMatch(automatic.text)
    }
}
