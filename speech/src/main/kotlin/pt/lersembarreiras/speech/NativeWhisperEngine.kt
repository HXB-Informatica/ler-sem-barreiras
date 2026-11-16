package pt.lersembarreiras.speech

import android.content.Context
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import pt.lersembarreiras.core.model.RecognizedWord

class NativeWhisperEngine(
    private val context: Context,
    override val modelName: String = DEFAULT_MODEL,
) : SpeechRecognitionEngine {
    private var handle: Long = 0L

    override val isReady: Boolean get() = handle != 0L

    override suspend fun ensureLoaded(onProgress: (Float) -> Unit) = withContext(Dispatchers.IO) {
        if (handle != 0L) {
            onProgress(1f)
            return@withContext
        }
        if (!WhisperJni.available) {
            throw SpeechEngineUnavailable(
                "A biblioteca nativa whisper.cpp não foi carregada. Compile a aplicação com NDK.",
            )
        }
        onProgress(0.05f)
        val modelFile = copyModelIfNeeded()
        onProgress(0.2f)
        val threads = Runtime.getRuntime().availableProcessors().coerceIn(2, 6)
        handle = WhisperJni.nativeInit(modelFile.absolutePath, threads)
        if (handle == 0L) {
            throw SpeechEngineUnavailable("Não foi possível carregar o modelo $modelName.")
        }
        onProgress(1f)
    }

    override suspend fun transcribe(
        pcm16kMono: ShortArray,
        language: String,
        prompt: String?,
        onProgress: (Float) -> Unit,
        isCancelled: () -> Boolean,
    ): TranscriptionResult = withContext(Dispatchers.Default) {
        ensureLoaded(onProgress)
        if (pcm16kMono.isEmpty()) {
            return@withContext TranscriptionResult(emptyList(), error = "Áudio vazio.")
        }
        val samples = FloatArray(pcm16kMono.size) { pcm16kMono[it] / 32768.0f }
        val json = coroutineScope {
            val job = async(Dispatchers.IO) {
                WhisperJni.nativeTranscribe(handle, samples, language, prompt)
            }
            while (!job.isCompleted) {
                if (isCancelled()) {
                    WhisperJni.nativeCancel(handle)
                }
                onProgress((WhisperJni.nativeProgress(handle) / 100f).coerceIn(0f, 0.99f))
                delay(200)
            }
            if (isCancelled()) {
                WhisperJni.nativeCancel(handle)
                job.cancel()
                return@coroutineScope null
            }
            job.await()
        } ?: return@withContext TranscriptionResult(emptyList(), cancelled = true)
        onProgress(1f)
        parseResult(json)
    }

    override fun cancel() {
        if (handle != 0L && WhisperJni.available) {
            WhisperJni.nativeCancel(handle)
        }
    }

    override fun release() {
        if (handle != 0L && WhisperJni.available) {
            WhisperJni.nativeFree(handle)
            handle = 0L
        }
    }

    private fun copyModelIfNeeded(): File {
        val outDir = File(context.filesDir, "models")
        outDir.mkdirs()
        val outFile = File(outDir, modelName)
        if (outFile.exists() && outFile.length() > 1_000_000) {
            return outFile
        }
        val assetPath = "models/$modelName"
        context.assets.open(assetPath).use { input ->
            outFile.outputStream().use { output -> input.copyTo(output) }
        }
        return outFile
    }

    private fun parseResult(raw: String): TranscriptionResult {
        val json = JSONObject(raw)
        if (json.optBoolean("cancelled")) {
            return TranscriptionResult(emptyList(), cancelled = true)
        }
        val error = json.optString("error").ifBlank { null }
        val array = json.optJSONArray("words")
        val words = mutableListOf<RecognizedWord>()
        var usedTokenTimestamps = false
        if (array != null) {
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                if (item.optBoolean("tokenTimestamps")) usedTokenTimestamps = true
                words += RecognizedWord(
                    text = item.getString("text"),
                    startMs = item.getLong("startMs"),
                    endMs = item.getLong("endMs"),
                    probability = item.optDouble("probability", 0.0).toFloat(),
                )
            }
        }
        return TranscriptionResult(
            words = words,
            error = error,
            usedTokenTimestamps = usedTokenTimestamps,
        )
    }

    companion object {
        const val DEFAULT_MODEL = "ggml-tiny.bin"
    }
}
