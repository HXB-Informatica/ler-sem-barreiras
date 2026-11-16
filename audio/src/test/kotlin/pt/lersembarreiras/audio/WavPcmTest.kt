package pt.lersembarreiras.audio

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WavPcmTest {
    @Test
    fun writesAndReadsRoundTrip() {
        val pcm = shortArrayOf(0, 1000, -1000, 32000, -32000)
        val file = File.createTempFile("lsb", ".wav")
        WavPcm.writeWav(file, pcm, WavPcm.WHISPER_SAMPLE_RATE)
        val (rate, loaded) = WavPcm.readPcm16Mono(file)
        assertEquals(WavPcm.WHISPER_SAMPLE_RATE, rate)
        assertEquals(pcm.toList(), loaded.toList())
        file.delete()
    }

    @Test
    fun resamplesPreservingApproximateDuration() {
        val sourceRate = 48_000
        val pcm = ShortArray(48_000) { 100 }
        val resampled = WavPcm.resampleTo16k(pcm, sourceRate)
        assertEquals(16_000, resampled.size)
        assertEquals(1000L, WavPcm.durationMs(resampled.size))
    }

    @Test
    fun convertsPcmToFloatRange() {
        val floats = WavPcm.pcm16ToFloat(shortArrayOf(0, 32767, -32768))
        assertEquals(0.0f, floats[0], 0.0001f)
        assertTrue(floats[1] > 0.9f)
        assertTrue(floats[2] < -0.9f)
    }
}
