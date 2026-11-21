package pt.lersembarreiras.lsb

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.zip.ZipInputStream

internal object SafeZip {
    const val MAX_ENTRIES = 16
    const val MAX_ENTRY_BYTES = 80L * 1024 * 1024
    const val MAX_TOTAL_BYTES = 100L * 1024 * 1024
    const val MAX_RATIO = 80

    fun readWhitelisted(input: InputStream, allowed: Set<String>): Map<String, ByteArray> {
        val out = linkedMapOf<String, ByteArray>()
        var total = 0L
        ZipInputStream(input).use { zip ->
            var count = 0
            var entry = zip.nextEntry
            while (entry != null) {
                count++
                if (count > MAX_ENTRIES) {
                    throw LsbException("O pacote tem demasiadas entradas e foi rejeitado.")
                }
                if (entry.isDirectory) {
                    zip.closeEntry()
                    entry = zip.nextEntry
                    continue
                }
                val name = sanitize(entry.name)
                if (name !in allowed) {
                    throw LsbException("O pacote contém um ficheiro não permitido: $name")
                }
                val compressed = entry.compressedSize
                val bytes = readLimited(zip, compressed)
                total += bytes.size
                if (total > MAX_TOTAL_BYTES) {
                    throw LsbException("O pacote é demasiado grande e foi rejeitado.")
                }
                out[name] = bytes
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        return out
    }

    fun sanitize(raw: String): String {
        val name = raw.replace('\\', '/').trim().trimStart('/')
        if (name.isEmpty() || name.contains("..") || name.startsWith("/") || name.contains(':')) {
            throw LsbException("O pacote tenta escrever fora da pasta de destino e foi rejeitado.")
        }
        if (name.split('/').any { it.isEmpty() || it == "." || it == ".." }) {
            throw LsbException("O pacote tenta escrever fora da pasta de destino e foi rejeitado.")
        }
        return name
    }

    private fun readLimited(zip: ZipInputStream, compressedSize: Long): ByteArray {
        val buffer = ByteArray(8192)
        val output = ByteArrayOutputStream()
        var written = 0L
        while (true) {
            val read = zip.read(buffer)
            if (read < 0) break
            written += read
            if (written > MAX_ENTRY_BYTES) {
                throw LsbException("Um ficheiro do pacote excede o tamanho permitido.")
            }
            if (compressedSize > 0 && written > compressedSize * MAX_RATIO && written > 1_000_000) {
                throw LsbException("O pacote parece comprimido de forma abusiva e foi rejeitado.")
            }
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }
}
