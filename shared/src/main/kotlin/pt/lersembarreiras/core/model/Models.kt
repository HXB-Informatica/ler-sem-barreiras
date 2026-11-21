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
enum class WordState {
    AUTO,
    REVIEW,
    MANUAL,
    VERIFIED,
}

@Serializable
enum class TimestampOrigin {
    NONE,
    ASR,
    ALIGNED,
    MANUAL,
    IMPORTED,
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
    val id: String = "",
    val index: Int = -1,
    val state: WordState? = null,
    val timestampOrigin: TimestampOrigin? = null,
) {
    val isTimed: Boolean get() = startMs != null && endMs != null && endMs >= startMs

    fun resolvedState(): WordState = state ?: when {
        manuallyAdjusted -> WordState.MANUAL
        needsReview || !isTimed -> WordState.REVIEW
        else -> WordState.AUTO
    }

    fun resolvedOrigin(): TimestampOrigin = timestampOrigin ?: when {
        manuallyAdjusted -> TimestampOrigin.MANUAL
        isTimed -> TimestampOrigin.ASR
        else -> TimestampOrigin.NONE
    }

    fun isProtectedFromResync(): Boolean {
        val current = resolvedState()
        return current == WordState.MANUAL || current == WordState.VERIFIED
    }

    fun requiresReview(): Boolean {
        val current = resolvedState()
        if (current == WordState.MANUAL || current == WordState.VERIFIED) return false
        return current == WordState.REVIEW || !isTimed
    }
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
    val reviewCount: Int get() = words.count { it.requiresReview() }
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

data class Transcription(
    val words: List<RecognizedWord>,
    val language: String = "pt",
    val usedTokenTimestamps: Boolean = false,
)

data class AlignmentResult(
    val words: List<WordTiming>,
    val alignedCount: Int,
    val reviewCount: Int,
    val unusedRecognizedCount: Int,
    val intervalIssues: List<IntervalIssue> = emptyList(),
) {
    val status: SyncStatus
        get() = when {
            words.isEmpty() -> SyncStatus.FAILED
            alignedCount == 0 -> SyncStatus.FAILED
            reviewCount > 0 -> SyncStatus.COMPLETED_WITH_GAPS
            else -> SyncStatus.COMPLETED
        }
}

data class IntervalIssue(
    val wordIndex: Int,
    val wordId: String,
    val type: IntervalIssueType,
    val message: String,
)

enum class IntervalIssueType {
    MISSING_TIME,
    INVALID_ORDER,
    OVERLAP,
    TOO_SHORT,
    ZERO_LENGTH,
    OUTSIDE_AUDIO,
}

data class ReaderSettings(
    val presentationMode: PresentationMode = PresentationMode.FULL_TEXT,
    val fontScale: Float = 1.4f,
    val highContrast: Boolean = false,
)

data class PlaybackSnapshot(
    val positionMs: Long,
    val durationMs: Long,
    val playing: Boolean,
    val ended: Boolean,
)

data class Pcm16kAudio(
    val samples: ShortArray,
    val sampleRate: Int = 16_000,
    val durationMs: Long,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Pcm16kAudio) return false
        return sampleRate == other.sampleRate &&
            durationMs == other.durationMs &&
            samples.contentEquals(other.samples)
    }

    override fun hashCode(): Int {
        var result = samples.contentHashCode()
        result = 31 * result + sampleRate
        result = 31 * result + durationMs.hashCode()
        return result
    }
}
