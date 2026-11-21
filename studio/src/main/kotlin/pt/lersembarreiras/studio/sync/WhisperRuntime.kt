package pt.lersembarreiras.studio.sync

import java.io.File

data class WhisperInstall(
    val executable: File,
    val model: File,
) {
    val ready: Boolean get() = executable.isFile && model.isFile && model.length() > 1_000_000
}

/**
 * Localiza o whisper-cli **Windows** (nunca o .so Android).
 *
 * Ordem: `compose.application.resources.dir`, variável
 * `LER_SEM_BARREIRAS_WHISPER_DIR`, `%USERPROFILE%\.lersembarreiras\whisper`,
 * `studio/packaging/windows` no repositório.
 */
object WhisperRuntime {
    const val EXE_NAME = "whisper-cli.exe"
    const val MODEL_NAME = "ggml-tiny.bin"

    fun locate(): WhisperInstall? {
        return candidateDirs()
            .mapNotNull { dir ->
                val exe = File(dir, EXE_NAME).takeIf { it.isFile }
                    ?: dir.walkTopDown().maxDepth(3).firstOrNull { it.name.equals(EXE_NAME, true) }
                val model = File(dir, MODEL_NAME).takeIf { it.isFile }
                    ?: File(dir, "models/$MODEL_NAME").takeIf { it.isFile }
                    ?: dir.walkTopDown().maxDepth(3).firstOrNull { it.name == MODEL_NAME }
                if (exe != null && model != null) WhisperInstall(exe, model) else null
            }
            .firstOrNull { it.ready }
    }

    fun status(): String {
        val found = locate()
        return if (found != null) {
            "Motor local: ${found.executable.name} + ${found.model.name} (${found.model.length() / 1_000_000} MB)."
        } else {
            "whisper-cli.exe Windows não encontrado. Execute .\\gradlew.bat :studio:fetchWhisperWindows " +
                "ou coloque o binário e ggml-tiny.bin em %USERPROFILE%\\.lersembarreiras\\whisper"
        }
    }

    private fun candidateDirs(): List<File> {
        val dirs = mutableListOf<File>()
        System.getProperty("compose.application.resources.dir")?.let { dirs += File(it) }
        System.getenv("LER_SEM_BARREIRAS_WHISPER_DIR")?.let { dirs += File(it) }
        dirs += File(System.getProperty("user.home"), ".lersembarreiras/whisper")
        dirs += File("packaging/windows")
        dirs += File("studio/packaging/windows")
        System.getProperty("user.dir")?.let { root ->
            dirs += File(root, "packaging/windows")
            dirs += File(root, "studio/packaging/windows")
        }
        return dirs.distinct()
    }
}
