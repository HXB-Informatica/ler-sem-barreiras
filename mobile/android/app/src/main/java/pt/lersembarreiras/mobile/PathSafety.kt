package pt.lersembarreiras.mobile

import java.io.File

internal object PathSafety {
    fun resolve(root: File, relative: String): File {
        val cleaned = relative.replace('\\', '/').trim().trimStart('/')
        if (cleaned.isEmpty() || cleaned.contains("..") || cleaned.contains(':')) {
            throw IllegalArgumentException("Caminho interno inválido.")
        }
        cleaned.split('/').forEach { part ->
            if (part.isEmpty() || part == "." || part == "..") {
                throw IllegalArgumentException("Caminho interno inválido.")
            }
        }
        val target = File(root, cleaned).canonicalFile
        val rootCanon = root.canonicalFile
        if (!target.path.startsWith(rootCanon.path)) {
            throw IllegalArgumentException("Caminho interno inválido.")
        }
        return target
    }
}
