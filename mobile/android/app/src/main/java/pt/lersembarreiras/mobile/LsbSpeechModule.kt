package pt.lersembarreiras.mobile

import android.app.ActivityManager
import android.content.ComponentCallbacks2
import android.content.res.Configuration
import android.os.PowerManager
import android.os.SystemClock
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import android.util.Log
import com.facebook.react.bridge.ReactMethod
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

class LsbSpeechModule(private val ctx: ReactApplicationContext) : ReactContextBaseJavaModule(ctx), ComponentCallbacks2 {
    @Volatile
    private var engine: EbranchCtc? = null
    private val transcribeLock = Any()
    private val running = AtomicBoolean(false)

    override fun getName(): String = "LsbSpeech"

    override fun initialize() {
        super.initialize()
        ctx.registerComponentCallbacks(this)
        LsbDiag.mark("speech_module_init", mapOf("memMb" to LsbDiag.memoryMb(ctx), "heapMb" to LsbDiag.javaHeapMb()))
    }

    override fun invalidate() {
        ctx.unregisterComponentCallbacks(this)
        releaseEngine("invalidate")
        super.invalidate()
    }

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

    @ReactMethod
    fun warmup(promise: Promise) {
        thread(name = "lsb-ctc-warm") {
            try {
                if (!hasEnoughRam()) {
                    LsbDiag.mark("ctc_warmup_skipped", mapOf("memMb" to LsbDiag.memoryMb(ctx)))
                    promise.resolve(false)
                    return@thread
                }
                val started = SystemClock.elapsedRealtime()
                ensureLoaded()
                LsbDiag.mark(
                    "ctc_warmup",
                    mapOf(
                        "ms" to LsbDiag.elapsedSince(started),
                        "memMb" to LsbDiag.memoryMb(ctx),
                        "pssMb" to LsbDiag.pssMb(),
                    ),
                )
                LsbDiag.persist(ctx)
                promise.resolve(true)
            } catch (error: Exception) {
                LsbDiag.mark("ctc_warmup_error", mapOf("kind" to error.javaClass.simpleName))
                promise.reject("SPEECH", error.message ?: "Não foi possível preparar o motor.", error)
            }
        }
    }

    @Suppress("UNUSED_PARAMETER")
    @ReactMethod
    fun transcribe(path: String, prompt: String, promise: Promise) {
        if (!running.compareAndSet(false, true)) {
            promise.reject("SPEECH_BUSY", "Já há uma sincronização a decorrer neste tablet.")
            return
        }
        thread(name = "lsb-ctc") {
            val wake = acquireWakeLock()
            val started = SystemClock.elapsedRealtime()
            try {
                val file = resolveAudio(path)
                if (!file.isFile) {
                    promise.reject("SPEECH", "Não há narração gravada neste conteúdo.")
                    return@thread
                }
                val samples = try {
                    WavPcm.readMonoFloats16k(file)
                } catch (error: Exception) {
                    LsbDiag.mark("audio_read_error", mapOf("kind" to error.javaClass.simpleName))
                    promise.reject("SPEECH", "O ficheiro de áudio não pôde ser lido. Grave ou importe de novo.")
                    return@thread
                }
                if (samples.isEmpty()) {
                    promise.reject("SPEECH", "O áudio está vazio.")
                    return@thread
                }
                val loadStarted = SystemClock.elapsedRealtime()
                val loaded = ensureLoaded()
                val loadMs = LsbDiag.elapsedSince(loadStarted)
                val inferStarted = SystemClock.elapsedRealtime()
                val heard = synchronized(transcribeLock) { loaded.recognize(samples) }
                val inferMs = LsbDiag.elapsedSince(inferStarted)
                LsbDiag.mark(
                    "ctc_infer",
                    mapOf(
                        "ms" to LsbDiag.elapsedSince(started),
                        "loadMs" to loadMs,
                        "inferMs" to inferMs,
                        "samples" to samples.size,
                        "words" to heard.size,
                        "memMb" to LsbDiag.memoryMb(ctx),
                        "heapMb" to LsbDiag.javaHeapMb(),
                        "pssMb" to LsbDiag.pssMb(),
                    ),
                )
                LsbDiag.persist(ctx)
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
            } catch (error: OutOfMemoryError) {
                releaseEngine("oom")
                LsbDiag.mark("ctc_oom", mapOf("memMb" to LsbDiag.memoryMb(ctx), "pssMb" to LsbDiag.pssMb()))
                LsbDiag.persist(ctx)
                promise.reject(
                    "SPEECH_OOM",
                    "Não há memória suficiente neste momento para sincronizar. Feche outras aplicações e tente de novo.",
                    error,
                )
            } catch (error: Exception) {
                LsbDiag.mark("ctc_error", mapOf("kind" to error.javaClass.simpleName))
                LsbDiag.persist(ctx)
                promise.reject("SPEECH", error.message ?: "Falha no reconhecimento de voz.", error)
            } finally {
                wake?.release()
                running.set(false)
            }
        }
    }

    private fun ensureLoaded(): EbranchCtc {
        engine?.let { return it }
        synchronized(this) {
            engine?.let { return it }
            val started = SystemClock.elapsedRealtime()
            val installed = EbranchCtc.install(
                ctx.assets,
                listOf(File(ctx.getExternalFilesDir(null), "ctc"), File(ctx.filesDir, "ctc")),
            )
            val created = EbranchCtc(installed.first, installed.second, installed.third)
            engine = created
            LsbDiag.mark(
                "ctc_load",
                mapOf(
                    "ms" to LsbDiag.elapsedSince(started),
                    "memMb" to LsbDiag.memoryMb(ctx),
                    "pssMb" to LsbDiag.pssMb(),
                    "heapMb" to LsbDiag.javaHeapMb(),
                ),
            )
            return created
        }
    }

    private fun releaseEngine(reason: String) {
        synchronized(this) {
            engine?.close()
            engine = null
            LsbDiag.mark("ctc_release", mapOf("reason" to reason, "memMb" to LsbDiag.memoryMb(ctx)))
        }
    }

    private fun hasEnoughRam(): Boolean {
        val info = ActivityManager.MemoryInfo()
        ctx.getSystemService(ActivityManager::class.java).getMemoryInfo(info)
        return info.availMem > 1_200L * 1024 * 1024 && !info.lowMemory
    }

    private fun acquireWakeLock(): PowerManager.WakeLock? {
        return runCatching {
            val manager = ctx.getSystemService(PowerManager::class.java)
            manager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "lersembarreiras:ctc").apply {
                setReferenceCounted(false)
                acquire(15 * 60 * 1000L)
            }
        }.getOrNull()
    }

    private fun resolveAudio(path: String): File {
        val file = File(path)
        if (file.isAbsolute) return file
        return PathSafety.resolve(File(ctx.filesDir, "lsb"), path)
    }

    override fun onConfigurationChanged(newConfig: Configuration) = Unit

    override fun onLowMemory() {
        if (!running.get()) {
            releaseEngine("low_memory")
        }
    }

    override fun onTrimMemory(level: Int) {
        if (!running.get() && level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL) {
            releaseEngine("trim_$level")
        }
    }
}
