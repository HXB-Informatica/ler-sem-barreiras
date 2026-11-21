package pt.lersembarreiras.studio.audio

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DesktopWaveformTest {
    @Test
    fun longRecordingYieldsFixedBarCount() {
        val file = File.createTempFile("wave", ".wav")
        val seconds = 12
        val rate = 16_000
        StreamingWavWriter(file, rate, 16, 1).use { writer ->
            writer.write(ByteArray(rate * seconds * 2), 0, rate * seconds * 2)
        }
        val peaks = DesktopWaveform.fromWav(file, barCount = 200)
        assertEquals(200, peaks.peaks.size)
        assertEquals(12_000L, peaks.durationMs)
        assertTrue(peaks.peaks.all { it in 0f..1f })
        file.delete()
    }
}
