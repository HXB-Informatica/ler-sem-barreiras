package pt.lersembarreiras.studio.sync

import java.io.File
import java.util.concurrent.TimeUnit
import pt.lersembarreiras.core.model.Transcription

/**
 * Invoca o whisper-cli nativo Windows como processo local.
 * Não usa JNI Android nem rede em tempo de execução.
 */
class WhisperCliEngine(
    private val installProvider: () -> WhisperInstall? = { WhisperRuntime.locate() },
) : LocalTranscriber {
    @Volatile private var process: Process? = null

    override fun transcribe(
        wav16kMono: File,
        language: String,
        prompt: String?,
        isCancelled: () -> Boolean,
    ): TranscriptionOutcome {
        val install = installProvider()
            ?: return TranscriptionOutcome(
                error = WhisperRuntime.status(),
                engineAvailable = false,
            )
        if (!wav16kMono.isFile) {
            return TranscriptionOutcome(error = "Não existe áudio 16 kHz para reconhecimento.")
        }
        val work = File(wav16kMono.parentFile, "whisper-work").apply { mkdirs() }
        val prefix = File(work, "out")
        val command = mutableListOf(
            install.executable.absolutePath,
            "-m", install.model.absolutePath,
            "-f", wav16kMono.absolutePath,
            "-l", language,
            "-nt",
            "-ml", "60",
            "-sow",
            "-oj",
            "-of", prefix.absolutePath,
        )
        if (!prompt.isNullOrBlank()) {
            command += listOf("--prompt", prompt.take(400))
        }
        return try {
            val builder = ProcessBuilder(command)
                .directory(install.executable.parentFile)
                .redirectErrorStream(true)
            val started = builder.start()
            process = started
            val output = StringBuilder()
            val reader = Thread {
                started.inputStream.bufferedReader().forEachLine { line ->
                    if (output.length < 32_000) output.appendLine(line)
                }
            }
            reader.isDaemon = true
            reader.start()
            while (started.isAlive) {
                if (isCancelled()) {
                    started.destroy()
                    return TranscriptionOutcome(cancelled = true)
                }
                started.waitFor(200, TimeUnit.MILLISECONDS)
            }
            reader.join(2_000)
            val code = started.exitValue()
            val jsonFile = File(work, "out.json").takeIf { it.isFile }
                ?: work.listFiles()?.firstOrNull { it.name.endsWith(".json") }
            if (code != 0 && jsonFile == null) {
                return TranscriptionOutcome(
                    error = "O whisper-cli terminou com código $code. ${output.take(400)}",
                )
            }
            val raw = jsonFile?.readText().orEmpty()
            val words = if (raw.isBlank()) emptyList() else WhisperJsonParser.parse(raw)
            TranscriptionOutcome(
                transcription = Transcription(words = words, language = language, usedTokenTimestamps = true),
            )
        } catch (error: Exception) {
            TranscriptionOutcome(error = error.message ?: "Falha ao executar o whisper-cli Windows.")
        } finally {
            process = null
        }
    }

    fun cancel() {
        process?.destroy()
    }
}
