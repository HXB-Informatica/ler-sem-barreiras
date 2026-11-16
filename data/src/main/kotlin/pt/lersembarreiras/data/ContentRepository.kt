package pt.lersembarreiras.data

import android.content.Context
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import pt.lersembarreiras.core.model.ReadingContent
import pt.lersembarreiras.core.model.SyncStatus
import pt.lersembarreiras.core.model.WordTiming
import pt.lersembarreiras.core.packageformat.ContentPackageMapper

class ContentRepository(
    private val context: Context,
    private val dao: ContentDao,
) {
    fun observeAll(): Flow<List<ReadingContent>> = dao.observeAll().map { list ->
        list.map { it.toModel() }
    }

    suspend fun get(id: String): ReadingContent? = dao.getById(id)?.toModel()

    suspend fun createDraft(title: String = "", text: String = ""): ReadingContent {
        val now = System.currentTimeMillis()
        val content = ReadingContent(
            id = UUID.randomUUID().toString(),
            title = title.ifBlank { "Novo conteúdo" },
            text = text,
            createdAtEpochMs = now,
            updatedAtEpochMs = now,
        )
        directoryFor(content.id).mkdirs()
        dao.upsert(ContentEntity.from(content))
        return content
    }

    suspend fun save(content: ReadingContent): ReadingContent {
        val updated = content.copy(updatedAtEpochMs = System.currentTimeMillis())
        dao.upsert(ContentEntity.from(updated))
        writeExportablePackage(updated)
        return updated
    }

    suspend fun saveAudio(contentId: String, source: File): String = withContext(Dispatchers.IO) {
        require(source.exists()) { "O ficheiro de áudio de origem não existe." }
        val dir = directoryFor(contentId)
        check(dir.exists() || dir.mkdirs()) { "Não foi possível criar a pasta do conteúdo." }
        val dest = File(dir, AUDIO_FILE)
        if (source.canonicalFile != dest.canonicalFile) {
            source.copyTo(dest, overwrite = true)
        }
        check(dest.exists()) { "O áudio não ficou guardado neste tablet." }
        relativeAudioPath(contentId)
    }

    fun audioFile(content: ReadingContent): File? {
        val relative = content.audioRelativePath ?: return null
        val file = File(context.filesDir, relative)
        return file.takeIf { it.exists() }
    }

    fun contentDirectory(id: String): File = directoryFor(id).also { it.mkdirs() }

    suspend fun updateWords(
        content: ReadingContent,
        words: List<WordTiming>,
        status: SyncStatus,
        durationMs: Long = content.durationMs,
    ): ReadingContent = save(
        content.copy(words = words, syncStatus = status, durationMs = durationMs),
    )

    suspend fun delete(id: String) {
        dao.delete(id)
        withContext(Dispatchers.IO) {
            directoryFor(id).deleteRecursively()
        }
    }

    private fun directoryFor(id: String): File = File(context.filesDir, "contents/$id")

    private fun relativeAudioPath(id: String): String = "contents/$id/$AUDIO_FILE"

    private fun writeExportablePackage(content: ReadingContent) {
        val dir = directoryFor(content.id)
        dir.mkdirs()
        File(dir, PACKAGE_FILE).writeText(ContentPackageMapper.encode(content))
    }

    companion object {
        const val AUDIO_FILE = "audio.wav"
        const val PACKAGE_FILE = "content.json"
    }
}
