package pt.lersembarreiras.studio.audio

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamingWavWriterTest {
    @Test
    fun writes48k16bitMonoWithCorrectDurationAndSize() {
        val file = File.createTempFile("orig", ".wav")
        val sampleRate = 48_000
        val seconds = 2
        val frames = sampleRate * seconds
        StreamingWavWriter(file, sampleRate, 16, 1).use { writer ->
            val chunk = ByteArray(2048)
            var remaining = frames * 2
            while (remaining > 0) {
                val n = minOf(chunk.size, remaining)
                writer.write(chunk, 0, n)
                remaining -= n
            }
        }
        val props = StreamingWavWriter.inspect(file)
        assertEquals(48_000, props.sampleRate)
        assertEquals(16, props.bitsPerSample)
        assertEquals(1, props.channels)
        assertEquals(2_000L, props.durationMs)
        assertEquals(44L + frames * 2, props.fileBytes)
        file.delete()
    }

    @Test
    fun writes24bitHeaderWithoutBufferingWholeTake() {
        val file = File.createTempFile("24bit", ".wav")
        StreamingWavWriter(file, 48_000, 24, 1).use { writer ->
            val chunk = ByteArray(3 * 1024)
            repeat(40) { writer.write(chunk, 0, chunk.size) }
        }
        val props = StreamingWavWriter.inspect(file)
        assertEquals(24, props.bitsPerSample)
        assertEquals(48_000, props.sampleRate)
        assertTrue(props.dataBytes > 0)
        file.delete()
    }

    @Test
    fun threeMinuteStreamStaysOnDisk() {
        val file = File.createTempFile("long", ".wav")
        val sampleRate = 16_000
        val seconds = 180
        val totalBytes = sampleRate * seconds * 2
        StreamingWavWriter(file, sampleRate, 16, 1).use { writer ->
            val chunk = ByteArray(8192)
            var remaining = totalBytes
            while (remaining > 0) {
                val n = minOf(chunk.size, remaining)
                writer.write(chunk, 0, n)
                remaining -= n
            }
        }
        val props = StreamingWavWriter.inspect(file)
        assertEquals(180_000L, props.durationMs)
        assertEquals(44L + totalBytes, props.fileBytes)
        file.delete()
    }
}
