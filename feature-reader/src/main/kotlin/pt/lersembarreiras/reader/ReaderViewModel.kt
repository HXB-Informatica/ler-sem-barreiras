package pt.lersembarreiras.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pt.lersembarreiras.alignment.TimeValidator
import pt.lersembarreiras.audio.ContentPlayer
import pt.lersembarreiras.core.model.PlaybackSnapshot
import pt.lersembarreiras.core.model.PresentationMode
import pt.lersembarreiras.core.model.ReaderSettings
import pt.lersembarreiras.core.model.ReadingContent
import pt.lersembarreiras.core.model.SyncStatus
import pt.lersembarreiras.data.ContentRepository

data class ReaderUiState(
    val content: ReadingContent? = null,
    val settings: ReaderSettings = ReaderSettings(),
    val playback: PlaybackSnapshot = PlaybackSnapshot(0, 0, playing = false, ended = false),
    val currentWordIndex: Int = -1,
    val missingAudio: Boolean = false,
)

class ReaderListViewModel(
    repository: ContentRepository,
) : ViewModel() {
    val contents = repository.observeAll()
        .map { list -> list.filter { it.syncStatus == SyncStatus.COMPLETED || it.syncStatus == SyncStatus.COMPLETED_WITH_GAPS } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    companion object {
        fun factory(repository: ContentRepository) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = ReaderListViewModel(repository) as T
        }
    }
}

class ReaderViewModel(
    private val contentId: String,
    private val repository: ContentRepository,
    private val player: ContentPlayer,
) : ViewModel() {
    private val _state = MutableStateFlow(ReaderUiState())
    val state: StateFlow<ReaderUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val content = repository.get(contentId)
            val audio = content?.let { repository.audioFile(it) }
            if (audio != null) {
                player.load(audio)
            }
            _state.update {
                it.copy(content = content, missingAudio = audio == null)
            }
        }
        viewModelScope.launch {
            player.playbackState().collect { snapshot ->
                val words = _state.value.content?.words.orEmpty()
                val index = TimeValidator.currentWordIndex(words, snapshot.positionMs)
                _state.update { it.copy(playback = snapshot, currentWordIndex = index) }
            }
        }
    }

    fun togglePlayback() {
        if (_state.value.missingAudio) return
        if (player.isPlaying) player.pause() else player.play()
    }

    fun restart() {
        player.restart()
    }

    fun setMode(mode: PresentationMode) {
        _state.update { it.copy(settings = it.settings.copy(presentationMode = mode)) }
    }

    fun cycleFont() {
        _state.update {
            val next = when {
                it.settings.fontScale < 1.3f -> 1.4f
                it.settings.fontScale < 1.8f -> 1.9f
                else -> 1.1f
            }
            it.copy(settings = it.settings.copy(fontScale = next))
        }
    }

    fun toggleContrast() {
        _state.update { it.copy(settings = it.settings.copy(highContrast = !it.settings.highContrast)) }
    }

    override fun onCleared() {
        super.onCleared()
        player.release()
    }

    companion object {
        fun factory(
            contentId: String,
            repository: ContentRepository,
            player: ContentPlayer,
        ) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return ReaderViewModel(contentId, repository, player) as T
            }
        }
    }
}
