package pt.lersembarreiras.core.packageformat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import pt.lersembarreiras.core.model.ReadingContent
import pt.lersembarreiras.core.model.SyncStatus
import pt.lersembarreiras.core.model.WordTiming

class ContentPackageMapperTest {
    @Test
    fun exportAndImportPreserveWordTimings() {
        val content = ReadingContent(
            id = "abc",
            title = "O gato",
            text = "O gato corre.",
            audioRelativePath = "contents/abc/audio.wav",
            durationMs = 900,
            words = listOf(
                WordTiming("O", 0, 1, 0, 120, 0.9f),
                WordTiming("gato", 2, 6, 130, 400, 0.8f, needsReview = true),
                WordTiming("corre.", 7, 13, 410, 880, 0.7f, manuallyAdjusted = true),
            ),
            syncStatus = SyncStatus.COMPLETED_WITH_GAPS,
            createdAtEpochMs = 1,
            updatedAtEpochMs = 2,
        )
        val encoded = ContentPackageMapper.encode(content)
        val decoded = ContentPackageMapper.decode(encoded)
        assertEquals("lersembarreiras-content", decoded.format)
        assertEquals(1, decoded.version)
        assertEquals("O gato", decoded.title)
        assertEquals(3, decoded.words.size)
        assertTrue(decoded.words[1].needsReview)
        assertTrue(decoded.words[2].manuallyAdjusted)
        val restored = ContentPackageMapper.fromPackage(decoded, "contents/abc/audio.wav")
        assertEquals(content.id, restored.id)
        assertEquals(content.words.map { it.text }, restored.words.map { it.text })
        assertEquals(content.words.map { it.startMs }, restored.words.map { it.startMs })
        assertEquals(listOf(0, 1, 2), restored.words.map { it.index })
        assertTrue(restored.words.all { it.id.isNotBlank() })
        assertTrue(restored.words[2].manuallyAdjusted)
    }
}
