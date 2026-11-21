package pt.lersembarreiras.studio.sync

import java.io.File
import pt.lersembarreiras.core.model.Transcription

data class TranscriptionOutcome(
    val transcription: Transcription = Transcription(emptyList()),
    val error: String? = null,
    val cancelled: Boolean = false,
    val engineAvailable: Boolean = true,
) {
    val failed: Boolean get() = error != null && !cancelled
}

fun interface LocalTranscriber {
    fun transcribe(
        wav16kMono: File,
        language: String,
        prompt: String?,
        isCancelled: () -> Boolean,
    ): TranscriptionOutcome
}
