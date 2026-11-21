package pt.lersembarreiras.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pt.lersembarreiras.core.model.IntervalIssueType
import pt.lersembarreiras.core.model.ReadingContent
import pt.lersembarreiras.core.model.SyncStatus
import pt.lersembarreiras.core.model.TimestampOrigin
import pt.lersembarreiras.core.model.WordState
import pt.lersembarreiras.core.model.WordTiming
import pt.lersembarreiras.core.packageformat.ContentPackageMapper

class SyncEditorTest {
    @Test
    fun olaUmDoisTresAllowsAssigningMissingWordsAndPersists() {
        val editor = SyncEditor()
        editor.load(partialOlaUmDoisTres(), durationMs = 2000)
        assertEquals(4, editor.snapshot.words.size)
        assertTrue(editor.snapshot.words[0].isTimed)
        assertTrue(editor.snapshot.words[1].isTimed)
        assertNull(editor.snapshot.words[2].startMs)
        assertNull(editor.snapshot.words[3].startMs)
        assertEquals(WordState.REVIEW, editor.snapshot.words[2].resolvedState())

        editor.select(2)
        editor.markStart(2, 900)
        editor.markEnd(2, 1200)
        editor.select(3)
        editor.setInterval(3, 1300, 1700)
        editor.nudge(3, IntervalEdge.END, 50)
        editor.verify(0)
        editor.verify(1)
        editor.verify(2)
        editor.verify(3)

        val words = editor.snapshot.words
        assertEquals(4, words.size)
        words.forEach { word ->
            assertTrue(word.text, word.isTimed)
            assertNotNull(word.startMs)
            assertTrue(word.endMs!! > word.startMs!!)
        }
        assertEquals(900L, words[2].startMs)
        assertEquals(1200L, words[2].endMs)
        assertEquals(1750L, words[3].endMs)
        assertTrue(words.all { it.resolvedState() == WordState.VERIFIED })
        assertEquals(SyncStatus.COMPLETED, editor.snapshot.status)
        assertEquals(listOf(0, 1, 2, 3), words.map { it.index })

        val content = ReadingContent(
            id = "demo",
            title = "Frase",
            text = "Olá um dois três",
            durationMs = 2000,
            words = words,
            syncStatus = editor.snapshot.status,
            createdAtEpochMs = 1,
            updatedAtEpochMs = 2,
        )
        val restored = ContentPackageMapper.fromPackage(
            ContentPackageMapper.decode(ContentPackageMapper.encode(content)),
            null,
        )
        assertEquals(words.map { it.id }, restored.words.map { it.id })
        assertEquals(words.map { it.startMs }, restored.words.map { it.startMs })
        assertEquals(words.map { it.endMs }, restored.words.map { it.endMs })
        assertTrue(restored.words.all { it.resolvedState() == WordState.VERIFIED })
    }

    @Test
    fun undoRedoRestoresManualInterval() {
        val editor = SyncEditor()
        editor.load(partialOlaUmDoisTres(), 2000)
        editor.setInterval(2, 800, 1100)
        assertEquals(800L, editor.snapshot.words[2].startMs)
        editor.undo()
        assertNull(editor.snapshot.words[2].startMs)
        editor.redo()
        assertEquals(800L, editor.snapshot.words[2].startMs)
        assertEquals(WordState.MANUAL, editor.snapshot.words[2].resolvedState())
    }

    @Test
    fun repeatedWordsKeepIndependentIntervals() {
        val editor = SyncEditor()
        val words = listOf(
            timed("o", 0, 1, 0, 80, 0),
            timed("gato", 2, 6, 90, 300, 1),
            timed("o", 7, 8, 400, 470, 2),
            timed("gato", 9, 13, 480, 700, 3),
        )
        editor.load(words, 800)
        editor.setInterval(2, 410, 450)
        assertEquals(0L, editor.snapshot.words[0].startMs)
        assertEquals(410L, editor.snapshot.words[2].startMs)
        assertEquals("o", editor.snapshot.words[0].text)
        assertEquals("o", editor.snapshot.words[2].text)
        assertEquals(listOf(0, 1, 2, 3), editor.snapshot.words.map { it.index })
    }

    @Test
    fun longPauseDoesNotFillTheGap() {
        val editor = SyncEditor()
        editor.load(
            listOf(
                timed("um", 0, 2, 0, 200, 0),
                timed("dois", 3, 7, 2400, 2700, 1),
            ),
            3000,
        )
        assertEquals(200L, editor.snapshot.words[0].endMs)
        assertEquals(2400L, editor.snapshot.words[1].startMs)
        assertTrue(editor.snapshot.words[1].startMs!! - editor.snapshot.words[0].endMs!! > 2000)
    }

    @Test
    fun overlapIsReportedButManualTimesAreKept() {
        val editor = SyncEditor()
        editor.load(partialOlaUmDoisTres(), 2000)
        editor.setInterval(2, 50, 250)
        assertEquals(50L, editor.snapshot.words[2].startMs)
        assertTrue(editor.snapshot.issues.any { it.type == IntervalIssueType.OVERLAP })
        assertEquals(WordState.MANUAL, editor.snapshot.words[2].resolvedState())
    }

    @Test
    fun untimedWordCanReceiveBothEdgesFromPlayback() {
        val editor = SyncEditor()
        editor.load(partialOlaUmDoisTres(), 2000)
        editor.markStart(3, 1400)
        assertEquals(1400L, editor.snapshot.words[3].startMs)
        assertNotNull(editor.snapshot.words[3].endMs)
        editor.markEnd(3, 1800)
        assertEquals(1800L, editor.snapshot.words[3].endMs)
        assertFalse(editor.snapshot.words[3].requiresReview())
    }

    private fun partialOlaUmDoisTres(): List<WordTiming> = listOf(
        timed("Olá", 0, 3, 40, 280, 0, WordState.AUTO),
        timed("um", 4, 6, 300, 520, 1, WordState.AUTO),
        WordTiming("dois", 7, 11, null, null, 0f, needsReview = true, id = "w-2-7-11", index = 2, state = WordState.REVIEW),
        WordTiming("três", 12, 16, null, null, 0f, needsReview = true, id = "w-3-12-16", index = 3, state = WordState.REVIEW),
    )

    private fun timed(
        text: String,
        startChar: Int,
        endChar: Int,
        startMs: Long,
        endMs: Long,
        index: Int,
        state: WordState = WordState.AUTO,
    ) = WordTiming(
        text = text,
        startChar = startChar,
        endChar = endChar,
        startMs = startMs,
        endMs = endMs,
        confidence = 0.9f,
        id = "w-$index-$startChar-$endChar",
        index = index,
        state = state,
        timestampOrigin = TimestampOrigin.ASR,
    )
}
