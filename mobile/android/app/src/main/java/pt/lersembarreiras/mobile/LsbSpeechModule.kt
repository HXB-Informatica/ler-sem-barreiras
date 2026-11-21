package pt.lersembarreiras.mobile

import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import android.util.Log
import com.facebook.react.bridge.ReactMethod
import java.io.File
import kotlin.concurrent.thread

class LsbSpeechModule(private val ctx: ReactApplicationContext) : ReactContextBaseJavaModule(ctx) {
    @Volatile
    private var engine: EbranchCtc? = null

    override fun getName(): String = "LsbSpeech"

    @ReactMethod
    fun isAvailable(promise: Promise) {
        val external = File(ctx.getExternalFilesDir(null), "ctc/model.onnx")
        val internal = File(ctx.filesDir, "ctc/model.onnx")
        val present = external.length() > 1_000_000 || internal.length() > 1_000_000
        val asset = runCatching {
            ctx.assets.open("ctc/model.onnx").close()
            ctx.assets.open("ctc/model.int8.onnx.data").close()
        }.isSuccess
        Log.i("LsbCtc", "external=${external.length()} internal=${internal.length()} asset=$asset")
        promise.resolve(present || asset)
    }

    @Suppress("UNUSED_PARAMETER")
    @ReactMethod
    fun transcribe(path: String, prompt: String, promise: Promise) {
        thread(name = "lsb-ctc") {
            try {
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
                val heard = ensureLoaded().recognize(samples)
                if (heard.isEmpty()) {
                    promise.reject("SPEECH", "O motor local não reconheceu palavras nesta narração.")
                    return@thread
                }
                val words = Arguments.createArray()
                for (item in heard) {
                    val word = Arguments.createMap()
                    word.putString("text", item.text)
                    word.putDouble("startMs", item.startMs.toDouble())
                    word.putDouble("endMs", item.endMs.toDouble())
                    word.putDouble("probability", item.probability.toDouble())
                    words.pushMap(word)
                }
                val result = Arguments.createMap()
                result.putArray("words", words)
                result.putString("language", "pt")
                result.putBoolean("usedTokenTimestamps", true)
                promise.resolve(result)
            } catch (error: Exception) {
                promise.reject("SPEECH", error.message ?: "Falha no reconhecimento de voz.", error)
            }
        }
    }

    private fun ensureLoaded(): EbranchCtc {
        engine?.let { return it }
        synchronized(this) {
            engine?.let { return it }
            val installed = EbranchCtc.install(
                ctx.assets,
                listOf(File(ctx.getExternalFilesDir(null), "ctc"), File(ctx.filesDir, "ctc")),
            )
            val created = EbranchCtc(installed.first, installed.second, installed.third)
            engine = created
            return created
        }
    }

    private fun resolveAudio(path: String): File {
        val file = File(path)
        if (file.isAbsolute) return file
        return PathSafety.resolve(File(ctx.filesDir, "lsb"), path)
    }
}
