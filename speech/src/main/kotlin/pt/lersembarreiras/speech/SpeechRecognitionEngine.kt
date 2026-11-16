package pt.lersembarreiras.speech

import pt.lersembarreiras.core.model.RecognizedWord

interface SpeechRecognitionEngine {
    val isReady: Boolean
    val modelName: String
    suspend fun ensureLoaded(onProgress: (Float) -> Unit = {})
    suspend fun transcribe(
        pcm16kMono: ShortArray,
        language: String = "pt",
        prompt: String? = null,
        onProgress: (Float) -> Unit = {},
        isCancelled: () -> Boolean = { false },
    ): TranscriptionResult
    fun cancel()
    fun release()
}

data class TranscriptionResult(
    val words: List<RecognizedWord>,
    val cancelled: Boolean = false,
    val error: String? = null,
    val usedTokenTimestamps: Boolean = false,
) {
    val isSuccess: Boolean get() = error == null && !cancelled && words.isNotEmpty()
}

class SpeechEngineUnavailable(message: String) : IllegalStateException(message)
