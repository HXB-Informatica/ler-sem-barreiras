package pt.lersembarreiras.lsb

import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import pt.lersembarreiras.alignment.TimeValidator
import pt.lersembarreiras.alignment.WordIdentity
import pt.lersembarreiras.core.model.ReadingContent
import pt.lersembarreiras.core.model.WordTiming
import pt.lersembarreiras.core.packageformat.ContentPackageMapper
import pt.lersembarreiras.core.text.WordTokenizer

/**
 * Pacote `.lsb`: ZIP versionado, sem caminhos absolutos.
 * O texto canónico está em `texto.txt`; os tempos em `sync/palavras.json`.
 */
object LsbArchive {
    const val FORMAT_ID = "lersembarreiras-lsb"
    const val SCHEMA_VERSION = 1
    const val MAX_SUPPORTED_SCHEMA = 1
    const val MANIFEST_PATH = "manifest.json"
    const val METADATA_PATH = "metadata.json"
    const val TEXT_PATH = "texto.txt"
    const val WORDS_PATH = "sync/palavras.json"
    const val AUDIO_PATH = "audio/leitura.wav"
    const val FILE_EXTENSION = "lsb"

    private val allowed = setOf(MANIFEST_PATH, METADATA_PATH, TEXT_PATH, WORDS_PATH, AUDIO_PATH)
    private val json = ContentPackageMapper.json

    fun export(content: ReadingContent, audio: ByteArray, output: OutputStream, source: String = "studio") {
        if (content.text.isBlank()) throw LsbException("O exercício não tem texto para exportar.")
        if (audio.size < 44) throw LsbException("O exercício não tem uma narração WAV para exportar.")
        val text = content.text.replace("\r\n", "\n").replace('\r', '\n')
        val words = WordIdentity.ensure(text, content.words)
        validateWords(text, words, content.durationMs)
        val audioDuration = WavProbe.durationMs(audio)
        if (content.durationMs > 0 && kotlin.math.abs(audioDuration - content.durationMs) > 250) {
            throw LsbException("A duração declarada não corresponde ao ficheiro de áudio.")
        }
        val metadata = LsbMetadata(
            title = content.title.ifBlank { "Sem título" },
            durationMs = if (content.durationMs > 0) content.durationMs else audioDuration,
            syncStatus = content.syncStatus,
            createdAtEpochMs = content.createdAtEpochMs,
            updatedAtEpochMs = content.updatedAtEpochMs,
            originalContentId = content.id,
            source = source,
        )
        val textBytes = text.toByteArray(Charsets.UTF_8)
        val wordsBytes = json.encodeToString(kotlinx.serialization.builtins.ListSerializer(WordTiming.serializer()), words)
            .toByteArray(Charsets.UTF_8)
        val metaBytes = json.encodeToString(LsbMetadata.serializer(), metadata).toByteArray(Charsets.UTF_8)
        val files = linkedMapOf(
            METADATA_PATH to sha256(metaBytes),
            TEXT_PATH to sha256(textBytes),
            WORDS_PATH to sha256(wordsBytes),
            AUDIO_PATH to sha256(audio),
        )
        val manifest = LsbManifest(
            contentId = content.id,
            files = files,
            createdAtEpochMs = content.createdAtEpochMs,
        )
        val manifestBytes = json.encodeToString(LsbManifest.serializer(), manifest).toByteArray(Charsets.UTF_8)
        val stamp = content.createdAtEpochMs.coerceAtLeast(0)
        ZipOutputStream(output).use { zip ->
            writeEntry(zip, MANIFEST_PATH, manifestBytes, stamp)
            writeEntry(zip, METADATA_PATH, metaBytes, stamp)
            writeEntry(zip, TEXT_PATH, textBytes, stamp)
            writeEntry(zip, WORDS_PATH, wordsBytes, stamp)
            writeEntry(zip, AUDIO_PATH, audio, stamp)
        }
    }

