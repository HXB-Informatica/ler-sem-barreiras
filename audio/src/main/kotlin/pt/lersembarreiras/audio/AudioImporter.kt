package pt.lersembarreiras.audio

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AudioImporter(private val context: Context) {
    suspend fun importToWav(source: Uri, destination: File): RecordedAudio = withContext(Dispatchers.IO) {
        destination.parentFile?.mkdirs()
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(context, source, null)
            val track = (0 until extractor.trackCount).firstOrNull { index ->
                extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: error("O ficheiro selecionado não contém áudio.")
            extractor.selectTrack(track)
            val format = extractor.getTrackFormat(track)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: error("Tipo de áudio desconhecido.")
            if (mime == MediaFormat.MIMETYPE_AUDIO_RAW) {
                val pcm = readRaw(extractor, format)
                val sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                val mono16k = WavPcm.resampleTo16k(pcm, sampleRate)
                WavPcm.writeWav(destination, mono16k)
                return@withContext RecordedAudio(destination, WavPcm.durationMs(mono16k.size), mono16k)
            }
            val codec = MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()
            val pcmChunks = mutableListOf<ShortArray>()
            var sampleRate = if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            } else {
                WavPcm.WHISPER_SAMPLE_RATE
            }
            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false
            while (!outputDone) {
                if (!inputDone) {
                    val inputIndex = codec.dequeueInputBuffer(10_000)
                    if (inputIndex >= 0) {
                        val input = codec.getInputBuffer(inputIndex)!!
                        val sampleSize = extractor.readSampleData(input, 0)
                        if (sampleSize < 0) {
                            codec.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(inputIndex, 0, sampleSize, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                val outputIndex = codec.dequeueOutputBuffer(info, 10_000)
                when {
                    outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val outFormat = codec.outputFormat
                        if (outFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                            sampleRate = outFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        }
                    }
                    outputIndex >= 0 -> {
                        val output = codec.getOutputBuffer(outputIndex)
                        if (output != null && info.size > 0) {
                            output.position(info.offset)
                            output.limit(info.offset + info.size)
                            pcmChunks += bufferToPcm(output, info.size)
                        }
                        codec.releaseOutputBuffer(outputIndex, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                            outputDone = true
                        }
                    }
                }
            }
            codec.stop()
            codec.release()
            val pcm = WavPcm.appendPcm(pcmChunks)
            val mono16k = WavPcm.resampleTo16k(pcm, sampleRate)
            WavPcm.writeWav(destination, mono16k)
            RecordedAudio(destination, WavPcm.durationMs(mono16k.size), mono16k)
        } finally {
            extractor.release()
        }
    }

    fun loadPcm16k(file: File): RecordedAudio {
        val (sampleRate, pcm) = WavPcm.readPcm16Mono(file)
        val mono16k = WavPcm.resampleTo16k(pcm, sampleRate)
        return RecordedAudio(file, WavPcm.durationMs(mono16k.size, WavPcm.WHISPER_SAMPLE_RATE), mono16k)
    }

    private fun readRaw(extractor: MediaExtractor, format: MediaFormat): ShortArray {
        val chunks = mutableListOf<ShortArray>()
        val buffer = ByteBuffer.allocate(4096)
        while (true) {
            buffer.clear()
            val size = extractor.readSampleData(buffer, 0)
            if (size < 0) break
            buffer.limit(size)
            chunks += bufferToPcm(buffer, size)
            extractor.advance()
        }
        val channels = if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
            format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        } else {
            1
        }
        val pcm = WavPcm.appendPcm(chunks)
        return if (channels <= 1) pcm else toMono(pcm, channels)
    }

    private fun bufferToPcm(buffer: ByteBuffer, size: Int): ShortArray {
        val ordered = buffer.order(ByteOrder.LITTLE_ENDIAN)
        val shorts = ShortArray(size / 2)
        ordered.asShortBuffer().get(shorts)
        return shorts
    }

    private fun toMono(pcm: ShortArray, channels: Int): ShortArray {
        val frames = pcm.size / channels
        return ShortArray(frames) { frame ->
            var sum = 0
            for (channel in 0 until channels) {
                sum += pcm[frame * channels + channel]
            }
            (sum / channels).toShort()
        }
    }
}
