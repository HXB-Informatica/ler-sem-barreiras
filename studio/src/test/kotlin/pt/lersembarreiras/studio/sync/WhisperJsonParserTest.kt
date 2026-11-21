package pt.lersembarreiras.studio.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WhisperJsonParserTest {
    @Test
    fun parsesClassicOjSegments() {
        val raw = """
            {
              "transcription": [
                {"text": " Olá", "offsets": {"from": 40, "to": 280}},
                {"text": " um", "offsets": {"from": 300, "to": 520}},
                {"text": " dois três", "offsets": {"from": 540, "to": 1100}}
              ]
            }
        """.trimIndent()
        val words = WhisperJsonParser.parse(raw)
        assertTrue(words.size >= 3)
        assertEquals("Olá", words[0].text)
        assertEquals(40L, words[0].startMs)
        assertEquals(280L, words[0].endMs)
        assertTrue(words.any { it.text.equals("dois", ignoreCase = true) })
    }

    @Test
    fun emptyJsonYieldsNoWords() {
        assertTrue(WhisperJsonParser.parse("""{"transcription":[]}""").isEmpty())
    }
}
