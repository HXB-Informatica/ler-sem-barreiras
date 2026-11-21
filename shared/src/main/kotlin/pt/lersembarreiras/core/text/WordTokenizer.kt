package pt.lersembarreiras.core.text

import pt.lersembarreiras.core.model.TextToken
import java.text.Normalizer
import java.util.Locale

object WordTokenizer {
    private val combiningMarks = "\\p{M}+".toRegex()
    private val nonAlphanumeric = "[^\\p{L}\\p{N}]+".toRegex()
    private val portuguese = Locale("pt", "PT")

    fun tokenize(text: String): List<TextToken> {
        val tokens = mutableListOf<TextToken>()
        var index = 0
        val length = text.length
        while (index < length) {
            while (index < length && text[index].isWhitespace()) {
                index++
            }
            if (index >= length) break
            val start = index
            while (index < length && !text[index].isWhitespace()) {
                index++
            }
            val original = text.substring(start, index)
            val normalized = normalizeForMatch(original)
            if (normalized.isNotEmpty()) {
                tokens += TextToken(
                    original = original,
                    normalized = normalized,
                    startChar = start,
                    endChar = index,
                )
            }
        }
        return tokens
    }

    fun normalizeForMatch(word: String): String {
        val decomposed = Normalizer.normalize(word, Normalizer.Form.NFD)
        val stripped = combiningMarks.replace(decomposed, "")
            .lowercase(portuguese)
            .let { nonAlphanumeric.replace(it, "") }
        return NUMBER_WORDS[stripped] ?: stripped
    }

    fun levenshtein(left: String, right: String): Int {
        if (left == right) return 0
        if (left.isEmpty()) return right.length
        if (right.isEmpty()) return left.length
        val previous = IntArray(right.length + 1) { it }
        val current = IntArray(right.length + 1)
        for (i in left.indices) {
            current[0] = i + 1
            for (j in right.indices) {
                val cost = if (left[i] == right[j]) 0 else 1
                current[j + 1] = minOf(
                    current[j] + 1,
                    previous[j + 1] + 1,
                    previous[j] + cost,
                )
            }
            for (j in previous.indices) {
                previous[j] = current[j]
            }
        }
        return previous[right.length]
    }

    private val NUMBER_WORDS = mapOf(
        "0" to "zero",
        "1" to "um",
        "2" to "dois",
        "3" to "tres",
        "4" to "quatro",
        "5" to "cinco",
        "6" to "seis",
        "7" to "sete",
        "8" to "oito",
        "9" to "nove",
        "10" to "dez",
    )
}
