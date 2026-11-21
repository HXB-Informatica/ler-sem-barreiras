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
    fun usesDtwTokenEndsAsWordBoundaries() {
        val raw = """
            {
              "transcription": [
                {
                  "text": " O rato roeu",
                  "offsets": {"from": 0, "to": 4690},
                  "tokens": [
                    {"text": " O", "t_dtw": 78, "p": 0.9},
                    {"text": " rato", "t_dtw": 160, "p": 0.8},
                    {"text": " roeu", "t_dtw": 250, "p": 0.8}
                  ]
                }
              ]
            }
        """.trimIndent()
        val words = WhisperJsonParser.parse(raw)
        assertEquals(listOf("O", "rato", "roeu"), words.map { it.text })
        assertEquals(0L, words[0].startMs)
        assertEquals(780L, words[0].endMs)
        assertEquals(780L, words[1].startMs)
        assertEquals(1600L, words[1].endMs)
        assertEquals(1600L, words[2].startMs)
        assertEquals(2500L, words[2].endMs)
    }

    @Test
    fun emptyJsonYieldsNoWords() {
        assertTrue(WhisperJsonParser.parse("""{"transcription":[]}""").isEmpty())
    }
}
