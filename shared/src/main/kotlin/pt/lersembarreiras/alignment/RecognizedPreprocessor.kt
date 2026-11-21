package pt.lersembarreiras.alignment

import pt.lersembarreiras.core.model.RecognizedWord
import pt.lersembarreiras.core.model.TextToken
import pt.lersembarreiras.core.text.WordTokenizer

/**
 * Prepara a transcrição antes do Needleman–Wunsch: hesitações, números e
 * fragmentos consecutivos que juntos formam uma palavra do texto.
 */
object RecognizedPreprocessor {
    private val fillers = setOf("uh", "hum", "hmm", "eh", "ah", "er", "erm", "euh", "ahh")

    fun prepare(source: List<TextToken>, recognized: List<RecognizedWord>): List<RecognizedWord> {
        if (recognized.isEmpty()) return recognized
        val wanted = source.map { it.normalized }.filter { it.isNotEmpty() }.toSet()
        val withoutFillers = recognized.filter { word ->
            val norm = WordTokenizer.normalizeForMatch(word.text)
            norm.isNotEmpty() && (norm !in fillers || norm in wanted)
        }
        return mergeFragments(wanted, withoutFillers)
    }

    private fun mergeFragments(
        wanted: Set<String>,
        recognized: List<RecognizedWord>,
    ): List<RecognizedWord> {
        if (recognized.size < 2) return recognized
        val out = mutableListOf<RecognizedWord>()
        var index = 0
        while (index < recognized.size) {
            val current = recognized[index]
            val next = recognized.getOrNull(index + 1)
            val left = WordTokenizer.normalizeForMatch(current.text)
            val right = next?.let { WordTokenizer.normalizeForMatch(it.text) }.orEmpty()
            val combined = left + right
            if (next != null && combined in wanted && left !in wanted && combined.length >= 3) {
                out += RecognizedWord(
                    text = current.text + next.text,
                    startMs = current.startMs,
                    endMs = maxOf(next.endMs, current.endMs),
                    probability = minOf(current.probability, next.probability),
                )
                index += 2
            } else {
                out += current
                index += 1
            }
        }
        return out
    }
}
