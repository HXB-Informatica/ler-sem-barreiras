package pt.lersembarreiras.lsb

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import pt.lersembarreiras.core.model.ReadingContent
import pt.lersembarreiras.core.packageformat.ContentPackageMapper
import pt.lersembarreiras.core.model.SyncStatus
import pt.lersembarreiras.core.model.WordState
import pt.lersembarreiras.core.model.WordTiming
import pt.lersembarreiras.editor.SyncEditor

class LsbArchiveTest {
    @Test
    fun roundTripOlaUmDoisTresPreservesWordsAudioAndStates() {
        val text = "Olá um dois três"
        val words = listOf(
            timed("Olá", 0, 3, 50, 280, 0, WordState.VERIFIED),
            timed("um", 4, 6, 300, 520, 1, WordState.VERIFIED),
            timed("dois", 7, 11, 900, 1200, 2, WordState.MANUAL),
            timed("três", 12, 16, 1300, 1750, 3, WordState.VERIFIED),
        )
        val content = exercise("id-ola", text, words, 2000)
        val audio = pcmWav(16_000, 16, 2_000)
        val zip = ByteArrayOutputStream()
        LsbArchive.export(content, audio, zip, source = "studio")
        val first = zip.toByteArray()
        val again = ByteArrayOutputStream()
        LsbArchive.export(content, audio, again, source = "studio")
        assertArrayEquals(first, again.toByteArray())

        val bundle = LsbArchive.import(ByteArrayInputStream(first))
        assertEquals(4, bundle.words.size)
        assertEquals(listOf("Olá", "um", "dois", "três"), bundle.words.map { it.text })
        assertEquals(50L, bundle.words[0].startMs)
        assertEquals(1750L, bundle.words[3].endMs)
        assertEquals(WordState.VERIFIED, bundle.words[0].resolvedState())
        assertEquals(WordState.MANUAL, bundle.words[2].resolvedState())
        assertArrayEquals(audio, bundle.audio)
        assertEquals(LsbArchive.sha256(audio), bundle.manifest.files[LsbArchive.AUDIO_PATH])
        assertEquals("Olá um dois três", bundle.text)
        assertEquals("id-ola", bundle.manifest.contentId)
    }

    @Test
    fun paragraphsKeepWordOrder() {
        val text = "Olá um.\n\nDois três."
        val tokens = pt.lersembarreiras.core.text.WordTokenizer.tokenize(text)
        val words = tokens.mapIndexed { index, token ->
            WordTiming(token.original, token.startChar, token.endChar, index * 200L, index * 200L + 150, 1f, index = index)
        }
        val zip = ByteArrayOutputStream()
        LsbArchive.export(exercise("p", text, words, 900), pcmWav(16_000, 16, 900), zip)
        val bundle = LsbArchive.import(ByteArrayInputStream(zip.toByteArray()))
        assertEquals(tokens.map { it.original }, bundle.words.map { it.text })
        assertEquals(4, bundle.words.size)
    }

    @Test
    fun rejectsChecksumTampering() {
        val original = LsbArchive.import(ByteArrayInputStream(exportSample()))
        val tamperedText = (original.text + " ").toByteArray()
        val error = runCatching {
            LsbArchive.import(
                ByteArrayInputStream(
                    rawZip(
                        mapOf(
                            "manifest.json" to ContentPackageMapper.json.encodeToString(
                                LsbManifest.serializer(),
                                original.manifest,
                            ),
                            "metadata.json" to ContentPackageMapper.json.encodeToString(
                                LsbMetadata.serializer(),
                                original.metadata,
                            ),
                            "texto.txt" to String(tamperedText, Charsets.ISO_8859_1),
                            "sync/palavras.json" to "[]",
                            "audio/leitura.wav" to String(original.audio, Charsets.ISO_8859_1),
                        ),
                    ),
                ),
            )
        }.exceptionOrNull()
        assertTrue(error is LsbException)
        assertTrue(error!!.message!!.contains("soma de verificação"))
    }

    @Test
    fun rejectsFutureSchema() {
        val error = runCatching {
            LsbArchive.import(ByteArrayInputStream(rawZip(mapOf(
                "manifest.json" to """{"format":"lersembarreiras-lsb","schemaVersion":99,"contentId":"x","files":{},"createdAtEpochMs":1}""",
            ))))
        }.exceptionOrNull() as LsbException
        assertTrue(error.message!!.contains("mais recente"))
    }

    @Test
    fun rejectsPathTraversal() {
        val error = runCatching {
            LsbArchive.import(ByteArrayInputStream(rawZip(mapOf("../evil.txt" to "nope"))))
        }.exceptionOrNull()
        assertTrue(error is LsbException)
        assertTrue(error!!.message!!.contains("rejeitado") || error.message!!.contains("não permitido") || error.message!!.contains("manifest"))
    }

    @Test
    fun rejectsZipBombEntryCount() {
        val entries = (1..20).associate { "file$it.txt" to "x" } + mapOf(
            "manifest.json" to """{"format":"lersembarreiras-lsb","schemaVersion":1,"contentId":"x","files":{},"createdAtEpochMs":1}""",
        )
        val error = runCatching { LsbArchive.import(ByteArrayInputStream(rawZip(entries))) }.exceptionOrNull()
        assertTrue(error is LsbException)
    }

