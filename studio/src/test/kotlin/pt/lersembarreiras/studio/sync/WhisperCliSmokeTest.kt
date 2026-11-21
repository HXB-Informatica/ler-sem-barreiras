package pt.lersembarreiras.studio.sync

import java.io.File
import org.junit.Assume.assumeTrue
import org.junit.Test
import pt.lersembarreiras.studio.audio.StreamingWavWriter

class WhisperCliSmokeTest {
    @Test
    fun silentAudioDoesNotCrashWhenBinaryIsPresent() {
        val install = WhisperRuntime.locate()
        assumeTrue("whisper-cli Windows ainda não está instalado.", install != null)
        val wav = File.createTempFile("silence", ".wav")
        StreamingWavWriter(wav, 16_000, 16, 1).use { writer ->
            writer.write(ByteArray(16_000 * 2), 0, 16_000 * 2)
        }
        val outcome = WhisperCliEngine().transcribe(wav, "pt", "Olá um dois três") { false }
        assert(!outcome.cancelled)
        wav.delete()
    }
}
