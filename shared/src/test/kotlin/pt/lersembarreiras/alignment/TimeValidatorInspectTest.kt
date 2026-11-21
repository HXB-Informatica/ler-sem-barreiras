package pt.lersembarreiras.alignment

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pt.lersembarreiras.core.model.IntervalIssueType
import pt.lersembarreiras.core.model.TimestampOrigin
import pt.lersembarreiras.core.model.WordState
import pt.lersembarreiras.core.model.WordTiming

class TimeValidatorInspectTest {
    @Test
    fun reportsOverlapOrderDurationAndIntegrity() {
        val words = listOf(
            WordTiming("um", 0, 2, startMs = 0, endMs = 400, confidence = 1f, id = "w-0", index = 0, state = WordState.AUTO),
            WordTiming("dois", 3, 7, startMs = 200, endMs = 250, confidence = 1f, id = "w-1", index = 1, state = WordState.AUTO),
            WordTiming("tres", 8, 12, startMs = 9000, endMs = 9100, confidence = 1f, id = "w-2", index = 2, state = WordState.AUTO),
            WordTiming("quatro", 13, 19, startMs = null, endMs = null, id = "w-3", index = 3, state = WordState.REVIEW),
        )
        val result = TimeValidator.inspect(words, audioDurationMs = 1000)
        val types = result.issues.map { it.type }.toSet()
        assertTrue("dois deixou de sobrepor-se a um", result.words[1].startMs!! >= result.words[0].endMs!!)
        assertTrue(result.words[1].needsReview)
        assertTrue(types.contains(IntervalIssueType.OUTSIDE_AUDIO))
        assertTrue(types.contains(IntervalIssueType.MISSING_TIME))
        assertNull(result.words[2].startMs)
        assertTrue(result.words[2].needsReview)
    }

    @Test
    fun doesNotDemoteManualWordsWhenIntervalsOverlap() {
        val words = listOf(
            WordTiming("um", 0, 2, 0, 300, 1f, id = "a", index = 0, state = WordState.AUTO),
            WordTiming(
                text = "dois",
                startChar = 3,
                endChar = 7,
                startMs = 100,
                endMs = 200,
                confidence = 1f,
                manuallyAdjusted = true,
                needsReview = false,
                id = "b",
                index = 1,
                state = WordState.MANUAL,
                timestampOrigin = TimestampOrigin.MANUAL,
            ),
        )
        val result = TimeValidator.inspect(words, 1000)
        assertEquals(WordState.MANUAL, result.words[1].resolvedState())
        assertFalse(result.words[1].needsReview)
        assertTrue(result.issues.any { it.type == IntervalIssueType.OVERLAP })
    }
}
