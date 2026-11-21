package pt.lersembarreiras.lab

import java.io.File
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import pt.lersembarreiras.alignment.DefaultAlignmentEngine
import pt.lersembarreiras.core.model.RecognizedWord
import pt.lersembarreiras.core.model.Transcription
import pt.lersembarreiras.shared.AlignmentRequest

/**
 * Interface de linha de comandos do alinhador da aplicação.
 * Destina-se ao laboratório interno; a app dos alunos não depende deste módulo.
 *
 * Uso: lab-cli <pedidos.jsonl> <resultados.jsonl>
 */
fun main(args: Array<String>) {
    require(args.size >= 2) { "Uso: lab-cli <pedidos.jsonl> <resultados.jsonl>" }
    val input = File(args[0])
    val output = File(args[1])
    require(input.isFile) { "Ficheiro de pedidos não encontrado: ${input.path}" }
    output.parentFile?.mkdirs()
    val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    val engine = DefaultAlignmentEngine()
    output.bufferedWriter().use { writer ->
        input.bufferedReader().useLines { lines ->
            lines.forEach { line ->
                if (line.isBlank()) return@forEach
                val request = json.decodeFromString(LabAlignRequest.serializer(), line)
                val started = System.nanoTime()
                val result = engine.align(
                    AlignmentRequest(
                        sourceText = request.text,
                        transcription = Transcription(
                            words = request.recognized.map {
                                RecognizedWord(it.text, it.startMs, it.endMs, it.probability)
                            },
                        ),
                        audioDurationMs = request.audioDurationMs,
                    ),
                )
                val elapsedMs = (System.nanoTime() - started) / 1_000_000.0
                val response = LabAlignResponse(
                    id = request.id,
                    alignedCount = result.alignedCount,
                    reviewCount = result.reviewCount,
                    unusedRecognizedCount = result.unusedRecognizedCount,
                    elapsedMs = elapsedMs,
                    words = result.words.map {
                        LabWord(
                            text = it.text,
                            index = it.index,
                            id = it.id,
                            startMs = it.startMs,
                            endMs = it.endMs,
                            state = it.resolvedState().name,
                            needsReview = it.requiresReview(),
                        )
                    },
                    issues = result.intervalIssues.map {
                        LabIssue(it.wordIndex, it.wordId, it.type.name, it.message)
                    },
                )
                writer.appendLine(json.encodeToString(LabAlignResponse.serializer(), response))
            }
        }
    }
}

@Serializable
data class LabAlignRequest(
    val id: String,
    val text: String,
    val audioDurationMs: Long,
    val recognized: List<LabRecognizedWord> = emptyList(),
)

@Serializable
data class LabRecognizedWord(
    val text: String,
    val startMs: Long,
    val endMs: Long,
    val probability: Float = 1f,
)

@Serializable
data class LabAlignResponse(
    val id: String,
    val alignedCount: Int,
    val reviewCount: Int,
    val unusedRecognizedCount: Int,
    val elapsedMs: Double,
    val words: List<LabWord>,
    val issues: List<LabIssue> = emptyList(),
)

@Serializable
data class LabWord(
    val text: String,
    val index: Int,
    val id: String,
    val startMs: Long? = null,
    val endMs: Long? = null,
    val state: String,
    val needsReview: Boolean,
)

@Serializable
data class LabIssue(
    val wordIndex: Int,
    val wordId: String,
    val type: String,
    val message: String,
)
