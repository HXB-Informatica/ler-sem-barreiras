package pt.lersembarreiras.studio.audio

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LevelMeterTest {
    @Test
    fun silenceIsNotClipping() {
        val buffer = ByteArray(256)
        val reading = LevelMeter.read(buffer, buffer.size, 16, 1)
        assertFalse(reading.clipping)
        assertTrue(reading.peak < 0.01f)
    }

    @Test
    fun saturated16BitIsClipping() {
        val buffer = ByteArray(40)
        for (i in buffer.indices step 2) {
            buffer[i] = 0xFF.toByte()
            buffer[i + 1] = 0x7F.toByte()
        }
        val reading = LevelMeter.read(buffer, buffer.size, 16, 1)
        assertTrue(reading.clipping)
        assertTrue(reading.peak > 0.9f)
    }
}
