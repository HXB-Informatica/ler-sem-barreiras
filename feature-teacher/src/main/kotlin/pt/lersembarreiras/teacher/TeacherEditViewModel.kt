package pt.lersembarreiras.teacher

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import java.io.File
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pt.lersembarreiras.alignment.WordAligner
import pt.lersembarreiras.audio.AudioImporter
import pt.lersembarreiras.audio.AudioRecorder
import pt.lersembarreiras.audio.ContentPlayer
import pt.lersembarreiras.audio.PlaybackSnapshot
import pt.lersembarreiras.core.model.ReadingContent
import pt.lersembarreiras.core.model.SyncStatus
import pt.lersembarreiras.core.model.WordTiming
import pt.lersembarreiras.data.ContentRepository
import pt.lersembarreiras.speech.SpeechRecognitionEngine

data class TeacherEditUiState(
    val content: ReadingContent? = null,
    val title: String = "",
    val text: String = "",
    val recording: Boolean = false,
    val hasAudio: Boolean = false,
    val playback: PlaybackSnapshot? = null,
    val loading: Boolean = true,
    val syncing: Boolean = false,
    val syncProgress: Float = 0f,
    val syncMessage: String? = null,
    val error: String? = null,
    val savedMessage: String? = null,
    val usedTokenTimestamps: Boolean? = null,
)

