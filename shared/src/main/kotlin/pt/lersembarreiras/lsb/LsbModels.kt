package pt.lersembarreiras.lsb

import kotlinx.serialization.Serializable
import pt.lersembarreiras.core.model.SyncStatus
import pt.lersembarreiras.core.model.WordTiming

@Serializable
data class LsbManifest(
    val format: String = LsbArchive.FORMAT_ID,
    val schemaVersion: Int = LsbArchive.SCHEMA_VERSION,
    val contentId: String,
    val files: Map<String, String>,
    val createdAtEpochMs: Long,
)

@Serializable
data class LsbMetadata(
    val title: String,
    val durationMs: Long,
    val syncStatus: SyncStatus,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
    val originalContentId: String,
    val source: String,
    val audioFile: String = LsbArchive.AUDIO_PATH,
    val textFile: String = LsbArchive.TEXT_PATH,
    val wordsFile: String = LsbArchive.WORDS_PATH,
)

data class LsbBundle(
    val manifest: LsbManifest,
    val metadata: LsbMetadata,
    val text: String,
    val words: List<WordTiming>,
    val audio: ByteArray,
) {
    val contentId: String get() = manifest.contentId
}

class LsbException(message: String, cause: Throwable? = null) : IllegalArgumentException(message, cause)

data class LsbImportResult(
    val content: pt.lersembarreiras.core.model.ReadingContent,
    val duplicate: Boolean = false,
    val idRemapped: Boolean = false,
)
