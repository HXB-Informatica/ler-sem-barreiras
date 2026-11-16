package pt.lersembarreiras.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import java.io.File
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
import pt.lersembarreiras.core.model.WordTiming
import pt.lersembarreiras.core.packageformat.ContentPackageMapper

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
}
