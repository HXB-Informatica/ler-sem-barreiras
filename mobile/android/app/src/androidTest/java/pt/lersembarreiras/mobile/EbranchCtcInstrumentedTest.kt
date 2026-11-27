package pt.lersembarreiras.mobile

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Inferência EBranch CTC no dispositivo, sem Metro e sem rede. O WAV de teste é síntese (não voz de aluno). */
@RunWith(AndroidJUnit4::class)
class EbranchCtcInstrumentedTest {
    @Test
    fun recognizesOfflineSamplesWithValidTimestamps() {
        val started = System.currentTimeMillis()
        val app = InstrumentationRegistry.getInstrumentation().targetContext
        val installed = EbranchCtc.install(app.assets, listOf(File(app.filesDir, "ctc")))
        val engine = EbranchCtc(installed.first, installed.second, installed.third)
        try {
            val samples = WavPcm.readMonoFloats16k(copyAsset("sample-record.wav"))
            assertTrue("o WAV de teste está vazio", samples.isNotEmpty())
            val simple = engine.recognize(samples)
            android.util.Log.i(
                "LsbCtcTest",
                "elapsedMs=${System.currentTimeMillis() - started} words=${simple.size} text=${simple.joinToString(" ") { it.text }}",
            )
            assertTrue("o motor não devolveu palavras no áudio de teste", simple.isNotEmpty())
            assertMonotonic(simple)
        } finally {
            engine.close()
        }
    }

    private fun assertMonotonic(words: List<HeardWord>) {
        words.forEachIndexed { index, word ->
            assertTrue("intervalo inválido em ${word.text}", word.endMs > word.startMs)
            if (index > 0) {
                assertTrue("tempos fora de ordem", word.startMs >= words[index - 1].startMs)
            }
        }
    }

    private fun copyAsset(name: String): File {
        val ctx = InstrumentationRegistry.getInstrumentation().context
        val out = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, name)
        ctx.assets.open(name).use { input ->
            out.outputStream().use { output -> input.copyTo(output) }
        }
        return out
    }
}
