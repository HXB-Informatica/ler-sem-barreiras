package pt.lersembarreiras.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import pt.lersembarreiras.core.model.SyncStatus
import pt.lersembarreiras.core.model.TimestampOrigin
import pt.lersembarreiras.core.model.WordState
import pt.lersembarreiras.core.model.WordTiming
import pt.lersembarreiras.core.packageformat.ContentPackageMapper
import pt.lersembarreiras.lsb.LsbArchive
import pt.lersembarreiras.lsb.LsbException
import pt.lersembarreiras.core.model.ReadingContent

@RunWith(RobolectricTestRunner::class)
class ContentRepositoryTest {
    private lateinit var database: AppDatabase
    private lateinit var repository: ContentRepository

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = ContentRepository(context, database.contentDao())
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun persistsAndReloadsContentWithoutRepeatingProcessing() = runBlocking {
        val created = repository.createDraft("Os animais", "O gato corre.")
        val words = listOf(
            WordTiming("O", 0, 1, 0, 120, 0.9f),
            WordTiming("gato", 2, 6, 130, 400, 0.8f),
            WordTiming("corre.", 7, 13, 410, 720, 0.7f),
        )
        repository.updateWords(created.copy(text = "O gato corre."), words, SyncStatus.COMPLETED, 800)
        val loaded = repository.get(created.id)!!
        assertEquals("Os animais", loaded.title)
        assertEquals(3, loaded.words.size)
        assertEquals(SyncStatus.COMPLETED, loaded.syncStatus)
        assertEquals(800L, loaded.durationMs)
        val observed = repository.observeAll().first()
        assertEquals(1, observed.size)
        val pkg = ContentPackageMapper.decode(ContentPackageMapper.encode(loaded))
        assertEquals("lersembarreiras-content", pkg.format)
        assertTrue(pkg.words.all { it.isTimed })
    }

    @Test
    fun saveAudioDoesNotDeleteWhenSourceIsTheDestination() = runBlocking {
        val created = repository.createDraft("Áudio", "Um dois.")
        val dest = File(repository.contentDirectory(created.id), ContentRepository.AUDIO_FILE)
        dest.writeBytes(byteArrayOf(82, 73, 70, 70, 0, 0, 0, 0))
        val relative = repository.saveAudio(created.id, dest)
        assertEquals("contents/${created.id}/audio.wav", relative)
        assertTrue(dest.exists())
        assertTrue(dest.length() > 0)
    }

    @Test
    fun saveAudioCopiesFromTemporaryFile() {
        runBlocking {
            val created = repository.createDraft("Cópia", "Um dois.")
            val temp = File.createTempFile("rec", ".wav")
            temp.writeBytes(ByteArray(64) { 1 })
            val relative = repository.saveAudio(created.id, temp)
            val stored = File(repository.contentDirectory(created.id), ContentRepository.AUDIO_FILE)
            assertEquals("contents/${created.id}/audio.wav", relative)
            assertTrue(stored.exists())
            assertEquals(64, stored.length())
            temp.delete()
        }
    }

    @Test
    fun reloadPreservesManualWordIdentityAndTimes() = runBlocking {
        val created = repository.createDraft("Frase", "Olá um dois três")
        val words = listOf(
            WordTiming("Olá", 0, 3, 10, 200, 0.9f, id = "w-0-0-3", index = 0, state = WordState.AUTO),
            WordTiming("um", 4, 6, null, null, 0f, needsReview = true, id = "w-1-4-6", index = 1, state = WordState.REVIEW),
            WordTiming(
                text = "dois",
                startChar = 7,
                endChar = 11,
                startMs = 500,
                endMs = 780,
                confidence = 1f,
                manuallyAdjusted = true,
                id = "w-2-7-11",
                index = 2,
                state = WordState.MANUAL,
                timestampOrigin = TimestampOrigin.MANUAL,
            ),
            WordTiming("três", 12, 16, null, null, 0f, needsReview = true, id = "w-3-12-16", index = 3, state = WordState.REVIEW),
        )
        repository.updateWords(created.copy(text = "Olá um dois três"), words, SyncStatus.COMPLETED_WITH_GAPS, 1000)
        val loaded = repository.get(created.id)!!
        assertEquals(4, loaded.words.size)
        assertEquals("w-2-7-11", loaded.words[2].id)
        assertEquals(2, loaded.words[2].index)
        assertEquals(500L, loaded.words[2].startMs)
        assertEquals(WordState.MANUAL, loaded.words[2].resolvedState())
        assertEquals(TimestampOrigin.MANUAL, loaded.words[2].resolvedOrigin())
    }

