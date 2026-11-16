package pt.lersembarreiras.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AudioRecorder {
    private val recording = AtomicBoolean(false)
    private val chunks = mutableListOf<ShortArray>()
    private var recordThread: Thread? = null

    val isRecording: Boolean get() = recording.get()

    @SuppressLint("MissingPermission")
    fun start() {
        if (recording.getAndSet(true)) return
        chunks.clear()
        val minBuffer = AudioRecord.getMinBufferSize(
            WavPcm.WHISPER_SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        ).coerceAtLeast(WavPcm.WHISPER_SAMPLE_RATE / 2)
        val recorder = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            WavPcm.WHISPER_SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            minBuffer * 2,
        )
        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            recording.set(false)
            error("Não foi possível inicializar o microfone.")
        }
        recorder.startRecording()
        recordThread = thread(name = "lsb-recorder", isDaemon = true) {
            val buffer = ShortArray(minBuffer)
            try {
                while (recording.get()) {
                    val read = recorder.read(buffer, 0, buffer.size)
                    if (read > 0) {
                        synchronized(chunks) {
                            chunks += buffer.copyOf(read)
                        }
                    }
                }
            } finally {
                runCatching { recorder.stop() }
                recorder.release()
            }
        }
    }

    suspend fun stopToFile(file: File): RecordedAudio = withContext(Dispatchers.IO) {
        recording.set(false)
        recordThread?.join(2_000)
        recordThread = null
        val pcm = synchronized(chunks) { WavPcm.appendPcm(chunks.toList()) }
        chunks.clear()
        WavPcm.writeWav(file, pcm)
        RecordedAudio(
            file = file,
            durationMs = WavPcm.durationMs(pcm.size),
            pcm16k = pcm,
        )
    }

    fun cancel() {
        recording.set(false)
        recordThread?.join(1_000)
        recordThread = null
        synchronized(chunks) { chunks.clear() }
    }
}

data class RecordedAudio(
    val file: File,
    val durationMs: Long,
    val pcm16k: ShortArray,
)
