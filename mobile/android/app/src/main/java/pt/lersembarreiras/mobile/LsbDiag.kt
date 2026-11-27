package pt.lersembarreiras.mobile

import android.app.ActivityManager
import android.content.Context
import android.os.Process
import android.os.SystemClock
import android.util.Log
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Diagnóstico local, sem rede e sem dados pedagógicos.
 * Não regista texto, áudio nem identificadores de conteúdos.
 */
internal object LsbDiag {
    private const val TAG = "LsbPerf"
    private const val MAX_EVENTS = 48
    private val events = ConcurrentLinkedQueue<String>()

    fun mark(name: String, extra: Map<String, Any?> = emptyMap()) {
        val payload = JSONObject()
        payload.put("t", System.currentTimeMillis())
        payload.put("name", name)
        extra.forEach { (key, value) ->
            if (value != null) payload.put(key, value)
        }
        val line = payload.toString()
        Log.i(TAG, line)
        events.add(line)
        while (events.size > MAX_EVENTS) {
            events.poll()
        }
    }

    fun memoryMb(context: Context): Int {
        val info = ActivityManager.MemoryInfo()
        context.getSystemService(ActivityManager::class.java).getMemoryInfo(info)
        return (info.availMem / (1024 * 1024)).toInt()
    }

    fun javaHeapMb(): Int =
        ((Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / (1024 * 1024)).toInt()

    fun pssMb(): Int {
        val info = android.os.Debug.MemoryInfo()
        android.os.Debug.getMemoryInfo(info)
        return info.totalPss / 1024
    }

    fun elapsedSince(startElapsed: Long): Long = SystemClock.elapsedRealtime() - startElapsed

    fun persist(context: Context) {
        try {
            val file = File(File(context.filesDir, "lsb"), "diag.jsonl")
            file.parentFile?.mkdirs()
            file.writeText(events.joinToString("\n", postfix = "\n"))
        } catch (_: Exception) {
            // Diagnóstico nunca deve falhar a app.
        }
    }

    fun pid(): Int = Process.myPid()
}
