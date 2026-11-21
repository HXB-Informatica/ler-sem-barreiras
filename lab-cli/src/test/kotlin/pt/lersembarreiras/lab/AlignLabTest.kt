package pt.lersembarreiras.lab

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AlignLabTest {
    @Test
    fun batchAlignsOlaUmDoisTres() {
        val dir = kotlin.io.path.createTempDirectory("lab-cli").toFile()
        val input = File(dir, "in.jsonl")
        val output = File(dir, "out.jsonl")
        input.writeText(
            """{"id":"c1","text":"Olá um dois três","audioDurationMs":2000,"recognized":[{"text":"ola","startMs":50,"endMs":280,"probability":0.9},{"text":"um","startMs":300,"endMs":520,"probability":0.9}]}""",
        )
        main(arrayOf(input.absolutePath, output.absolutePath))
        val line = output.readText().trim()
        assertTrue(line.contains("\"id\":\"c1\""))
        assertTrue(line.contains("Olá"))
        assertEquals(1, output.readLines().size)
        dir.deleteRecursively()
    }
}
