package pt.lersembarreiras.shared

import kotlinx.coroutines.flow.Flow
import pt.lersembarreiras.core.model.AlignmentResult
import pt.lersembarreiras.core.model.ContentPackage
import pt.lersembarreiras.core.model.Pcm16kAudio
import pt.lersembarreiras.core.model.PlaybackSnapshot
import pt.lersembarreiras.core.model.ReadingContent
import pt.lersembarreiras.core.model.SyncStatus
import pt.lersembarreiras.core.model.Transcription
import pt.lersembarreiras.core.model.WordTiming

data class AlignmentRequest(
    val sourceText: String,
    val transcription: Transcription,
    val audioDurationMs: Long,
    val previousWords: List<WordTiming> = emptyList(),
)

/**
 * Alinha o texto original com uma transcrição já obtida.
 * Não executa reconhecimento de fala.
 */
interface AlignmentEngine {
    fun align(request: AlignmentRequest): AlignmentResult
}

/** Fonte de PCM 16 kHz mono para o alinhamento, independente da captura. */
interface AudioSource {
    suspend fun readPcm16kMono(): Pcm16kAudio
}

interface PlaybackController {
    val positionMs: Long
    val durationMs: Long
    val isPlaying: Boolean
    fun load(path: String)
    fun play()
    fun pause()
    fun restart()
    fun seekTo(positionMs: Long)
    fun setSpeed(speed: Float) {}
    fun release()
    fun playbackState(): Flow<PlaybackSnapshot>
}

interface AlignmentRepository {
    fun observeAll(): Flow<List<ReadingContent>>
    suspend fun get(id: String): ReadingContent?
    suspend fun save(content: ReadingContent): ReadingContent
    suspend fun updateWords(
        content: ReadingContent,
        words: List<WordTiming>,
        status: SyncStatus,
        durationMs: Long,
    ): ReadingContent
    suspend fun delete(id: String)
}

interface ProjectSerializer {
    fun encode(content: ReadingContent): String
    fun decode(raw: String): ContentPackage
    fun toPackage(content: ReadingContent): ContentPackage
    fun fromPackage(pkg: ContentPackage, audioRelativePath: String?): ReadingContent
}
