package pt.lersembarreiras.teacher

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pt.lersembarreiras.audio.AudioImporter
import pt.lersembarreiras.audio.AudioRecorder
import pt.lersembarreiras.audio.ContentPlayer
import pt.lersembarreiras.audio.FileAudioSource
import pt.lersembarreiras.audio.WaveformExtractor
import pt.lersembarreiras.audio.WaveformPeaks
import pt.lersembarreiras.core.model.PlaybackSnapshot
import pt.lersembarreiras.core.model.ReadingContent
import pt.lersembarreiras.core.model.SyncStatus
import pt.lersembarreiras.core.model.Transcription
import pt.lersembarreiras.data.ContentRepository
import pt.lersembarreiras.editor.IntervalEdge
import pt.lersembarreiras.editor.SyncEditor
import pt.lersembarreiras.editor.SyncEditorState
import pt.lersembarreiras.shared.AlignmentEngine
import pt.lersembarreiras.shared.AlignmentRequest
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
    val editor: SyncEditorState = SyncEditorState(),
    val waveform: WaveformPeaks? = null,
    val waveformLoading: Boolean = false,
    val playbackSpeed: Float = 1f,
    val playingWordIndex: Int = -1,
    val playWordOnly: Boolean = false,
)

class TeacherEditViewModel(
    private val contentId: String?,
    private val repository: ContentRepository,
    private val recorder: AudioRecorder,
    private val importer: AudioImporter,
    private val player: ContentPlayer,
    private val speechEngine: SpeechRecognitionEngine,
    private val aligner: AlignmentEngine,
) : ViewModel() {
    private val _state = MutableStateFlow(TeacherEditUiState())
    val state: StateFlow<TeacherEditUiState> = _state.asStateFlow()
    private var syncJob: Job? = null
    private var cancelRequested = false
    private val editor = SyncEditor()
    private var playUntilMs: Long? = null

    init {
        viewModelScope.launch { load() }
        viewModelScope.launch {
            player.playbackState().collect { snapshot ->
                val limit = playUntilMs
                if (limit != null && snapshot.playing && snapshot.positionMs >= limit) {
                    player.pause()
                    playUntilMs = null
                }
                val playingIndex = editor.playingWordIndex(snapshot.positionMs)
                _state.update {
                    it.copy(
                        playback = snapshot,
                        playingWordIndex = playingIndex,
                        playWordOnly = playUntilMs != null,
                    )
                }
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
        val editorState = editor.load(content.words, content.durationMs)
        _state.update {
            it.copy(
                content = content,
                title = content.title,
                text = content.text,
                hasAudio = repository.audioFile(content) != null,
                loading = false,
                editor = editorState,
            )
        }
        refreshWaveform()
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
            val temp = File(repository.contentDirectory(content.id), "recording-${System.currentTimeMillis()}.wav")
            try {
                val recorded = recorder.stopToFile(temp)
                if (recorded.pcm16k.isEmpty()) {
                    temp.delete()
                    _state.update {
                        it.copy(
                            recording = false,
                            error = "A gravação está vazia. Toque em Gravar, fale para o microfone e depois em Parar gravação.",
                        )
                    }
                    return@launch
                }
                val relative = repository.saveAudio(content.id, recorded.file)
                val updated = currentContent().copy(
                    audioRelativePath = relative,
                    durationMs = recorded.durationMs,
                    syncStatus = SyncStatus.NONE,
                )
                repository.save(updated)
                val stored = repository.audioFile(updated)
                    ?: error("O áudio foi gravado, mas não ficou disponível para reprodução.")
                player.load(stored)
                editor.load(updated.words, updated.durationMs)
                _state.update {
                    it.copy(
                        content = updated,
                        recording = false,
                        hasAudio = true,
                        error = null,
                        savedMessage = "Narração gravada.",
                        editor = editor.snapshot,
                    )
                }
                refreshWaveform()
            } catch (error: Exception) {
                recorder.cancel()
                _state.update {
                    it.copy(
                        recording = false,
                        error = friendlyAudioError(error),
                    )
                }
            } finally {
                if (temp.exists()) temp.delete()
            }
        }
    }

    fun importAudio(uri: Uri) {
        val content = _state.value.content ?: return
        viewModelScope.launch {
            val temp = File(repository.contentDirectory(content.id), "import-${System.currentTimeMillis()}.wav")
            try {
                val imported = importer.importToWav(uri, temp)
                val relative = repository.saveAudio(content.id, imported.file)
                val updated = currentContent().copy(
                    audioRelativePath = relative,
                    durationMs = imported.durationMs,
                    syncStatus = SyncStatus.NONE,
                )
                repository.save(updated)
                val stored = repository.audioFile(updated)
                    ?: error("O áudio foi importado, mas não ficou disponível para reprodução.")
                player.load(stored)
                editor.load(updated.words, updated.durationMs)
                _state.update {
                    it.copy(
                        content = updated,
                        hasAudio = true,
                        savedMessage = "Áudio importado.",
                        error = null,
                        editor = editor.snapshot,
                    )
                }
                refreshWaveform()
            } catch (error: Exception) {
                _state.update { it.copy(error = "Falha na importação: ${friendlyAudioError(error)}") }
            } finally {
                if (temp.exists()) temp.delete()
            }
        }
    }

    fun togglePlayback() {
        playUntilMs = null
        if (player.isPlaying) player.pause() else player.play()
    }

    fun restartPlayback() {
        playUntilMs = null
        player.restart()
    }

    fun playAll() {
        playUntilMs = null
        player.setSpeed(_state.value.playbackSpeed)
        if (player.positionMs <= 0 || player.positionMs >= player.durationMs - 40) {
            player.seekTo(0)
        }
        player.play()
    }

    fun playSelectedWord() {
        val word = editor.snapshot.selected ?: return
        val start = word.startMs
        val end = word.endMs
        if (start == null || end == null) {
            _state.update { it.copy(error = "Atribua início e fim à palavra antes de a ouvir.") }
            return
        }
        playUntilMs = end
        player.setSpeed(_state.value.playbackSpeed)
        player.seekTo(start)
        player.play()
    }

    fun setPlaybackSpeed(speed: Float) {
        val value = when (speed) {
            0.5f, 0.75f, 1f -> speed
            else -> 1f
        }
        player.setSpeed(value)
        _state.update { it.copy(playbackSpeed = value) }
    }

    fun selectWord(index: Int) {
        _state.update { it.copy(editor = editor.select(index), error = null) }
    }

    fun setSelectedInterval(startMs: Long, endMs: Long) {
        val index = editor.snapshot.selectedIndex
        if (index < 0) return
        persistEditor { editor.setInterval(index, startMs, endMs) }
    }

    fun nudgeSelected(edge: IntervalEdge, deltaMs: Long) {
        val index = editor.snapshot.selectedIndex
        if (index < 0) return
        persistEditor { editor.nudge(index, edge, deltaMs) }
    }

    fun markSelectedStart() {
        val index = editor.snapshot.selectedIndex
        if (index < 0) return
        persistEditor { editor.markStart(index, player.positionMs) }
    }

    fun markSelectedEnd() {
        val index = editor.snapshot.selectedIndex
        if (index < 0) return
        persistEditor { editor.markEnd(index, player.positionMs) }
    }

    fun verifySelected() {
        val index = editor.snapshot.selectedIndex
        if (index < 0) return
        persistEditor { editor.verify(index) }
    }

    fun undoEdit() {
        if (!editor.snapshot.canUndo) return
        persistEditor { editor.undo() }
    }

    fun redoEdit() {
        if (!editor.snapshot.canRedo) return
        persistEditor { editor.redo() }
    }

    fun seekTo(positionMs: Long) {
        player.seekTo(positionMs)
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
                val pcm = FileAudioSource(importer, audio).readPcm16kMono()
                _state.update { it.copy(syncMessage = "A reconhecer a narração no tablet…", syncProgress = 0.1f) }
                val transcription = speechEngine.transcribe(
                    pcm16kMono = pcm.samples,
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
                val alignment = aligner.align(
                    AlignmentRequest(
                        sourceText = content.text,
                        transcription = Transcription(
                            words = transcription.words,
                            usedTokenTimestamps = transcription.usedTokenTimestamps,
                        ),
                        audioDurationMs = pcm.durationMs,
                        previousWords = content.words,
                    ),
                )
                val updated = repository.updateWords(
                    content = content,
                    words = alignment.words,
                    status = alignment.status,
                    durationMs = pcm.durationMs,
                )
                editor.load(updated.words, updated.durationMs)
                _state.update {
                    it.copy(
                        content = updated,
                        syncing = false,
                        syncProgress = 1f,
                        usedTokenTimestamps = transcription.usedTokenTimestamps,
                        editor = editor.snapshot,
                        syncMessage = when (alignment.status) {
                            SyncStatus.COMPLETED -> "Sincronização concluída."
                            SyncStatus.COMPLETED_WITH_GAPS ->
                                "Sincronização concluída com ${alignment.reviewCount} palavras a rever."
                            else -> "Não foi possível alinhar o texto com confiança suficiente."
                        },
                    )
                }
                refreshWaveform()
            } catch (error: Exception) {
                val failed = content.copy(syncStatus = SyncStatus.FAILED)
                repository.save(failed)
                _state.update {
                    it.copy(
                        content = failed,
                        syncing = false,
                        error = friendlyAudioError(error),
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
        editor.select(index)
        persistEditor { editor.setInterval(index, startMs, endMs) }
    }

    fun save() {
        viewModelScope.launch {
            val updated = repository.save(currentContent())
            _state.update { it.copy(content = updated, savedMessage = "Conteúdo guardado neste tablet.") }
        }
    }

    private fun persistEditor(action: () -> SyncEditorState) {
        viewModelScope.launch {
            val editorState = action()
            val content = currentContent()
            val updated = repository.updateWords(
                content = content,
                words = editorState.words,
                status = editorState.status,
                durationMs = content.durationMs.coerceAtLeast(editorState.durationMs),
            )
            _state.update {
                it.copy(
                    content = updated,
                    editor = editorState,
                    savedMessage = "Alteração guardada neste tablet.",
                    error = null,
                )
            }
        }
    }

    private fun refreshWaveform() {
        val content = _state.value.content ?: return
        val audio = repository.audioFile(content)
        if (audio == null) {
            _state.update { it.copy(waveform = null, waveformLoading = false) }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(waveformLoading = true) }
            val peaks = withContext(Dispatchers.Default) {
                runCatching { WaveformExtractor.fromWav(audio) }.getOrNull()
            }
            _state.update {
                it.copy(
                    waveform = peaks,
                    waveformLoading = false,
                    error = if (peaks == null) "Não foi possível desenhar a forma de onda." else it.error,
                )
            }
        }
    }

    private fun currentContent(): ReadingContent {
        val current = _state.value.content ?: error("Conteúdo ainda não carregado.")
        return current.copy(title = _state.value.title.ifBlank { "Sem título" }, text = _state.value.text)
    }

    private fun workingAudio(content: ReadingContent): File {
        return File(repository.contentDirectory(content.id), ContentRepository.AUDIO_FILE)
    }

    private fun friendlyAudioError(error: Throwable): String {
        val details = error.message.orEmpty()
        return when {
            details.contains("ENOENT") || details.contains("No such file", ignoreCase = true) ->
                "Não foi possível guardar ou abrir o áudio. Tente gravar outra vez."
            else -> error.message ?: "Ocorreu um erro ao tratar o áudio."
        }
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
            aligner: AlignmentEngine,
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
