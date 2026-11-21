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
import pt.lersembarreiras.lsb.LsbArchive
import pt.lersembarreiras.lsb.LsbException
import pt.lersembarreiras.lsb.LsbImportResult
import pt.lersembarreiras.shared.AlignmentRepository
import java.io.InputStream

class ContentRepository(
    private val context: Context,
    private val dao: ContentDao,
) : AlignmentRepository {
    override fun observeAll(): Flow<List<ReadingContent>> = dao.observeAll().map { list ->
        list.map { it.toModel() }
    }

    override suspend fun get(id: String): ReadingContent? = dao.getById(id)?.toModel()

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

    override suspend fun save(content: ReadingContent): ReadingContent {
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

    override suspend fun updateWords(
        content: ReadingContent,
        words: List<WordTiming>,
        status: SyncStatus,
        durationMs: Long,
    ): ReadingContent = save(
        content.copy(words = words, syncStatus = status, durationMs = durationMs),
    )

    suspend fun importLsb(input: InputStream): LsbImportResult = withContext(Dispatchers.IO) {
        val bundle = LsbArchive.import(input)
        val existing = dao.getById(bundle.manifest.contentId)?.toModel()
        if (existing != null && LsbArchive.identical(existing, bundle)) {
            return@withContext LsbImportResult(existing, duplicate = true)
        }
        val id = if (existing == null) bundle.manifest.contentId else UUID.randomUUID().toString()
        val remapped = existing != null
        val staging = File(context.filesDir, "contents/.import-${UUID.randomUUID()}")
        val destination = directoryFor(id)
        val previous = destination.takeIf { it.exists() }?.let { dir ->
            File(context.filesDir, "contents/.backup-$id-${System.currentTimeMillis()}").also { backup ->
                dir.copyRecursively(backup, overwrite = true)
            }
        }
        try {
            if (staging.exists()) staging.deleteRecursively()
            staging.mkdirs()
            File(staging, AUDIO_FILE).writeBytes(bundle.audio)
            val content = LsbArchive.toContent(bundle, id, relativeAudioPath(id))
            File(staging, PACKAGE_FILE).writeText(ContentPackageMapper.encode(content))
            if (destination.exists()) destination.deleteRecursively()
            check(staging.renameTo(destination) || staging.copyRecursively(destination, overwrite = true)) {
                "Não foi possível gravar o exercício importado."
            }
            if (staging.exists() && staging.canonicalFile != destination.canonicalFile) {
                staging.deleteRecursively()
            }
            dao.upsert(ContentEntity.from(content))
            previous?.deleteRecursively()
            LsbImportResult(content, idRemapped = remapped)
        } catch (error: Exception) {
            staging.deleteRecursively()
            if (previous != null) {
                destination.deleteRecursively()
                previous.renameTo(destination)
            }
            if (error is LsbException) throw error
            throw LsbException(error.message ?: "A importação falhou e os conteúdos existentes foram preservados.", error)
        }
    }

    override suspend fun delete(id: String) {
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
