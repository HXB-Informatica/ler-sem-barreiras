package pt.lersembarreiras.studio.project

import java.io.File
import java.util.UUID
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import pt.lersembarreiras.core.model.ContentPackage
import pt.lersembarreiras.core.model.ReadingContent
import pt.lersembarreiras.core.model.SyncStatus
import pt.lersembarreiras.core.packageformat.ContentPackageMapper

@Serializable
data class CaptureInfo(
    val deviceName: String,
    val sampleRate: Int,
    val bitsPerSample: Int,
    val channels: Int,
    val encoding: String = "PCM",
    val fallbackReason: String? = null,
    val clipped: Boolean = false,
)

@Serializable
data class StudioProjectFile(
    val format: String = FORMAT_ID,
    val version: Int = FORMAT_VERSION,
    val content: ContentPackage,
    val originalAudioFile: String? = ORIGINAL_AUDIO,
    val recognitionAudioFile: String? = RECOGNITION_AUDIO,
    val capture: CaptureInfo? = null,
    val recoveredAfterCrash: Boolean = false,
) {
    companion object {
        const val FORMAT_ID = "lersembarreiras-studio"
        const val FORMAT_VERSION = 1
        const val STUDIO_FILE = "studio.json"
        const val CONTENT_FILE = "content.json"
        const val ORIGINAL_AUDIO = "audio-original.wav"
        const val RECOGNITION_AUDIO = "audio-16k.wav"
        const val AUTOSAVE_FILE = "studio.autosave.json"
    }
}

class StudioProjectStore {
    fun create(directory: File, title: String = "Novo conteúdo"): StudioProjectFile {
        directory.mkdirs()
        val now = System.currentTimeMillis()
        val content = ReadingContent(
            id = UUID.randomUUID().toString(),
            title = title.ifBlank { "Novo conteúdo" },
            text = "",
            createdAtEpochMs = now,
            updatedAtEpochMs = now,
        )
        val project = StudioProjectFile(content = ContentPackageMapper.toPackage(content))
        write(directory, project)
        return project
    }

    fun load(directory: File): StudioProjectFile {
        val autosave = File(directory, StudioProjectFile.AUTOSAVE_FILE)
        val main = File(directory, StudioProjectFile.STUDIO_FILE)
        val source = when {
            autosave.exists() && (!main.exists() || autosave.lastModified() > main.lastModified()) -> autosave
            main.exists() -> main
            else -> error("Não foi encontrado um projecto Ler sem Barreiras nesta pasta.")
        }
        val recovered = source == autosave
        val project = ContentPackageMapper.json.decodeFromString<StudioProjectFile>(source.readText())
        return project.copy(recoveredAfterCrash = recovered)
    }

    fun save(directory: File, project: StudioProjectFile) {
        write(directory, project)
        File(directory, StudioProjectFile.AUTOSAVE_FILE).delete()
    }

    fun autosave(directory: File, project: StudioProjectFile) {
        directory.mkdirs()
        val tmp = File(directory, StudioProjectFile.AUTOSAVE_FILE + ".tmp")
        tmp.writeText(ContentPackageMapper.json.encodeToString(project))
        val dest = File(directory, StudioProjectFile.AUTOSAVE_FILE)
        if (dest.exists()) dest.delete()
        tmp.renameTo(dest)
    }

    fun originalWav(directory: File): File = File(directory, StudioProjectFile.ORIGINAL_AUDIO)

    fun recognitionWav(directory: File): File = File(directory, StudioProjectFile.RECOGNITION_AUDIO)

    private fun write(directory: File, project: StudioProjectFile) {
        directory.mkdirs()
        val json = ContentPackageMapper.json
        val studioTmp = File(directory, StudioProjectFile.STUDIO_FILE + ".tmp")
        studioTmp.writeText(json.encodeToString(project))
        val studio = File(directory, StudioProjectFile.STUDIO_FILE)
        if (studio.exists()) studio.delete()
        studioTmp.renameTo(studio)
        val content = ContentPackageMapper.fromPackage(
            project.content,
            project.originalAudioFile,
        ).copy(syncStatus = project.content.syncStatus)
        File(directory, StudioProjectFile.CONTENT_FILE).writeText(ContentPackageMapper.encode(content))
    }
}
