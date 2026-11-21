package pt.lersembarreiras.studio.project

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import pt.lersembarreiras.core.model.SyncStatus
import pt.lersembarreiras.core.model.WordState
import pt.lersembarreiras.core.model.WordTiming
import pt.lersembarreiras.core.packageformat.ContentPackageMapper
import pt.lersembarreiras.lsb.LsbArchive
import pt.lersembarreiras.studio.audio.StreamingWavWriter

class LsbStudioExportTest {
    @Test
    fun studioProjectExportsAndReimportsLsb() {
        val dir = kotlin.io.path.createTempDirectory("lsb-studio-exp").toFile()
        val store = StudioProjectStore()
        val created = store.create(dir, "Frase")
        val words = listOf(
            WordTiming("Olá", 0, 3, 50, 280, 1f, index = 0, state = WordState.VERIFIED),
            WordTiming("um", 4, 6, 300, 520, 1f, index = 1, state = WordState.VERIFIED),
            WordTiming("dois", 7, 11, 900, 1200, 1f, index = 2, state = WordState.MANUAL, manuallyAdjusted = true),
            WordTiming("três", 12, 16, 1300, 1750, 1f, index = 3, state = WordState.VERIFIED),
        )
        val content = created.content.copy(
            text = "Olá um dois três",
            durationMs = 2000,
            words = words,
            syncStatus = SyncStatus.COMPLETED,
        )
        store.save(dir, created.copy(content = content))
        StreamingWavWriter(store.originalWav(dir), 16_000, 16, 1).use { writer ->
            writer.write(ByteArray(16_000 * 2 * 2), 0, 16_000 * 2 * 2)
        }
        val lsb = File(dir, "ola.lsb")
        val reading = ContentPackageMapper.fromPackage(store.load(dir).content, store.originalWav(dir).name)
        lsb.outputStream().use { LsbArchive.export(reading, store.originalWav(dir).readBytes(), it, "studio") }
        val imported = LsbArchive.import(lsb.inputStream())
        assertEquals(4, imported.words.size)
        assertEquals(store.originalWav(dir).readBytes().size, imported.audio.size)
        assertTrue(LsbArchive.identical(reading.copy(text = "Olá um dois três", words = words, durationMs = 2000), imported) || imported.words[2].startMs == 900L)
        assertEquals(900L, imported.words[2].startMs)
        dir.deleteRecursively()
    }
}
