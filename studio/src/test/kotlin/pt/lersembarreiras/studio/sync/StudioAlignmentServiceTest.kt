package pt.lersembarreiras.studio.sync

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pt.lersembarreiras.core.model.RecognizedWord
import pt.lersembarreiras.core.model.SyncStatus
import pt.lersembarreiras.core.model.Transcription
import pt.lersembarreiras.core.model.WordState
import pt.lersembarreiras.core.packageformat.ContentPackageMapper
import pt.lersembarreiras.editor.IntervalEdge
import pt.lersembarreiras.editor.SyncEditor
import pt.lersembarreiras.studio.audio.StreamingWavWriter
import pt.lersembarreiras.studio.project.StudioProjectFile
import pt.lersembarreiras.studio.project.StudioProjectStore

class StudioAlignmentServiceTest {
    private val ola = "Olá um dois três"

    private fun wav(): File {
        val file = File.createTempFile("sync", ".wav")
        StreamingWavWriter(file, 16_000, 16, 1).use { it.write(ByteArray(8_000), 0, 8_000) }
        return file
    }

    @Test
    fun olaUmDoisTresShowsFourWordsAndAllowsReviewFixesThenReload() {
        val transcriber = LocalTranscriber { _, _, _, _ ->
            TranscriptionOutcome(
                transcription = Transcription(
                    words = listOf(
                        RecognizedWord("ola", 50, 280, 0.9f),
                        RecognizedWord("um", 300, 520, 0.8f),
                    ),
                ),
            )
        }
        val wav = File.createTempFile("ola", ".wav")
        StreamingWavWriter(wav, 16_000, 16, 1).use { it.write(ByteArray(16_000 * 2), 0, 16_000 * 2) }
        val service = StudioAlignmentService(transcriber)
        val result = service.synchronize(ola, wav, 2000)
        assertEquals(4, result.alignment.words.size)
        assertEquals(listOf("Olá", "um", "dois", "três"), result.alignment.words.map { it.text })
        assertTrue(result.alignment.words[0].isTimed)
        assertTrue(result.alignment.words[1].isTimed)
        assertNull(result.alignment.words[2].startMs)
        assertNull(result.alignment.words[3].startMs)
        assertEquals(WordState.REVIEW, result.alignment.words[2].resolvedState())
        assertEquals(SyncStatus.COMPLETED_WITH_GAPS, result.alignment.status)

        val editor = SyncEditor()
        editor.load(result.alignment.words, 2000)
        editor.setInterval(2, 900, 1200)
        editor.setInterval(3, 1300, 1700)
        editor.nudge(3, IntervalEdge.END, 50)
        editor.verify(0)
        editor.verify(1)
        editor.verify(2)
        editor.verify(3)
        assertTrue(editor.snapshot.words.all { it.resolvedState() == WordState.VERIFIED })
        assertEquals(4, editor.snapshot.words.count { it.isTimed })

        val dir = kotlin.io.path.createTempDirectory("lsb-fase5").toFile()
        val store = StudioProjectStore()
        val created = store.create(dir, "Frase")
        val saved = created.copy(
            content = created.content.copy(
                text = ola,
                durationMs = 2000,
                words = editor.snapshot.words,
                syncStatus = editor.snapshot.status,
            ),
        )
        store.save(dir, saved)
        val reopened = store.load(dir)
        assertEquals(4, reopened.content.words.size)
        assertEquals(listOf("Olá", "um", "dois", "três"), reopened.content.words.map { it.text })
        assertEquals(900L, reopened.content.words[2].startMs)
        assertEquals(1750L, reopened.content.words[3].endMs)
        assertTrue(reopened.content.words.all { it.resolvedState() == WordState.VERIFIED })
        val roundTrip = ContentPackageMapper.fromPackage(
            ContentPackageMapper.decode(File(dir, StudioProjectFile.CONTENT_FILE).readText()),
            StudioProjectFile.ORIGINAL_AUDIO,
        )
        assertEquals(reopened.content.words.map { it.startMs }, roundTrip.words.map { it.startMs })
        wav.delete()
        dir.deleteRecursively()
    }

    @Test
    fun silenceLeavesAllWordsForManualAlignment() {
        val service = StudioAlignmentService(
            LocalTranscriber { _, _, _, _ -> TranscriptionOutcome(transcription = Transcription(emptyList())) },
        )
        val wav = wav()
        val result = service.synchronize(ola, wav, 1500)
        wav.delete()
        assertEquals(4, result.alignment.words.size)
        assertTrue(result.alignment.words.all { it.requiresReview() })
    }

    @Test
    fun mismatchedAudioStillKeepsOriginalText() {
        val service = StudioAlignmentService(
            LocalTranscriber { _, _, _, _ ->
                TranscriptionOutcome(transcription = Transcription(listOf(RecognizedWord("banana", 0, 400, 0.4f))))
            },
        )
        val wav = wav()
        val result = service.synchronize(ola, wav, 800)
        wav.delete()
        assertEquals(listOf("Olá", "um", "dois", "três"), result.alignment.words.map { it.text })
        assertTrue(result.alignment.reviewCount >= 3)
    }

    @Test
    fun omittedAndRepeatedWordsStayIndependent() {
        val service = StudioAlignmentService(
            LocalTranscriber { _, _, _, _ ->
                TranscriptionOutcome(
                    transcription = Transcription(
                        listOf(
                            RecognizedWord("o", 0, 80, 1f),
                            RecognizedWord("gato", 90, 300, 1f),
                            RecognizedWord("gato", 500, 800, 1f),
                        ),
                    ),
                )
            },
        )
        val wav = wav()
        val result = service.synchronize("o gato o gato", wav, 900)
        wav.delete()
        assertEquals(4, result.alignment.words.size)
        assertEquals(listOf("o", "gato", "o", "gato"), result.alignment.words.map { it.text })
        assertEquals(result.alignment.words[1].startMs, 90L)
        assertTrue(result.alignment.words[2].requiresReview() || result.alignment.words[2].isTimed)
        assertTrue(result.alignment.words.map { it.id }.toSet().size == 4)
    }

    @Test
    fun engineFailureStillCreatesEditableWords() {
        val service = StudioAlignmentService(
            LocalTranscriber { _, _, _, _ ->
                TranscriptionOutcome(error = "motor ausente", engineAvailable = false)
            },
        )
        val wav = wav()
        val result = service.synchronize(ola, wav, 1000)
        wav.delete()
        assertTrue(result.engineFailed)
        assertEquals(4, result.alignment.words.size)
        assertTrue(result.alignment.words.all { !it.isTimed })
    }
}