    fun import(input: InputStream): LsbBundle {
        val entries = SafeZip.readWhitelisted(input, allowed)
        val manifestBytes = entries[MANIFEST_PATH]
            ?: throw LsbException("Falta manifest.json. Este ficheiro não é um pacote Ler sem Barreiras.")
        val manifest = runCatching {
            json.decodeFromString(LsbManifest.serializer(), manifestBytes.toString(Charsets.UTF_8))
        }.getOrElse {
            throw LsbException("O manifesto do pacote está danificado.", it)
        }
        if (manifest.format != FORMAT_ID) {
            throw LsbException("Este ficheiro não é um pacote Ler sem Barreiras (.lsb).")
        }
        if (manifest.schemaVersion > MAX_SUPPORTED_SCHEMA) {
            throw LsbException(
                "Este pacote usa a versão ${manifest.schemaVersion}, mais recente do que esta aplicação. Actualize o Ler sem Barreiras.",
            )
        }
        if (manifest.schemaVersion < 1) {
            throw LsbException("A versão do pacote não é reconhecida.")
        }
        fun requireFile(path: String): ByteArray {
            val bytes = entries[path] ?: throw LsbException("Falta o ficheiro obrigatório $path.")
            val expected = manifest.files[path]
                ?: throw LsbException("O manifesto não indica a soma de verificação de $path.")
            val actual = sha256(bytes)
            if (!expected.equals(actual, ignoreCase = true)) {
                throw LsbException("A soma de verificação de $path não coincide. O pacote pode ter sido alterado.")
            }
            return bytes
        }
        val metadata = json.decodeFromString(
            LsbMetadata.serializer(),
            requireFile(METADATA_PATH).toString(Charsets.UTF_8),
        )
        val text = requireFile(TEXT_PATH).toString(Charsets.UTF_8).replace("\r\n", "\n")
        val words = WordIdentity.ensure(
            text,
            json.decodeFromString(
                kotlinx.serialization.builtins.ListSerializer(WordTiming.serializer()),
                requireFile(WORDS_PATH).toString(Charsets.UTF_8),
            ),
        )
        val audio = requireFile(AUDIO_PATH)
        if (text.isBlank()) throw LsbException("O texto do exercício está vazio.")
        validateWords(text, words, metadata.durationMs)
        val audioDuration = WavProbe.durationMs(audio)
        if (metadata.durationMs > 0 && kotlin.math.abs(audioDuration - metadata.durationMs) > 250) {
            throw LsbException("A duração do áudio não corresponde aos metadados do pacote.")
        }
        if (metadata.audioFile.contains("..") || metadata.textFile.contains("..")) {
            throw LsbException("O pacote contém referências internas inválidas.")
        }
        return LsbBundle(manifest, metadata, text, words, audio)
    }

    fun toContent(bundle: LsbBundle, assignedId: String, audioRelativePath: String): ReadingContent = ReadingContent(
        id = assignedId,
        title = bundle.metadata.title,
        text = bundle.text,
        audioRelativePath = audioRelativePath,
        durationMs = bundle.metadata.durationMs,
        words = bundle.words,
        syncStatus = bundle.metadata.syncStatus,
        createdAtEpochMs = bundle.metadata.createdAtEpochMs,
        updatedAtEpochMs = bundle.metadata.updatedAtEpochMs,
    )

    fun sha256(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString("") { byte -> "%02x".format(byte) }
    }

    fun identical(existing: ReadingContent, bundle: LsbBundle): Boolean {
        if (existing.text != bundle.text) return false
        if (existing.words.size != bundle.words.size) return false
        return existing.words.zip(bundle.words).all { (left, right) ->
            left.text == right.text && left.startMs == right.startMs && left.endMs == right.endMs &&
                left.resolvedState() == right.resolvedState()
        }
    }

    private fun validateWords(text: String, words: List<WordTiming>, durationMs: Long) {
        val tokens = WordTokenizer.tokenize(text)
        if (tokens.size != words.size) {
            throw LsbException("O número de palavras não corresponde ao texto original.")
        }
        tokens.zip(words).forEachIndexed { index, (token, word) ->
            if (token.original != word.text) {
                throw LsbException("A ordem das palavras não coincide com o texto (posição ${index + 1}).")
            }
        }
        TimeValidator.inspect(words, durationMs)
    }

    private fun writeEntry(zip: ZipOutputStream, name: String, bytes: ByteArray, timeMs: Long) {
        val entry = ZipEntry(name)
        entry.time = timeMs
        entry.size = bytes.size.toLong()
        zip.putNextEntry(entry)
        zip.write(bytes)
        zip.closeEntry()
    }

    fun peek(bytes: ByteArray): LsbBundle = import(ByteArrayInputStream(bytes))
}
