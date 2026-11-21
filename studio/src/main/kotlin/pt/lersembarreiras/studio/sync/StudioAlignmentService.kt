package pt.lersembarreiras.studio.sync

import java.io.File
import pt.lersembarreiras.alignment.DefaultAlignmentEngine
import pt.lersembarreiras.core.model.AlignmentResult
import pt.lersembarreiras.core.model.Transcription
import pt.lersembarreiras.core.model.WordTiming
import pt.lersembarreiras.shared.AlignmentEngine
import pt.lersembarreiras.shared.AlignmentRequest

data class StudioSyncResult(
    val alignment: AlignmentResult,
    val transcription: Transcription,
    val engineMessage: String? = null,
    val engineFailed: Boolean = false,
    val cancelled: Boolean = false,
)

class StudioAlignmentService(
    private val transcriber: LocalTranscriber,
    private val aligner: AlignmentEngine = DefaultAlignmentEngine(),
) {
    fun synchronize(
        sourceText: String,
        wav16kMono: File?,
        audioDurationMs: Long,
        previousWords: List<WordTiming> = emptyList(),
        isCancelled: () -> Boolean = { false },
    ): StudioSyncResult {
        val asr = if (wav16kMono != null && wav16kMono.isFile) {
            transcriber.transcribe(wav16kMono, "pt", sourceText.take(400), isCancelled)
        } else {
            TranscriptionOutcome(error = "Grave ou importe a narração antes de sincronizar.", engineAvailable = false)
        }
        if (asr.cancelled) {
            return StudioSyncResult(
                alignment = aligner.align(emptyRequest(sourceText, audioDurationMs, previousWords)),
                transcription = Transcription(emptyList()),
                cancelled = true,
                engineMessage = "Sincronização cancelada.",
            )
        }
        val transcription = asr.transcription
        val alignment = aligner.align(
            AlignmentRequest(
                sourceText = sourceText,
                transcription = transcription,
                audioDurationMs = audioDurationMs,
                previousWords = previousWords,
            ),
        )
        val message = when {
            asr.engineAvailable.not() || asr.error != null ->
                (asr.error ?: "O reconhecimento local não está disponível.") +
                    " O alinhamento criou as palavras do texto para correção manual."
            transcription.words.isEmpty() ->
                "O motor local não reconheceu fala. As palavras ficaram por alinhar."
            else -> null
        }
        return StudioSyncResult(
            alignment = alignment,
            transcription = transcription,
            engineMessage = message,
            engineFailed = asr.failed || asr.engineAvailable.not(),
        )
    }

    private fun emptyRequest(text: String, durationMs: Long, previous: List<WordTiming>) = AlignmentRequest(
        sourceText = text,
        transcription = Transcription(emptyList()),
        audioDurationMs = durationMs,
        previousWords = previous,
    )
}
