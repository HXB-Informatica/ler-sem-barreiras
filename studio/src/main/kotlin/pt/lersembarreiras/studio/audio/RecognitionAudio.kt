package pt.lersembarreiras.studio.audio

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.roundToInt

object RecognitionAudio {
    const val TARGET_RATE = 16_000

    fun writeMono16k(source: File, destination: File) {
        val props = StreamingWavWriter.inspect(source)
        destination.parentFile?.mkdirs()
        RandomAccessFile(source, "r").use { raf ->
            val dataOffset = findDataOffset(raf)
            val bytesPerSample = (props.bitsPerSample / 8).coerceAtLeast(1)
            val frameSize = bytesPerSample * props.channels.coerceAtLeast(1)
            val totalFrames = if (frameSize == 0) 0 else (props.dataBytes / frameSize).toInt()
            val ratio = props.sampleRate.toDouble() / TARGET_RATE
            val outFrames = (totalFrames / ratio).roundToInt().coerceAtLeast(1)
            val frame = ByteArray(frameSize.coerceAtLeast(2))
            val sampleBytes = ByteArray(2)
            val sampleBuffer = ByteBuffer.wrap(sampleBytes).order(ByteOrder.LITTLE_ENDIAN)
            StreamingWavWriter(destination, TARGET_RATE, 16, 1).use { writer ->
                for (i in 0 until outFrames) {
                    val src = (i * ratio).toInt().coerceIn(0, (totalFrames - 1).coerceAtLeast(0))
                    raf.seek(dataOffset + src.toLong() * frameSize)
                    raf.readFully(frame)
                    sampleBuffer.clear()
                    sampleBuffer.putShort(downmixFrame(frame, props.channels, bytesPerSample))
                    writer.write(sampleBytes, 0, 2)
                }
            }
        }
    }

    private fun downmixFrame(frame: ByteArray, channels: Int, bytesPerSample: Int): Short {
        var sum = 0
        var offset = 0
        repeat(channels.coerceAtLeast(1)) {
            val sample = when {
                bytesPerSample >= 3 -> {
                    val v = (frame[offset + 2].toInt() shl 16) or
                        ((frame[offset + 1].toInt() and 0xFF) shl 8) or
                        (frame[offset].toInt() and 0xFF)
                    v shr 8
                }
                else -> {
                    val lo = frame[offset].toInt() and 0xFF
                    val hi = frame[offset + 1].toInt()
                    (hi shl 8) or lo
                }
            }
            sum += sample
            offset += bytesPerSample
        }
        return (sum / channels.coerceAtLeast(1)).coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
    }

    private fun findDataOffset(raf: RandomAccessFile): Long {
        raf.seek(12)
        val id = ByteArray(4)
        while (raf.filePointer + 8 <= raf.length()) {
            raf.readFully(id)
            val sizeBytes = ByteArray(4)
            raf.readFully(sizeBytes)
            val size = ByteBuffer.wrap(sizeBytes).order(ByteOrder.LITTLE_ENDIAN).int
            if (String(id, Charsets.US_ASCII) == "data") return raf.filePointer
            raf.skipBytes(size + size % 2)
        }
        error("Chunk data não encontrado.")
    }
}
