package pt.lersembarreiras.core.packageformat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import pt.lersembarreiras.core.model.ReadingContent
import pt.lersembarreiras.core.model.SyncStatus
import pt.lersembarreiras.core.model.TimestampOrigin
import pt.lersembarreiras.core.model.WordState
import pt.lersembarreiras.core.model.WordTiming

class LegacyMigrationTest {
    @Test
    fun migratesVersion1WordsWithoutIdOrState() {
        val legacy = """
            {
              "format": "lersembarreiras-content",
              "version": 1,
              "id": "abc",
              "title": "O gato",
              "text": "O gato corre.",
              "audioFile": "audio.wav",
              "durationMs": 900,
              "words": [
                {
                  "text": "O",
                  "startChar": 0,
                  "endChar": 1,
                  "startMs": 0,
                  "endMs": 120,
                  "confidence": 0.9,
                  "manuallyAdjusted": false,
                  "needsReview": false
                },
                {
                  "text": "gato",
                  "startChar": 2,
                  "endChar": 6,
                  "startMs": 130,
                  "endMs": 400,
                  "confidence": 0.8,
                  "manuallyAdjusted": false,
                  "needsReview": true
                },
                {
                  "text": "corre.",
                  "startChar": 7,
                  "endChar": 13,
                  "startMs": 410,
                  "endMs": 880,
                  "confidence": 0.7,
                  "manuallyAdjusted": true,
                  "needsReview": false
                }
              ],
              "syncStatus": "COMPLETED_WITH_GAPS",
              "createdAtEpochMs": 1,
              "updatedAtEpochMs": 2
            }
        """.trimIndent()

        val decoded = ContentPackageMapper.decode(legacy)
        assertEquals(3, decoded.words.size)
        assertTrue(decoded.words.all { it.id.isNotBlank() })
        assertEquals(listOf(0, 1, 2), decoded.words.map { it.index })
        assertEquals(WordState.AUTO, decoded.words[0].resolvedState())
        assertEquals(WordState.REVIEW, decoded.words[1].resolvedState())
        assertEquals(WordState.MANUAL, decoded.words[2].resolvedState())
        assertEquals(0L, decoded.words[0].startMs)
        assertEquals(880L, decoded.words[2].endMs)
        assertTrue(decoded.words[2].manuallyAdjusted)

        val restored = ContentPackageMapper.fromPackage(decoded, "contents/abc/audio.wav")
        val encoded = ContentPackageMapper.encode(restored)
        val roundTrip = ContentPackageMapper.decode(encoded)
        assertEquals(decoded.words.map { it.id }, roundTrip.words.map { it.id })
        assertEquals(decoded.words.map { it.resolvedState() }, roundTrip.words.map { it.resolvedState() })
        assertEquals(decoded.words.map { it.startMs }, roundTrip.words.map { it.startMs })
    }

    @Test
    fun encodeThenDecodeKeepsManualState() {
        val content = ReadingContent(
            id = "x",
            title = "Frase",
            text = "Olá um dois três",
            durationMs = 1000,
            words = listOf(
                WordTiming("Olá", 0, 3, 10, 200, 0.9f, id = "keep-0", index = 0, state = WordState.AUTO),
                WordTiming("um", 4, 6, null, null, 0f, needsReview = true, id = "keep-1", index = 1, state = WordState.REVIEW),
                WordTiming(
                    text = "dois",
                    startChar = 7,
                    endChar = 11,
                    startMs = 400,
                    endMs = 700,
                    confidence = 1f,
                    manuallyAdjusted = true,
                    id = "keep-2",
                    index = 2,
                    state = WordState.MANUAL,
                    timestampOrigin = TimestampOrigin.MANUAL,
                ),
                WordTiming("três", 12, 16, 710, 900, 0.8f, id = "keep-3", index = 3, state = WordState.VERIFIED),
            ),
            syncStatus = SyncStatus.COMPLETED_WITH_GAPS,
            createdAtEpochMs = 1,
            updatedAtEpochMs = 2,
        )
        val roundTrip = ContentPackageMapper.fromPackage(ContentPackageMapper.decode(ContentPackageMapper.encode(content)), null)
        assertEquals(listOf("keep-0", "keep-1", "keep-2", "keep-3"), roundTrip.words.map { it.id })
        assertEquals(listOf(0, 1, 2, 3), roundTrip.words.map { it.index })
        assertEquals(WordState.MANUAL, roundTrip.words[2].resolvedState())
        assertEquals(400L, roundTrip.words[2].startMs)
        assertEquals(WordState.VERIFIED, roundTrip.words[3].resolvedState())
    }
}