    @Test
    fun importLsbRoundTripPreservesFourWordsAndAudio() = runBlocking {
        val words = listOf(
            WordTiming("Olá", 0, 3, 50, 280, 1f, id = "w-0-0-3", index = 0, state = WordState.VERIFIED),
            WordTiming("um", 4, 6, 300, 520, 1f, id = "w-1-4-6", index = 1, state = WordState.VERIFIED),
            WordTiming("dois", 7, 11, 900, 1200, 1f, manuallyAdjusted = true, id = "w-2-7-11", index = 2, state = WordState.MANUAL),
            WordTiming("três", 12, 16, 1300, 1750, 1f, id = "w-3-12-16", index = 3, state = WordState.VERIFIED),
        )
        val source = ReadingContent(
            id = "pkg-ola",
            title = "Frase",
            text = "Olá um dois três",
            durationMs = 2000,
            words = words,
            syncStatus = SyncStatus.COMPLETED,
            createdAtEpochMs = 10,
            updatedAtEpochMs = 20,
        )
        val audio = pcmWav(16_000, 2000)
        val zip = ByteArrayOutputStream()
        LsbArchive.export(source, audio, zip, "studio")
        val imported = repository.importLsb(ByteArrayInputStream(zip.toByteArray()))
        assertEquals(4, imported.content.words.size)
        assertEquals(listOf("Olá", "um", "dois", "três"), imported.content.words.map { it.text })
        assertEquals(50L, imported.content.words[0].startMs)
        assertEquals(1750L, imported.content.words[3].endMs)
        assertEquals(WordState.MANUAL, imported.content.words[2].resolvedState())
        val stored = File(repository.contentDirectory(imported.content.id), ContentRepository.AUDIO_FILE)
        assertTrue(stored.exists())
        assertEquals(audio.size.toLong(), stored.length())
        val reopened = repository.get(imported.content.id)!!
        assertEquals(imported.content.words.map { it.startMs }, reopened.words.map { it.startMs })
        val again = repository.importLsb(ByteArrayInputStream(zip.toByteArray()))
        assertTrue(again.duplicate)
        assertEquals(imported.content.id, again.content.id)
    }

    @Test
    fun corruptPackageDoesNotDeleteExistingContent() = runBlocking {
        val created = repository.createDraft("Guardado", "Olá um dois três")
        val error = runCatching { repository.importLsb(ByteArrayInputStream(byteArrayOf(1, 2, 3, 4))) }.exceptionOrNull()
        assertTrue(error is LsbException)
        assertEquals("Guardado", repository.get(created.id)?.title)
    }

    private fun pcmWav(sampleRate: Int, durationMs: Long): ByteArray {
        val frames = (sampleRate * durationMs / 1000L).toInt()
        val data = frames * 2
        val buffer = ByteBuffer.allocate(44 + data).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put("RIFF".toByteArray())
        buffer.putInt(36 + data)
        buffer.put("WAVE".toByteArray())
        buffer.put("fmt ".toByteArray())
        buffer.putInt(16)
        buffer.putShort(1)
        buffer.putShort(1)
        buffer.putInt(sampleRate)
        buffer.putInt(sampleRate * 2)
        buffer.putShort(2)
        buffer.putShort(16)
        buffer.put("data".toByteArray())
        buffer.putInt(data)
        buffer.put(ByteArray(data))
        return buffer.array()
    }
}
