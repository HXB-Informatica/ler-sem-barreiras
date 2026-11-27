package pt.lersembarreiras.mobile

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.util.Base64
import com.facebook.react.bridge.ActivityEventListener
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.bridge.ReadableMap
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class LsbFilesModule(private val ctx: ReactApplicationContext) :
    ReactContextBaseJavaModule(ctx),
    ActivityEventListener {

    private var pickPromise: Promise? = null

    init {
        ctx.addActivityEventListener(this)
    }

    override fun getName(): String = "LsbFiles"

    override fun getConstants(): MutableMap<String, Any> {
        val info = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
        val code = if (Build.VERSION.SDK_INT >= 28) {
            info.longVersionCode.toInt()
        } else {
            @Suppress("DEPRECATION")
            info.versionCode
        }
        return mutableMapOf(
            "versionName" to (info.versionName ?: "0.2.3-experimental"),
            "versionCode" to code,
        )
    }

    private fun root(): File = File(ctx.filesDir, "lsb").apply { mkdirs() }

    @ReactMethod
    fun writeText(path: String, text: String, promise: Promise) {
        try {
            val file = PathSafety.resolve(root(), path)
            file.parentFile?.mkdirs()
            file.writeText(text, Charsets.UTF_8)
            promise.resolve(null)
        } catch (error: Exception) {
            promise.reject("FILES", error.message, error)
        }
    }

    @ReactMethod
    fun readText(path: String, promise: Promise) {
        try {
            val file = PathSafety.resolve(root(), path)
            if (!file.isFile) {
                promise.resolve(null)
            } else {
                promise.resolve(file.readText(Charsets.UTF_8))
            }
        } catch (error: Exception) {
            promise.reject("FILES", error.message, error)
        }
    }

    @ReactMethod
    fun writeBase64(path: String, data: String, promise: Promise) {
        try {
            val file = PathSafety.resolve(root(), path)
            file.parentFile?.mkdirs()
            file.writeBytes(Base64.decode(data, Base64.DEFAULT))
            promise.resolve(null)
        } catch (error: Exception) {
            promise.reject("FILES", error.message, error)
        }
    }

    @ReactMethod
    fun readBase64(path: String, promise: Promise) {
        try {
            val file = PathSafety.resolve(root(), path)
            if (!file.isFile) {
                promise.resolve(null)
            } else {
                promise.resolve(Base64.encodeToString(file.readBytes(), Base64.NO_WRAP))
            }
        } catch (error: Exception) {
            promise.reject("FILES", error.message, error)
        }
    }

    @ReactMethod
    fun exists(path: String, promise: Promise) {
        try {
            promise.resolve(PathSafety.resolve(root(), path).exists())
        } catch (error: Exception) {
            promise.reject("FILES", error.message, error)
        }
    }

    @ReactMethod
    fun deletePath(path: String, promise: Promise) {
        try {
            PathSafety.resolve(root(), path).deleteRecursively()
            promise.resolve(null)
        } catch (error: Exception) {
            promise.reject("FILES", error.message, error)
        }
    }

    @ReactMethod
    fun list(path: String, promise: Promise) {
        try {
            val dir = if (path.isBlank()) root() else PathSafety.resolve(root(), path)
            val names = Arguments.createArray()
            dir.listFiles()?.forEach { names.pushString(it.name) }
            promise.resolve(names)
        } catch (error: Exception) {
            promise.reject("FILES", error.message, error)
        }
    }

    @ReactMethod
    fun absolutePath(path: String, promise: Promise) {
        try {
            promise.resolve(PathSafety.resolve(root(), path).absolutePath)
        } catch (error: Exception) {
            promise.reject("FILES", error.message, error)
        }
    }

    @ReactMethod
    fun pickFile(mime: String, promise: Promise) {
        val activity = ctx.currentActivity
        if (activity == null) {
            promise.reject("FILES", "Não há actividade em primeiro plano.")
            return
        }
        pickPromise = promise
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = if (mime.isBlank()) "*/*" else mime
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf(mime, "application/zip", "application/octet-stream", "*/*"))
        }
        activity.startActivityForResult(intent, PICK_CODE)
    }

    @ReactMethod
    fun unzipWhitelisted(base64: String, promise: Promise) {
        try {
            val allowed = setOf(
                "manifest.json",
                "metadata.json",
                "texto.txt",
                "sync/palavras.json",
                "audio/leitura.wav",
            )
            val input = ByteArrayInputStream(Base64.decode(base64, Base64.DEFAULT))
            val out = Arguments.createMap()
            var total = 0L
            var count = 0
            ZipInputStream(input).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    count++
                    if (count > 16) {
                        throw IllegalArgumentException("O pacote tem demasiadas entradas e foi rejeitado.")
                    }
                    if (!entry.isDirectory) {
                        val name = sanitizeZipName(entry.name)
                        if (name !in allowed) {
                            throw IllegalArgumentException("O pacote contém um ficheiro não permitido: $name")
                        }
                        val bytes = readLimited(zip, entry.compressedSize)
                        total += bytes.size
                        if (total > 100L * 1024 * 1024) {
                            throw IllegalArgumentException("O pacote é demasiado grande e foi rejeitado.")
                        }
                        out.putString(name, Base64.encodeToString(bytes, Base64.NO_WRAP))
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
            promise.resolve(out)
        } catch (error: Exception) {
            promise.reject("LSB", error.message, error)
        }
    }

    @ReactMethod
    fun zipFiles(files: ReadableMap, promise: Promise) {
        try {
            val buffer = ByteArrayOutputStream()
            ZipOutputStream(buffer).use { zip ->
                val keys = files.keySetIterator()
                while (keys.hasNextKey()) {
                    val name = sanitizeZipName(keys.nextKey())
                    val bytes = Base64.decode(files.getString(name), Base64.DEFAULT)
                    zip.putNextEntry(ZipEntry(name))
                    zip.write(bytes)
                    zip.closeEntry()
                }
            }
            promise.resolve(Base64.encodeToString(buffer.toByteArray(), Base64.NO_WRAP))
        } catch (error: Exception) {
            promise.reject("LSB", error.message, error)
        }
    }

    override fun onActivityResult(activity: Activity, requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode != PICK_CODE) {
            return
        }
        val pending = pickPromise ?: return
        pickPromise = null
        if (resultCode != Activity.RESULT_OK || data?.data == null) {
            pending.resolve(null)
            return
        }
        try {
            ctx.contentResolver.openInputStream(data.data!!).use { stream ->
                if (stream == null) {
                    pending.reject("FILES", "Não foi possível ler o ficheiro escolhido.")
                    return
                }
                pending.resolve(Base64.encodeToString(stream.readBytes(), Base64.NO_WRAP))
            }
        } catch (error: Exception) {
            pending.reject("FILES", error.message, error)
        }
    }

    override fun onNewIntent(intent: Intent) = Unit

    private fun sanitizeZipName(raw: String): String {
        val name = raw.replace('\\', '/').trim().trimStart('/')
        if (name.isEmpty() || name.contains("..") || name.startsWith("/") || name.contains(':')) {
            throw IllegalArgumentException("O pacote tenta escrever fora da pasta de destino e foi rejeitado.")
        }
        if (name.split('/').any { it.isEmpty() || it == "." || it == ".." }) {
            throw IllegalArgumentException("O pacote tenta escrever fora da pasta de destino e foi rejeitado.")
        }
        return name
    }

    private fun readLimited(zip: ZipInputStream, compressedSize: Long): ByteArray {
        val buffer = ByteArray(8192)
        val output = ByteArrayOutputStream()
        var written = 0L
        while (true) {
            val read = zip.read(buffer)
            if (read < 0) {
                break
            }
            written += read
            if (written > 80L * 1024 * 1024) {
                throw IllegalArgumentException("Um ficheiro do pacote excede o tamanho permitido.")
            }
            if (compressedSize > 0 && written > compressedSize * 80 && written > 1_000_000) {
                throw IllegalArgumentException("O pacote parece comprimido de forma abusiva e foi rejeitado.")
            }
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }

    companion object {
        private const val PICK_CODE = 41901
    }
}
