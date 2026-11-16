package pt.lersembarreiras.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class SyncStatus {
    NONE,
    IN_PROGRESS,
    COMPLETED,
    COMPLETED_WITH_GAPS,
    FAILED,
    CANCELLED,
}

@Serializable
enum class PresentationMode {
    FULL_TEXT,
    SINGLE_WORD,
    PROGRESSIVE,
}

@Serializable
data class WordTiming(
    val text: String,
    val startChar: Int,
    val endChar: Int,
    val startMs: Long? = null,
    val endMs: Long? = null,
    val confidence: Float = 0f,
    val manuallyAdjusted: Boolean = false,
    val needsReview: Boolean = false,
) {
    val isTimed: Boolean get() = startMs != null && endMs != null && endMs >= startMs
}

@Serializable
data class ReadingContent(
    val id: String,
    val title: String,
    val text: String,
    val audioRelativePath: String? = null,
    val durationMs: Long = 0,
    val words: List<WordTiming> = emptyList(),
    val syncStatus: SyncStatus = SyncStatus.NONE,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
) {
    val reviewCount: Int get() = words.count { it.needsReview || !it.isTimed }
}

@Serializable
data class ContentPackage(
    val format: String = FORMAT_ID,
    val version: Int = FORMAT_VERSION,
    val id: String,
    val title: String,
    val text: String,
    val audioFile: String? = null,
    val durationMs: Long = 0,
    val words: List<WordTiming> = emptyList(),
    val syncStatus: SyncStatus = SyncStatus.NONE,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
) {
    companion object {
        const val FORMAT_ID = "lersembarreiras-content"
        const val FORMAT_VERSION = 1
    }
}

data class TextToken(
    val original: String,
    val normalized: String,
    val startChar: Int,
    val endChar: Int,
)

data class RecognizedWord(
    val text: String,
    val startMs: Long,
    val endMs: Long,
    val probability: Float,
)

data class AlignmentResult(
    val words: List<WordTiming>,
    val alignedCount: Int,
    val reviewCount: Int,
    val unusedRecognizedCount: Int,
) {
    val status: SyncStatus
        get() = when {
            words.isEmpty() -> SyncStatus.FAILED
            alignedCount == 0 -> SyncStatus.FAILED
            reviewCount > 0 -> SyncStatus.COMPLETED_WITH_GAPS
            else -> SyncStatus.COMPLETED
        }
}

data class ReaderSettings(
    val presentationMode: PresentationMode = PresentationMode.FULL_TEXT,
    val fontScale: Float = 1.4f,
    val highContrast: Boolean = false,
)
