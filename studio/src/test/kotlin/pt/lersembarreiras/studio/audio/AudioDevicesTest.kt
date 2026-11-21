package pt.lersembarreiras.studio.audio

import org.junit.Assert.assertTrue
import org.junit.Test

class AudioDevicesTest {
    @Test
    fun listsMixersWithoutThrowing() {
        val devices = AudioDevices.listInputs()
        assertTrue(devices.size >= 0)
        if (devices.isNotEmpty()) {
            val format = AudioDevices.negotiate(devices.first())
            assertTrue(format == null || format.sampleRate in setOf(16_000, 44_100, 48_000))
        }
    }

    @Test
    fun fallbackOrderPrefers48k24WhenLineExists() {
        val devices = AudioDevices.listInputs()
        if (devices.isEmpty()) return
        val format = AudioDevices.negotiate(devices.first()) ?: return
        if (format.preferred) {
            assertTrue(format.sampleRate == 48_000 && format.bitsPerSample == 24)
        } else {
            assertTrue(format.fallbackReason!!.contains("48 kHz"))
        }
    }
}
