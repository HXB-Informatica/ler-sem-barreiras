package pt.lersembarreiras.speech

class UnavailableSpeechEngine : SpeechRecognitionEngine {
    override val isReady: Boolean = false
    override val modelName: String = "indisponível"

    override suspend fun ensureLoaded(onProgress: (Float) -> Unit) = Unit

    override suspend fun transcribe(
        pcm16kMono: ShortArray,
        language: String,
        prompt: String?,
        onProgress: (Float) -> Unit,
        isCancelled: () -> Boolean,
    ): TranscriptionResult = TranscriptionResult(
        words = emptyList(),
        error = "O reconhecimento local não está incluído nesta aplicação.",
    )

    override fun cancel() = Unit

    override fun release() = Unit
}
