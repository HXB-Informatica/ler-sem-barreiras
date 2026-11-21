package pt.lersembarreiras.studio.sync

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import pt.lersembarreiras.core.model.RecognizedWord

/** Interpreta o JSON do whisper-cli Windows (formato `-oj` / `-ojf`). */
object WhisperJsonParser {
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(raw: String): List<RecognizedWord> {
        val root = json.parseToJsonElement(raw).jsonObject
        val fromTranscription = root["transcription"]?.let { parseTranscription(it) }.orEmpty()
        if (fromTranscription.isNotEmpty()) return fromTranscription
        val segments = root["result"]?.jsonObject?.get("segments")
            ?: root["segments"]
        return segments?.let { parseSegments(it) }.orEmpty()
    }

    private fun parseTranscription(element: JsonElement): List<RecognizedWord> {
        val array = element as? JsonArray ?: return emptyList()
        val words = mutableListOf<RecognizedWord>()
        array.forEach { item ->
            val obj = item as? JsonObject ?: return@forEach
            val offsets = obj["offsets"]?.jsonObject
            val start = offsets?.get("from")?.jsonPrimitive?.longOrNull ?: 0L
            val end = offsets?.get("to")?.jsonPrimitive?.longOrNull ?: start
            val tokens = obj["tokens"]?.jsonArray
            if (tokens != null && tokens.isNotEmpty()) {
                words += parseTokens(tokens)
            } else {
                val text = obj["text"]?.jsonPrimitive?.contentOrNull.orEmpty()
                words += splitEvenly(text, start, end)
            }
        }
        return words.filter { it.text.isNotBlank() }
    }

    private fun parseSegments(element: JsonElement): List<RecognizedWord> {
        val array = element as? JsonArray ?: return emptyList()
        val words = mutableListOf<RecognizedWord>()
        array.forEach { item ->
            val obj = item as? JsonObject ?: return@forEach
            val start = timeMs(obj["start"] ?: obj["t0"])
            val end = timeMs(obj["end"] ?: obj["t1"], start)
            val tokens = obj["tokens"]?.jsonArray ?: obj["words"]?.jsonArray
            if (tokens != null && tokens.isNotEmpty()) {
                words += parseTokens(tokens)
            } else {
                words += splitEvenly(obj["text"]?.jsonPrimitive?.contentOrNull.orEmpty(), start, end)
            }
        }
        return words.filter { it.text.isNotBlank() }
    }

    private fun parseTokens(tokens: JsonArray): List<RecognizedWord> {
        return tokens.mapNotNull { item ->
            val obj = item as? JsonObject ?: return@mapNotNull null
            val text = (obj["text"] ?: obj["word"] ?: obj["token"])?.jsonPrimitive?.contentOrNull
                ?.trim()
                ?.trim(',', '.', '!', '?', ';', ':')
                .orEmpty()
            if (text.isBlank() || text.startsWith("[")) return@mapNotNull null
            val start = timeMs(obj["t0"] ?: obj["start"] ?: obj["offsets"]?.jsonObject?.get("from"))
            val end = timeMs(obj["t1"] ?: obj["end"] ?: obj["offsets"]?.jsonObject?.get("to"), start)
            RecognizedWord(text = text, startMs = start, endMs = end.coerceAtLeast(start + 20), probability = 0.7f)
        }
    }

    private fun splitEvenly(text: String, startMs: Long, endMs: Long): List<RecognizedWord> {
        val parts = text.split(Regex("\\s+")).map { it.trim().trim(',', '.', '!', '?') }.filter { it.isNotBlank() }
        if (parts.isEmpty()) return emptyList()
        val span = (endMs - startMs).coerceAtLeast(parts.size * 40L)
        val step = span / parts.size
        return parts.mapIndexed { index, word ->
            val start = startMs + index * step
            RecognizedWord(word, start, start + step.coerceAtLeast(40), 0.6f)
        }
    }

    private fun timeMs(element: JsonElement?, fallback: Long = 0L): Long {
        if (element == null) return fallback
        val primitive = runCatching { element.jsonPrimitive }.getOrNull() ?: return fallback
        primitive.longOrNull?.let { return it }
        primitive.intOrNull?.let { return it.toLong() }
        val text = primitive.contentOrNull ?: return fallback
        return parseClock(text) ?: fallback
    }

    private fun parseClock(text: String): Long? {
        // 00:00:00,320 ou 00:00:00.320
        val normalized = text.trim().replace(',', '.')
        val parts = normalized.split(':')
        if (parts.size < 2) return normalized.toFloatOrNull()?.let { (it * 1000).toLong() }
        val hours = if (parts.size == 3) parts[0].toLongOrNull() ?: 0 else 0
        val minutes = if (parts.size == 3) parts[1].toLongOrNull() ?: 0 else parts[0].toLongOrNull() ?: 0
        val seconds = parts.last().toFloatOrNull() ?: return null
        return ((hours * 3600 + minutes * 60) * 1000 + (seconds * 1000)).toLong()
    }
}
