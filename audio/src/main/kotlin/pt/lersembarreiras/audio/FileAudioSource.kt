package pt.lersembarreiras.audio

import java.io.File
import pt.lersembarreiras.core.model.Pcm16kAudio
import pt.lersembarreiras.shared.AudioSource

class FileAudioSource(
    private val importer: AudioImporter,
    private val file: File,
) : AudioSource {
    override suspend fun readPcm16kMono(): Pcm16kAudio {
        val recorded = importer.loadPcm16k(file)
        return Pcm16kAudio(
            samples = recorded.pcm16k,
            sampleRate = WavPcm.WHISPER_SAMPLE_RATE,
            durationMs = recorded.durationMs,
        )
    }
}
