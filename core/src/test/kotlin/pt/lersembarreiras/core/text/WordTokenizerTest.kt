package pt.lersembarreiras.core.text

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WordTokenizerTest {
    @Test
    fun splitsPortugueseSentenceAndKeepsOriginalSpelling() {
        val text = "Olá, mundo!"
        val tokens = WordTokenizer.tokenize(text)
        assertEquals(2, tokens.size)
        assertEquals("Olá,", tokens[0].original)
        assertEquals("ola", tokens[0].normalized)
        assertEquals(0, tokens[0].startChar)
        assertEquals("mundo!", tokens[1].original)
        assertEquals("mundo", tokens[1].normalized)
    }

    @Test
    fun handlesRepeatedWordsAndPunctuation() {
        val text = "O gato, o gato corre."
        val tokens = WordTokenizer.tokenize(text)
        assertEquals(listOf("o", "gato", "o", "gato", "corre"), tokens.map { it.normalized })
        assertEquals("gato,", tokens[1].original)
    }

    @Test
    fun ignoresPunctuationOnlyTokens() {
        val tokens = WordTokenizer.tokenize("Um — dois.")
        assertEquals(listOf("um", "dois"), tokens.map { it.normalized })
    }

    @Test
    fun preservesCharacterOffsetsInOriginalText() {
        val text = "Boa tarde"
        val tokens = WordTokenizer.tokenize(text)
        assertEquals("Boa", text.substring(tokens[0].startChar, tokens[0].endChar))
        assertEquals("tarde", text.substring(tokens[1].startChar, tokens[1].endChar))
    }

    @Test
    fun levenshteinDetectsSmallAsrErrors() {
        assertEquals(0, WordTokenizer.levenshtein("casa", "casa"))
        assertEquals(1, WordTokenizer.levenshtein("casa", "casas"))
        assertTrue(WordTokenizer.levenshtein("rapido", "rapido") < WordTokenizer.levenshtein("rapido", "lento"))
    }
}
