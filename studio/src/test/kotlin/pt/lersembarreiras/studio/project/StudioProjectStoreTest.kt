package pt.lersembarreiras.studio.project

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import pt.lersembarreiras.studio.audio.RecognitionAudio
import pt.lersembarreiras.studio.audio.StreamingWavWriter

class StudioProjectStoreTest {
    @Test
    fun createSaveReloadAndAutosaveRecover() {
        val dir = kotlin.io.path.createTempDirectory("lsb-studio").toFile()
        val store = StudioProjectStore()
        val created = store.create(dir, "Olá um dois três")
        assertTrue(File(dir, StudioProjectFile.STUDIO_FILE).exists())
        val updated = created.copy(
            content = created.content.copy(text = "Olá um dois três", title = "Frase"),
        )
        store.autosave(dir, updated)
        File(dir, StudioProjectFile.STUDIO_FILE).writeText(File(dir, StudioProjectFile.STUDIO_FILE).readText())
        Thread.sleep(20)
        File(dir, StudioProjectFile.AUTOSAVE_FILE).setLastModified(System.currentTimeMillis() + 1_000)
        val loaded = store.load(dir)
        assertTrue(loaded.recoveredAfterCrash)
        assertEquals("Olá um dois três", loaded.content.text)
        store.save(dir, loaded.copy(content = loaded.content.copy(title = "Frase")))
        val again = store.load(dir)
        assertEquals("Frase", again.content.title)
        dir.deleteRecursively()
    }

    @Test
    fun recognitionCopyIs16kMonoAndOriginalUntouched() {
        val dir = kotlin.io.path.createTempDirectory("lsb-wav").toFile()
        val original = File(dir, "audio-original.wav")
        val frames = 48_000
        StreamingWavWriter(original, 48_000, 16, 1).use { writer ->
            writer.write(ByteArray(frames * 2), 0, frames * 2)
        }
        val originalSize = original.length()
        val copy = File(dir, "audio-16k.wav")
        RecognitionAudio.writeMono16k(original, copy)
        val copyProps = StreamingWavWriter.inspect(copy)
        val originalProps = StreamingWavWriter.inspect(original)
        assertEquals(48_000, originalProps.sampleRate)
        assertEquals(originalSize, original.length())
        assertEquals(16_000, copyProps.sampleRate)
        assertEquals(1, copyProps.channels)
        assertEquals(16, copyProps.bitsPerSample)
        dir.deleteRecursively()
    }
}
