package pt.lersembarreiras.alignment

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import pt.lersembarreiras.core.model.WordTiming

class TimeValidatorTest {
    @Test
    fun clampsIntervalsToAudioDuration() {
        val words = listOf(
            WordTiming("um", 0, 2, startMs = 0, endMs = 5000, confidence = 1f),
        )
        val result = TimeValidator.validate(words, audioDurationMs = 1000)
        assertEquals(1000L, result[0].endMs)
    }

    @Test
    fun flagsNonMonotonicIntervals() {
        val words = listOf(
            WordTiming("um", 0, 2, 100, 300, 0.9f),
            WordTiming("dois", 3, 7, 50, 80, 0.9f),
        )
        val result = TimeValidator.validate(words, 1000)
        assertTrue(result[1].needsReview)
    }

    @Test
    fun flagsMissingTimesAsInvalid() {
        val words = listOf(WordTiming("olá", 0, 3, null, null, 0f, needsReview = false))
        val result = TimeValidator.validate(words, 1000)
        assertTrue(result[0].needsReview)
    }

    @Test
    fun currentWordUsesMediaPositionWindow() {
        val words = listOf(
            WordTiming("um", 0, 2, 0, 250, 1f),
            WordTiming("dois", 3, 7, 260, 500, 1f),
        )
        assertEquals(0, TimeValidator.currentWordIndex(words, 10))
        assertEquals(1, TimeValidator.currentWordIndex(words, 300))
        assertEquals(1, TimeValidator.currentWordIndex(words, 800))
    }
}