class TeacherEditViewModel(
    private val contentId: String?,
    private val repository: ContentRepository,
    private val recorder: AudioRecorder,
    private val importer: AudioImporter,
    private val player: ContentPlayer,
    private val speechEngine: SpeechRecognitionEngine,
    private val aligner: WordAligner,
) : ViewModel() {
    private val _state = MutableStateFlow(TeacherEditUiState())
    val state: StateFlow<TeacherEditUiState> = _state.asStateFlow()
    private var syncJob: Job? = null
    private var cancelRequested = false

    init {
        viewModelScope.launch { load() }
        viewModelScope.launch {
            player.playbackState().collect { snapshot ->
                _state.update { it.copy(playback = snapshot) }
            }
        }
    }

    private suspend fun load() {
        val content = if (contentId == null) {
            repository.createDraft()
        } else {
            repository.get(contentId) ?: repository.createDraft()
        }
        repository.audioFile(content)?.let { player.load(it) }
        _state.update {
            it.copy(
                content = content,
                title = content.title,
                text = content.text,
                hasAudio = repository.audioFile(content) != null,
                loading = false,
            )
        }
    }

    fun updateTitle(value: String) {
        _state.update { it.copy(title = value, savedMessage = null) }
    }

    fun updateText(value: String) {
        _state.update { it.copy(text = value, savedMessage = null) }
    }

    fun startRecording() {
        runCatching { recorder.start() }
            .onSuccess { _state.update { it.copy(recording = true, error = null) } }
            .onFailure { error ->
                _state.update { it.copy(error = "Não foi possível gravar: ${error.message}") }
            }
    }

    fun stopRecording() {
        val content = _state.value.content ?: return
        viewModelScope.launch {
            try {
                val recorded = recorder.stopToFile(workingAudio(content))
                val relative = repository.saveAudio(content.id, recorded.file)
                val updated = currentContent().copy(
                    audioRelativePath = relative,
                    durationMs = recorded.durationMs,
                    syncStatus = SyncStatus.NONE,
                )
                repository.save(updated)
                player.load(recorded.file)
                _state.update {
                    it.copy(
                        content = updated,
                        recording = false,
                        hasAudio = true,
                        savedMessage = "Narração gravada.",
                    )
                }
            } catch (error: Exception) {
                recorder.cancel()
                _state.update { it.copy(recording = false, error = error.message) }
            }
        }
    }

    fun importAudio(uri: Uri) {
        val content = _state.value.content ?: return
        viewModelScope.launch {
            try {
                val dest = workingAudio(content)
                val imported = importer.importToWav(uri, dest)
                val relative = repository.saveAudio(content.id, imported.file)
                val updated = currentContent().copy(
                    audioRelativePath = relative,
                    durationMs = imported.durationMs,
                    syncStatus = SyncStatus.NONE,
                )
                repository.save(updated)
                player.load(imported.file)
                _state.update {
                    it.copy(content = updated, hasAudio = true, savedMessage = "Áudio importado.", error = null)
                }
            } catch (error: Exception) {
                _state.update { it.copy(error = "Falha na importação: ${error.message}") }
            }
        }
    }

    fun togglePlayback() {
        if (player.isPlaying) player.pause() else player.play()
    }

    fun restartPlayback() {
        player.restart()
    }

    fun playFrom(word: WordTiming) {
        word.startMs?.let { player.seekTo(it); player.play() }
    }

    fun synchronize() {
        val content = currentContent()
        if (content.text.isBlank()) {
            _state.update { it.copy(error = "Escreva o texto antes de sincronizar.") }
            return
        }
        val audio = repository.audioFile(content)
        if (audio == null) {
            _state.update { it.copy(error = "Grave ou importe a narração antes de sincronizar.") }
            return
        }
        cancelRequested = false
        syncJob?.cancel()
        syncJob = viewModelScope.launch {
            _state.update {
                it.copy(
                    syncing = true,
                    syncProgress = 0.02f,
                    syncMessage = "A preparar o motor de reconhecimento local…",
                    error = null,
                )
            }
            try {
                repository.save(content.copy(syncStatus = SyncStatus.IN_PROGRESS))
                val pcm = importer.loadPcm16k(audio)
                _state.update { it.copy(syncMessage = "A reconhecer a narração no tablet…", syncProgress = 0.1f) }
                val transcription = speechEngine.transcribe(
                    pcm16kMono = pcm.pcm16k,
                    language = "pt",
                    prompt = content.text.take(400),
                    onProgress = { value ->
                        _state.update {
                            it.copy(syncProgress = 0.1f + value * 0.7f)
                        }
                    },
                    isCancelled = { cancelRequested },
                )
                if (transcription.cancelled) {
                    _state.update {
                        it.copy(syncing = false, syncMessage = "Sincronização cancelada.", content = content.copy(syncStatus = SyncStatus.CANCELLED))
                    }
                    repository.save(content.copy(syncStatus = SyncStatus.CANCELLED))
                    return@launch
                }
                if (transcription.error != null) {
                    throw IllegalStateException(transcription.error)
                }
                _state.update { it.copy(syncMessage = "A alinhar o texto original com a voz…", syncProgress = 0.88f) }
                val alignment = aligner.align(content.text, transcription.words, pcm.durationMs)
                val updated = repository.updateWords(
                    content = content,
                    words = alignment.words,
                    status = alignment.status,
                    durationMs = pcm.durationMs,
                )
                _state.update {
                    it.copy(
                        content = updated,
                        syncing = false,
                        syncProgress = 1f,
                        usedTokenTimestamps = transcription.usedTokenTimestamps,
                        syncMessage = when (alignment.status) {
                            SyncStatus.COMPLETED -> "Sincronização concluída."
                            SyncStatus.COMPLETED_WITH_GAPS ->
                                "Sincronização concluída com ${alignment.reviewCount} palavras a rever."
                            else -> "Não foi possível alinhar o texto com confiança suficiente."
                        },
                    )
                }
            } catch (error: Exception) {
                val failed = content.copy(syncStatus = SyncStatus.FAILED)
                repository.save(failed)
                _state.update {
                    it.copy(
                        content = failed,
                        syncing = false,
                        error = error.message ?: "Falha na sincronização local.",
                        syncMessage = null,
                    )
                }
            }
        }
    }

    fun cancelSync() {
        cancelRequested = true
        speechEngine.cancel()
    }

    fun adjustWord(index: Int, startMs: Long, endMs: Long) {
        val content = currentContent()
        if (index !in content.words.indices) return
        val words = content.words.toMutableList()
        val current = words[index]
        words[index] = current.copy(
            startMs = startMs.coerceAtLeast(0),
            endMs = endMs.coerceAtLeast(startMs),
            manuallyAdjusted = true,
            needsReview = false,
            confidence = 1f,
        )
        viewModelScope.launch {
            val status = if (words.any { it.needsReview || !it.isTimed }) {
                SyncStatus.COMPLETED_WITH_GAPS
            } else {
                SyncStatus.COMPLETED
            }
            val updated = repository.updateWords(content, words, status)
            _state.update { it.copy(content = updated, savedMessage = "Tempo da palavra atualizado.") }
        }
    }

    fun save() {
        viewModelScope.launch {
            val updated = repository.save(currentContent())
            _state.update { it.copy(content = updated, savedMessage = "Conteúdo guardado neste tablet.") }
        }
    }

    private fun currentContent(): ReadingContent {
        val current = _state.value.content ?: error("Conteúdo ainda não carregado.")
        return current.copy(title = _state.value.title.ifBlank { "Sem título" }, text = _state.value.text)
    }

    private fun workingAudio(content: ReadingContent): File {
        return File(repository.contentDirectory(content.id), ContentRepository.AUDIO_FILE)
    }

    override fun onCleared() {
        super.onCleared()
        player.release()
        if (recorder.isRecording) recorder.cancel()
    }

    companion object {
        fun factory(
            contentId: String?,
            repository: ContentRepository,
            recorder: AudioRecorder,
            importer: AudioImporter,
            player: ContentPlayer,
            speechEngine: SpeechRecognitionEngine,
            aligner: WordAligner,
        ) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return TeacherEditViewModel(
                    contentId,
                    repository,
                    recorder,
                    importer,
                    player,
                    speechEngine,
                    aligner,
                ) as T
            }
        }
    }
}
