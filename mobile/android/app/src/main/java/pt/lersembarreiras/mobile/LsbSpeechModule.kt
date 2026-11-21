package pt.lersembarreiras.mobile

import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import org.json.JSONObject
import java.io.File
import kotlin.concurrent.thread

class LsbSpeechModule(private val ctx: ReactApplicationContext) : ReactContextBaseJavaModule(ctx) {
    @Volatile
    private var handle: Long = 0L

    override fun getName(): String = "LsbSpeech"

    @ReactMethod
    fun isAvailable(promise: Promise) {
        promise.resolve(WhisperJni.available)
    }

    @ReactMethod
    fun transcribe(path: String, prompt: String, promise: Promise) {
        thread(name = "lsb-whisper") {
            try {
                if (!WhisperJni.available) {
                    promise.reject(
                        "SPEECH",
                        "A biblioteca nativa whisper.cpp não foi carregada. Compile a aplicação com NDK.",
                    )
                    return@thread
                }
                val file = resolveAudio(path)
                if (!file.isFile) {
                    promise.reject("SPEECH", "Não há narração gravada neste conteúdo.")
                    return@thread
                }
                val samples = WavPcm.readMonoFloats16k(file)
                if (samples.isEmpty()) {
                    promise.reject("SPEECH", "O áudio está vazio.")
                    return@thread
                }
                ensureLoaded()
                val raw = WhisperJni.nativeTranscribe(handle, samples, "pt", prompt.take(800).ifBlank { null })
                val parsed = JSONObject(raw)
                if (parsed.optBoolean("cancelled")) {
                    promise.reject("SPEECH", "A sincronização foi cancelada.")
                    return@thread
                }
                val error = parsed.optString("error")
                if (error.isNotBlank()) {
                    promise.reject("SPEECH", error)
                    return@thread
                }
                val wordsJson = parsed.optJSONArray("words")
                val words = Arguments.createArray()
                var usedTokenTimestamps = false
                if (wordsJson != null) {
                    for (i in 0 until wordsJson.length()) {
                        val item = wordsJson.getJSONObject(i)
                        if (item.optBoolean("tokenTimestamps")) {
                            usedTokenTimestamps = true
                        }
                        val word = Arguments.createMap()
                        word.putString("text", item.getString("text"))
                        word.putDouble("startMs", item.getLong("startMs").toDouble())
                        word.putDouble("endMs", item.getLong("endMs").toDouble())
                        word.putDouble("probability", item.optDouble("probability", 0.0))
                        words.pushMap(word)
                    }
                }
                if (words.size() == 0) {
                    promise.reject("SPEECH", "O motor local não reconheceu palavras nesta narração.")
                    return@thread
                }
                val result = Arguments.createMap()
                result.putArray("words", words)
                result.putString("language", "pt")
                result.putBoolean("usedTokenTimestamps", usedTokenTimestamps)
                promise.resolve(result)
            } catch (error: Exception) {
                promise.reject("SPEECH", error.message ?: "Falha no reconhecimento de voz.", error)
            }
        }
    }

    private fun ensureLoaded() {
        if (handle != 0L) {
            return
        }
        synchronized(this) {
            if (handle != 0L) {
                return
            }
            val model = copyModelIfNeeded()
            val threads = Runtime.getRuntime().availableProcessors().coerceIn(2, 6)
            handle = WhisperJni.nativeInit(model.absolutePath, threads)
            if (handle == 0L) {
                throw IllegalStateException("Não foi possível carregar o modelo Whisper tiny.")
            }
        }
    }

    private fun copyModelIfNeeded(): File {
        val outDir = File(ctx.filesDir, "models")
        outDir.mkdirs()
        val outFile = File(outDir, MODEL)
        if (outFile.exists() && outFile.length() > 1_000_000) {
            return outFile
        }
        ctx.assets.open("models/$MODEL").use { input ->
            outFile.outputStream().use { output -> input.copyTo(output) }
        }
        return outFile
    }

    private fun resolveAudio(path: String): File {
        val file = File(path)
        if (file.isAbsolute) {
            return file
        }
        return PathSafety.resolve(File(ctx.filesDir, "lsb"), path)
    }

    companion object {
        private const val MODEL = "ggml-tiny.bin"
    }
}
