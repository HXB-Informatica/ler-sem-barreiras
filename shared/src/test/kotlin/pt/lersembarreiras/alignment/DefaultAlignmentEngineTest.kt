package pt.lersembarreiras.alignment

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pt.lersembarreiras.core.model.RecognizedWord
import pt.lersembarreiras.core.model.TimestampOrigin
import pt.lersembarreiras.core.model.Transcription
import pt.lersembarreiras.core.model.WordState
import pt.lersembarreiras.core.model.WordTiming
import pt.lersembarreiras.shared.AlignmentRequest

class DefaultAlignmentEngineTest {
    private val engine = DefaultAlignmentEngine()

    @Test
    fun olaUmDoisTresAlwaysYieldsFourWordObjects() {
        val result = engine.align(
            AlignmentRequest(
                sourceText = "Olá um dois três",
                transcription = Transcription(
                    words = listOf(
                        rec("ola", 50, 300),
                        rec("dois", 600, 900),
                    ),
                ),
                audioDurationMs = 1200,
            ),
        )
        assertEquals(4, result.words.size)
        assertEquals(listOf(0, 1, 2, 3), result.words.map { it.index })
        assertEquals(listOf("Olá", "um", "dois", "três"), result.words.map { it.text })
        assertTrue(result.words.all { it.id.isNotBlank() })
        assertEquals(result.words.map { it.id }.toSet().size, 4)
    }

    @Test
    fun unmatchedWordsStayReviewWithNullTimes() {
        val result = engine.align(
            AlignmentRequest(
                sourceText = "Olá um dois três",
                transcription = Transcription(words = listOf(rec("ola", 50, 300))),
                audioDurationMs = 1000,
            ),
        )
        val unmatched = result.words.filter { it.text != "Olá" }
        assertTrue(unmatched.isNotEmpty())
        unmatched.forEach { word ->
            assertNull(word.startMs)
            assertNull(word.endMs)
            assertEquals(WordState.REVIEW, word.resolvedState())
            assertEquals(TimestampOrigin.NONE, word.resolvedOrigin())
            assertTrue(word.needsReview)
        }
    }

    @Test
    fun identifiersAndIndicesRemainStableAcrossRealignment() {
        val request = AlignmentRequest(
            sourceText = "Olá um dois três",
            transcription = Transcription(
                words = listOf(rec("ola", 40, 200), rec("um", 220, 350)),
            ),
            audioDurationMs = 900,
        )
        val first = engine.align(request)
        val second = engine.align(request.copy(previousWords = first.words))
        assertEquals(first.words.map { it.id }, second.words.map { it.id })
        assertEquals(first.words.map { it.index }, second.words.map { it.index })
        assertEquals(first.words.map { it.text }, second.words.map { it.text })
    }

    @Test
    fun manualCorrectionsSurviveANewSynchronization() {
        val first = engine.align(
            AlignmentRequest(
                sourceText = "Olá um dois três",
                transcription = Transcription(words = listOf(rec("ola", 10, 200))),
                audioDurationMs = 1000,
            ),
        )
        val dois = first.words[2]
        val corrected = first.words.toMutableList()
        corrected[2] = dois.copy(
            startMs = 500,
            endMs = 780,
            confidence = 1f,
            manuallyAdjusted = true,
            needsReview = false,
            state = WordState.MANUAL,
            timestampOrigin = TimestampOrigin.MANUAL,
        )
        val second = engine.align(
            AlignmentRequest(
                sourceText = "Olá um dois três",
                transcription = Transcription(
                    words = listOf(
                        rec("ola", 10, 180),
                        rec("dois", 300, 400),
                    ),
                ),
                audioDurationMs = 1000,
                previousWords = corrected,
            ),
        )
        val kept = second.words[2]
        assertEquals(dois.id, kept.id)
        assertEquals(2, kept.index)
        assertEquals(500L, kept.startMs)
        assertEquals(780L, kept.endMs)
        assertEquals(WordState.MANUAL, kept.resolvedState())
        assertEquals(TimestampOrigin.MANUAL, kept.resolvedOrigin())
        assertTrue(kept.manuallyAdjusted)
        assertNotEquals(300L, kept.startMs)
    }

    @Test
    fun emptyRecognitionStillKeepsEveryOriginalWord() {
        val result = engine.align(
            AlignmentRequest(
                sourceText = "Olá um dois três",
                transcription = Transcription(words = emptyList()),
                audioDurationMs = 800,
            ),
        )
        assertEquals(4, result.words.size)
        assertTrue(result.words.all { it.resolvedState() == WordState.REVIEW })
        assertTrue(result.words.all { !it.isTimed })
    }

    private fun rec(text: String, startMs: Long, endMs: Long, probability: Float = 0.9f) =
        RecognizedWord(text, startMs, endMs, probability)
}
