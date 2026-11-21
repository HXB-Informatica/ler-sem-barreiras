package pt.lersembarreiras.alignment

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pt.lersembarreiras.core.model.RecognizedWord
import pt.lersembarreiras.core.model.SyncStatus

class WordAlignerTest {
    private val aligner = WordAligner()

    @Test
    fun matchesWordsWithTimestampsIgnoringCaseAndPunctuation() {
        val result = aligner.align(
            sourceText = "Olá, mundo!",
            recognized = listOf(
                rec("ola", 100, 400, 0.9f),
                rec("mundo", 420, 800, 0.8f),
            ),
            audioDurationMs = 1000,
        )
        assertEquals(2, result.alignedCount)
        assertEquals("Olá,", result.words[0].text)
        assertEquals(100L, result.words[0].startMs)
        assertEquals("mundo!", result.words[1].text)
        assertEquals(SyncStatus.COMPLETED, result.status)
    }

    @Test
    fun keepsRepeatedWordsInOrder() {
        val result = aligner.align(
            sourceText = "o gato o gato",
            recognized = listOf(
                rec("o", 0, 80),
                rec("gato", 90, 300),
                rec("o", 400, 470),
                rec("gato", 480, 700),
            ),
            audioDurationMs = 800,
        )
        assertEquals(listOf(0L, 90L, 400L, 480L), result.words.map { it.startMs })
    }

    @Test
    fun interpolatesShortInternalGapsAsReviewNotAsr() {
        val result = aligner.align(
            sourceText = "o gato preto corre",
            recognized = listOf(
                rec("o", 0, 80),
                rec("gato", 90, 300),
                rec("corre", 700, 980),
            ),
            audioDurationMs = 1000,
        )
        val omitted = result.words.first { it.text == "preto" }
        assertEquals(300L, omitted.startMs)
        assertEquals(700L, omitted.endMs)
        assertTrue(omitted.needsReview)
        assertEquals(pt.lersembarreiras.core.model.TimestampOrigin.ALIGNED, omitted.resolvedOrigin())
        assertEquals(4, result.alignedCount)
        assertEquals(SyncStatus.COMPLETED_WITH_GAPS, result.status)
    }

    @Test
    fun doesNotInventTimesAcrossLongPauses() {
        val result = aligner.align(
            sourceText = "o gato preto corre",
            recognized = listOf(
                rec("o", 0, 80),
                rec("gato", 90, 300),
                rec("corre", 2200, 2500),
            ),
            audioDurationMs = 2600,
        )
        val omitted = result.words.first { it.text == "preto" }
        assertNull(omitted.startMs)
        assertNull(omitted.endMs)
        assertTrue(omitted.needsReview)
    }

    @Test
    fun ignoresHesitationsAndExtraRecognizedWords() {
        val result = aligner.align(
            sourceText = "bom dia",
            recognized = listOf(
                rec("uh", 0, 120, 0.2f),
                rec("bom", 150, 320),
                rec("dia", 340, 520),
            ),
            audioDurationMs = 600,
        )
        assertEquals(150L, result.words[0].startMs)
        assertEquals(340L, result.words[1].startMs)
        assertEquals(2, result.alignedCount)
    }

    @Test
    fun alignsDespiteAsrSpellingErrors() {
        val result = aligner.align(
            sourceText = "rapido",
            recognized = listOf(rec("rapidoo", 10, 400, 0.6f)),
            audioDurationMs = 500,
        )
        assertEquals(10L, result.words[0].startMs)
        assertTrue(result.words[0].needsReview)
    }

    @Test
    fun handlesLongPausesBetweenWords() {
        val result = aligner.align(
            sourceText = "um dois",
            recognized = listOf(
                rec("um", 0, 200),
                rec("dois", 2400, 2700),
            ),
            audioDurationMs = 3000,
        )
        assertEquals(0L, result.words[0].startMs)
        assertEquals(2400L, result.words[1].startMs)
        assertFalse(result.words.any { !it.isTimed })
    }

    @Test
    fun emptyRecognitionMarksEveryWordForReview() {
        val result = aligner.align("três palavras aqui", emptyList(), 1000)
        assertEquals(0, result.alignedCount)
        assertTrue(result.words.all { it.needsReview && it.startMs == null })
        assertEquals(SyncStatus.FAILED, result.status)
    }

    @Test
    fun similarityTreatsAccentInsensitiveTokensAsEqualAfterNormalization() {
        assertTrue(aligner.similarity("acao", "acao") > 0)
        assertTrue(aligner.similarity("casa", "casas") > 0)
        assertTrue(aligner.similarity("mesa", "gato") < 0)
    }

    @Test
    fun matchesSpokenDigitsToNumberWords() {
        val result = aligner.align(
            sourceText = "um dois três",
            recognized = listOf(
                rec("1", 0, 120),
                rec("2", 140, 280),
                rec("3", 300, 440),
            ),
            audioDurationMs = 500,
        )
        assertEquals(3, result.alignedCount)
        assertEquals(0L, result.words[0].startMs)
        assertEquals(140L, result.words[1].startMs)
    }

    @Test
    fun mergesSplitTokensAndIgnoresFillers() {
        val result = aligner.align(
            sourceText = "vamos ler",
            recognized = listOf(
                rec("uh", 0, 80, 0.2f),
                rec("va", 100, 220, 0.8f),
                rec("mos", 220, 360, 0.8f),
                rec("ler", 400, 560, 0.9f),
            ),
            audioDurationMs = 600,
        )
        assertEquals(100L, result.words[0].startMs)
        assertEquals(400L, result.words[1].startMs)
        assertEquals(2, result.alignedCount)
    }

    private fun rec(
        text: String,
        startMs: Long,
        endMs: Long,
        probability: Float = 0.9f,
    ) = RecognizedWord(text, startMs, endMs, probability)
}
