package pt.lersembarreiras.audio

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WaveformExtractorTest {
    @Test
    fun longRecordingYieldsFixedPeakCountWithoutReturningPcm() {
        val seconds = 12
        val pcm = ShortArray(WavPcm.WHISPER_SAMPLE_RATE * seconds) { index ->
            if (index % 400 == 0) 20_000 else 200
        }
        val file = File.createTempFile("long-wave", ".wav")
        WavPcm.writeWav(file, pcm)
        val peaks = WaveformExtractor.fromWav(file, barCount = 200)
        assertEquals(200, peaks.peaks.size)
        assertEquals(12_000L, peaks.durationMs)
        assertTrue(peaks.peaks.any { it > 0.2f })
        assertTrue(file.length() > pcm.size.toLong() * 2)
        file.delete()
    }

    @Test
    fun samplesAndWavProduceSameBarCount() {
        val pcm = ShortArray(8_000) { 3_000 }
        val fromSamples = WaveformExtractor.fromSamples(pcm, WavPcm.WHISPER_SAMPLE_RATE, 80)
        val file = File.createTempFile("wave", ".wav")
        WavPcm.writeWav(file, pcm)
        val fromFile = WaveformExtractor.fromWav(file, 80)
        assertEquals(80, fromSamples.peaks.size)
        assertEquals(80, fromFile.peaks.size)
        file.delete()
    }
}