    @Test
    fun missingTextIsReadableError() {
        val audio = pcmWav(16_000, 16, 500)
        val full = exportSample()
        val bundle = LsbArchive.import(ByteArrayInputStream(full))
        val incomplete = rawZip(
            mapOf(
                "manifest.json" to """{"format":"lersembarreiras-lsb","schemaVersion":1,"contentId":"${bundle.manifest.contentId}","files":{"metadata.json":"00","texto.txt":"00","sync/palavras.json":"00","audio/leitura.wav":"${LsbArchive.sha256(audio)}"},"createdAtEpochMs":1}""",
                "metadata.json" to "{}",
                "audio/leitura.wav" to String(audio, Charsets.ISO_8859_1),
            ),
        )
        val error = runCatching { LsbArchive.import(ByteArrayInputStream(incomplete)) }.exceptionOrNull()
        assertTrue(error is LsbException)
        assertTrue(error!!.message!!.isNotBlank())
    }

    @Test
    fun editorCorrectionsSurviveExport() {
        val editor = SyncEditor()
        editor.load(
            listOf(
                timed("Olá", 0, 3, 50, 280, 0, WordState.AUTO),
                timed("um", 4, 6, 300, 520, 1, WordState.AUTO),
                WordTiming("dois", 7, 11, null, null, 0f, index = 2),
                WordTiming("três", 12, 16, null, null, 0f, index = 3),
            ),
            2000,
        )
        editor.setInterval(2, 900, 1200)
        editor.setInterval(3, 1300, 1700)
        editor.verify(2)
        val zip = ByteArrayOutputStream()
        LsbArchive.export(exercise("e", "Olá um dois três", editor.snapshot.words, 2000), pcmWav(16_000, 16, 2000), zip)
        val imported = LsbArchive.import(ByteArrayInputStream(zip.toByteArray()))
        assertEquals(900L, imported.words[2].startMs)
        assertEquals(WordState.VERIFIED, imported.words[2].resolvedState())
        assertEquals(4, imported.words.size)
    }

    @Test
    fun newIdWhenCollisionWouldOverwriteDifferentContent() {
        val a = LsbArchive.toContent(
            LsbArchive.import(ByteArrayInputStream(exportSample())),
            "same-id",
            "contents/same-id/audio.wav",
        )
        assertEquals("same-id", a.id)
        assertNotEquals("id-ola", a.id)
    }

    private fun exportSample(): ByteArray {
        val words = listOf(
            timed("Olá", 0, 3, 50, 280, 0, WordState.VERIFIED),
            timed("um", 4, 6, 300, 520, 1, WordState.VERIFIED),
            timed("dois", 7, 11, 900, 1200, 2, WordState.VERIFIED),
            timed("três", 12, 16, 1300, 1750, 3, WordState.VERIFIED),
        )
        val zip = ByteArrayOutputStream()
        LsbArchive.export(exercise("id-ola", "Olá um dois três", words, 2000), pcmWav(16_000, 16, 2000), zip)
        return zip.toByteArray()
    }

    private fun exercise(id: String, text: String, words: List<WordTiming>, durationMs: Long) = ReadingContent(
        id = id,
        title = "Frase",
        text = text,
        audioRelativePath = "audio/leitura.wav",
        durationMs = durationMs,
        words = words,
        syncStatus = SyncStatus.COMPLETED,
        createdAtEpochMs = 1_700_000_000_000L,
        updatedAtEpochMs = 1_700_000_000_100L,
    )

    private fun timed(text: String, startChar: Int, endChar: Int, start: Long, end: Long, index: Int, state: WordState) =
        WordTiming(
            text = text,
            startChar = startChar,
            endChar = endChar,
            startMs = start,
            endMs = end,
            confidence = 1f,
            manuallyAdjusted = state == WordState.MANUAL || state == WordState.VERIFIED,
            needsReview = false,
            id = "w-$index-$startChar-$endChar",
            index = index,
            state = state,
        )

    companion object {
        fun pcmWav(sampleRate: Int, bits: Int, durationMs: Long): ByteArray {
            val channels = 1
            val bytesPerSample = bits / 8
            val frames = (sampleRate * durationMs / 1000L).toInt()
            val data = frames * bytesPerSample * channels
            val buffer = ByteBuffer.allocate(44 + data).order(ByteOrder.LITTLE_ENDIAN)
            buffer.put("RIFF".toByteArray())
            buffer.putInt(36 + data)
            buffer.put("WAVE".toByteArray())
            buffer.put("fmt ".toByteArray())
            buffer.putInt(16)
            buffer.putShort(1)
            buffer.putShort(channels.toShort())
            buffer.putInt(sampleRate)
            buffer.putInt(sampleRate * bytesPerSample * channels)
            buffer.putShort((bytesPerSample * channels).toShort())
            buffer.putShort(bits.toShort())
            buffer.put("data".toByteArray())
            buffer.putInt(data)
            buffer.put(ByteArray(data))
            return buffer.array()
        }

        fun rawZip(files: Map<String, String>): ByteArray {
            val out = ByteArrayOutputStream()
            ZipOutputStream(out).use { zip ->
                files.forEach { (name, body) ->
                    zip.putNextEntry(ZipEntry(name))
                    zip.write(body.toByteArray(Charsets.ISO_8859_1))
                    zip.closeEntry()
                }
            }
            return out.toByteArray()
        }
    }
}
