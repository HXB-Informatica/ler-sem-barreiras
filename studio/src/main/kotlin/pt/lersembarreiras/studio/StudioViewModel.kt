package pt.lersembarreiras.studio

import java.io.File
import javax.sound.sampled.LineUnavailableException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import pt.lersembarreiras.core.model.ContentPackage
import pt.lersembarreiras.core.model.SyncStatus
import pt.lersembarreiras.core.packageformat.ContentPackageMapper
import pt.lersembarreiras.lsb.LsbArchive
import pt.lersembarreiras.lsb.LsbException
import pt.lersembarreiras.editor.IntervalEdge
import pt.lersembarreiras.editor.SyncEditor
import pt.lersembarreiras.editor.SyncEditorState
import pt.lersembarreiras.studio.audio.AudioDevice
import pt.lersembarreiras.studio.audio.DesktopAudioImport
import pt.lersembarreiras.studio.audio.DesktopWaveform
import pt.lersembarreiras.studio.audio.WaveformPeaks
import pt.lersembarreiras.studio.sync.StudioAlignmentService
import pt.lersembarreiras.studio.sync.MissingTranscriber
import pt.lersembarreiras.studio.audio.AudioDevices
import pt.lersembarreiras.studio.audio.DesktopPlayer
import pt.lersembarreiras.studio.audio.DesktopRecorder
import pt.lersembarreiras.studio.audio.InputMonitor
import pt.lersembarreiras.studio.audio.LevelReading
import pt.lersembarreiras.studio.audio.NegotiatedFormat
import pt.lersembarreiras.studio.audio.RecognitionAudio
import pt.lersembarreiras.studio.audio.RecorderState
import pt.lersembarreiras.studio.audio.StreamingWavWriter
import pt.lersembarreiras.studio.project.CaptureInfo
import pt.lersembarreiras.studio.project.StudioProjectFile
import pt.lersembarreiras.studio.project.StudioProjectStore

data class StudioUiState(
    val title: String = "Novo conteúdo",
    val text: String = "",
    val directory: File? = null,
    val devices: List<AudioDevice> = emptyList(),
    val selectedDeviceId: String? = null,
    val negotiated: NegotiatedFormat? = null,
    val recorderState: RecorderState = RecorderState.IDLE,
    val level: LevelReading = LevelReading(0f, 0f, false),
    val durationMs: Long = 0,
    val hasOriginal: Boolean = false,
    val hasRecognition: Boolean = false,
    val playing: Boolean = false,
    val playPositionMs: Long = 0,
    val playDurationMs: Long = 0,
    val capture: CaptureInfo? = null,
    val originalProps: String? = null,
    val message: String? = null,
    val error: String? = null,
    val recovered: Boolean = false,
    val busy: Boolean = false,
    val projectId: String? = null,
    val editor: SyncEditorState = SyncEditorState(),
    val waveform: WaveformPeaks? = null,
    val playbackSpeed: Float = 1f,
    val playingWordIndex: Int = -1,
    val zoom: Float = 1f,
    val looping: Boolean = false,
    val syncing: Boolean = false,
    val syncProgress: Float = 0f,
    val speechStatus: String = "O reconhecimento local não está incluído nesta versão.",
) {
    val selectedDevice: AudioDevice? get() = devices.firstOrNull { it.id == selectedDeviceId }
}

class StudioViewModel(
    private val store: StudioProjectStore = StudioProjectStore(),
    private val recorder: DesktopRecorder = DesktopRecorder(),
    private val player: DesktopPlayer = DesktopPlayer(),
    private val monitor: InputMonitor = InputMonitor(),
    private val speech: MissingTranscriber = MissingTranscriber(),
    private val alignment: StudioAlignmentService = StudioAlignmentService(speech),
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _state = MutableStateFlow(StudioUiState())
    val state: StateFlow<StudioUiState> = _state.asStateFlow()
    private var autosaveJob: Job? = null
    private var createdAtEpochMs: Long = System.currentTimeMillis()
    private val editor = SyncEditor()
    private var playUntilMs: Long? = null
    private var loopRange: Pair<Long, Long>? = null
    private var cancelSync = false
    private var syncJob: Job? = null

    init {
        monitor.onLevel = { reading ->
            if (!recorder.isRecording) {
                _state.update { it.copy(level = reading) }
            }
        }
        recorder.onStatus = { status ->
            _state.update {
                it.copy(
                    recorderState = status.state,
                    level = status.level,
                    durationMs = status.durationMs,
                    error = status.error ?: it.error,
                    capture = status.format?.let { format ->
                        CaptureInfo(
                            deviceName = status.deviceName ?: "Desconhecido",
                            sampleRate = format.sampleRate,
                            bitsPerSample = format.bitsPerSample,
                            channels = format.channels,
                            fallbackReason = format.fallbackReason,
                            clipped = status.clipped,
                        )
                    } ?: it.capture,
                    message = status.format?.fallbackReason ?: it.message,
                )
            }
        }
        refreshDevices()
        scope.launch {
            while (isActive) {
                delay(40)
                val pos = player.positionMs
                val limit = playUntilMs
                val loop = loopRange
                if (limit != null && player.isPlaying && pos >= limit) {
                    if (loop != null) {
                        player.seekTo(loop.first)
                    } else {
                        player.pause()
                        playUntilMs = null
                    }
                }
                if (player.isPlaying || _state.value.playing) {
                    _state.update {
                        it.copy(
                            playing = player.isPlaying,
                            playPositionMs = pos,
                            playDurationMs = player.durationMs,
                            playingWordIndex = editor.playingWordIndex(pos),
                        )
                    }
                }
            }
        }
    }

    fun refreshDevices() {
        val devices = AudioDevices.listInputs()
        val preferred = devices.firstOrNull { it.isUsb } ?: devices.firstOrNull()
        val selected = _state.value.selectedDeviceId?.let { id -> devices.firstOrNull { it.id == id } } ?: preferred
        val negotiated = AudioDevices.negotiate(selected)
        _state.update {
            it.copy(
                devices = devices,
                selectedDeviceId = selected?.id,
                negotiated = negotiated,
                error = if (devices.isEmpty()) {
                    "Não foi encontrado nenhum microfone. Ligue um microfone USB ou autorize o acesso nas definições do Windows."
                } else {
                    it.error
                },
                message = negotiated?.fallbackReason ?: it.message,
            )
        }
        restartMonitor()
    }

    fun selectDevice(id: String) {
        if (recorder.isRecording) {
            _state.update { it.copy(error = "Termine a gravação antes de mudar de microfone.") }
            return
        }
        val device = _state.value.devices.firstOrNull { it.id == id }
        _state.update {
            it.copy(
                selectedDeviceId = id,
                negotiated = AudioDevices.negotiate(device),
                message = AudioDevices.negotiate(device)?.fallbackReason,
                error = null,
            )
        }
        restartMonitor()
    }

    fun updateTitle(value: String) {
        _state.update { it.copy(title = value) }
        scheduleAutosave()
    }

    fun updateText(value: String) {
        _state.update { it.copy(text = value) }
        scheduleAutosave()
    }

    fun importText(file: File) {
        runCatching { file.readText() }
            .onSuccess { text ->
                _state.update { it.copy(text = text, message = "Texto importado de ${file.name}", error = null) }
                scheduleAutosave()
            }
            .onFailure { error ->
                _state.update { it.copy(error = error.message ?: "Não foi possível ler o ficheiro.") }
            }
    }

    fun newProject(directory: File) {
        val created = store.create(directory, _state.value.title)
        openLoaded(directory, created)
        _state.update { it.copy(message = "Projecto criado em ${directory.absolutePath}") }
    }

    fun openProject(directory: File) {
        runCatching { store.load(directory) }
            .onSuccess { project ->
                openLoaded(directory, project)
                _state.update {
                    it.copy(
                        recovered = project.recoveredAfterCrash,
                        message = if (project.recoveredAfterCrash) {
                            "Foi recuperado um autosave. Guarde o projecto para confirmar."
                        } else {
                            "Projecto aberto."
                        },
                    )
                }
            }
            .onFailure { error ->
                _state.update { it.copy(error = error.message) }
            }
    }

    fun saveProject() {
        val directory = _state.value.directory
        if (directory == null) {
            _state.update { it.copy(error = "Escolha uma pasta para guardar o projecto.") }
            return
        }
        store.save(directory, currentProject())
        _state.update { it.copy(message = "Projecto guardado.", recovered = false, error = null) }
    }

    fun startRecording() {
        val directory = ensureDirectory() ?: return
        if (_state.value.devices.isEmpty()) {
            _state.update { it.copy(error = "Não há microfone disponível.") }
            return
        }
        player.stop()
        monitor.stop()
        val original = store.originalWav(directory)
        try {
            recorder.start(_state.value.selectedDevice, original)
            _state.update { it.copy(error = null, message = "A gravar…") }
        } catch (error: LineUnavailableException) {
            _state.update { it.copy(error = error.message ?: "Não foi possível abrir o microfone.") }
            restartMonitor()
        } catch (error: Exception) {
            _state.update { it.copy(error = error.message ?: "Falha ao iniciar a gravação.") }
            restartMonitor()
        }
    }

    fun pauseOrResume() {
        if (recorder.isPaused) recorder.resume() else recorder.pause()
    }

    fun stopRecording() {
        val directory = _state.value.directory ?: return
        val original = store.originalWav(directory)
        scope.launch {
            _state.update { it.copy(busy = true) }
            val saved = recorder.stop(original)
            if (saved != null) {
                val props = StreamingWavWriter.inspect(saved)
                runCatching { RecognitionAudio.writeMono16k(saved, store.recognitionWav(directory)) }
                store.save(directory, currentProject(props.durationMs))
                _state.update {
                    it.copy(
                        busy = false,
                        hasOriginal = true,
                        hasRecognition = store.recognitionWav(directory).exists(),
                        durationMs = props.durationMs,
                        originalProps = describe(saved, it.capture),
                        message = "Gravação original preservada em WAV PCM. Cópia 16 kHz mono criada para reconhecimento.",
                    )
                }
                runCatching { player.load(saved) }
                refreshWaveform()
            } else {
                _state.update { it.copy(busy = false) }
            }
            restartMonitor()
        }
    }

    fun playRecording() {
        playAll()
    }

    fun exportLsb(destination: File) {
        val directory = ensureDirectory() ?: return
        val original = store.originalWav(directory)
        if (!original.exists()) {
            _state.update { it.copy(error = "Grave ou importe a narração antes de exportar.") }
            return
        }
        val target = if (destination.extension.equals("lsb", true)) destination else File(destination.path + ".lsb")
        try {
            val content = ContentPackageMapper.fromPackage(currentProject().content, original.name)
            target.outputStream().use { stream ->
                LsbArchive.export(content, original.readBytes(), stream, source = "studio")
            }
            _state.update { it.copy(message = "Pacote exportado: ${target.name}", error = null) }
        } catch (error: Exception) {
            _state.update { it.copy(error = error.message ?: "Não foi possível exportar o .lsb.") }
        }
    }

    fun importLsb(file: File) {
        val directory = ensureDirectory() ?: return
        try {
            val bundle = file.inputStream().use { LsbArchive.import(it) }
            val original = store.originalWav(directory)
            original.writeBytes(bundle.audio)
            runCatching { RecognitionAudio.writeMono16k(original, store.recognitionWav(directory)) }
            editor.load(bundle.words, bundle.metadata.durationMs)
            _state.update {
                it.copy(
                    title = bundle.metadata.title,
                    text = bundle.text,
                    durationMs = bundle.metadata.durationMs,
                    hasOriginal = true,
                    hasRecognition = store.recognitionWav(directory).exists(),
                    editor = editor.snapshot,
                    message = "Pacote .lsb importado. ${bundle.words.size} palavras preservadas.",
                    error = null,
                )
            }
            store.save(directory, currentProject(bundle.metadata.durationMs))
            runCatching { player.load(original) }
            refreshWaveform()
        } catch (error: LsbException) {
            _state.update { it.copy(error = error.message) }
        } catch (error: Exception) {
            _state.update { it.copy(error = error.message ?: "Pacote inválido.") }
        }
    }

    fun importAudio(file: File) {
        val directory = ensureDirectory() ?: return
        scope.launch {
            runCatching {
                DesktopAudioImport.importToProject(file, store.originalWav(directory), store.recognitionWav(directory))
            }.onSuccess {
                val original = store.originalWav(directory)
                val props = StreamingWavWriter.inspect(original)
                runCatching { player.load(original) }
                store.save(directory, currentProject(props.durationMs))
                _state.update {
                    it.copy(
                        hasOriginal = true,
                        hasRecognition = store.recognitionWav(directory).exists(),
                        durationMs = props.durationMs,
                        originalProps = describe(original, it.capture),
                        message = "Áudio importado. Original preservado; cópia 16 kHz criada.",
                        error = null,
                    )
                }
                editor.load(editor.snapshot.words, props.durationMs, resetHistory = false)
                pushEditor()
                refreshWaveform()
            }.onFailure { error ->
                _state.update { it.copy(error = error.message ?: "Falha na importação.") }
            }
        }
    }

    fun synchronize() {
        val directory = ensureDirectory() ?: return
        if (_state.value.text.isBlank()) {
            _state.update { it.copy(error = "Escreva ou importe o texto antes de sincronizar.") }
            return
        }
        val recognition = store.recognitionWav(directory)
        val original = store.originalWav(directory)
        if (!original.exists()) {
            _state.update { it.copy(error = "Grave ou importe a narração antes de sincronizar.") }
            return
        }
        if (!recognition.exists()) {
            runCatching { RecognitionAudio.writeMono16k(original, recognition) }
        }
        cancelSync = false
        syncJob?.cancel()
        syncJob = scope.launch {
            _state.update { it.copy(syncing = true, syncProgress = 0.05f, error = null, message = "A sincronizar localmente…") }
            val result = alignment.synchronize(
                sourceText = _state.value.text,
                wav16kMono = recognition.takeIf { it.exists() },
                audioDurationMs = _state.value.durationMs,
                previousWords = editor.snapshot.words,
                isCancelled = { cancelSync },
            )
            if (result.cancelled) {
                _state.update { it.copy(syncing = false, message = "Sincronização cancelada.") }
                return@launch
            }
            val loaded = editor.load(result.alignment.words, _state.value.durationMs)
            store.save(directory, currentProject())
            _state.update {
                it.copy(
                    syncing = false,
                    syncProgress = 1f,
                    editor = loaded,
                    speechStatus = "O reconhecimento local não está incluído nesta versão.",
                    message = result.engineMessage ?: when (result.alignment.status) {
                        SyncStatus.COMPLETED -> "Sincronização concluída."
                        SyncStatus.COMPLETED_WITH_GAPS ->
                            "Sincronização com ${result.alignment.reviewCount} palavras a rever."
                        else -> "Não foi possível alinhar com confiança. Corrija manualmente."
                    },
                    error = if (result.engineFailed) result.engineMessage else null,
                )
            }
        }
    }

    fun cancelSynchronize() {
        cancelSync = true
        speech.cancel()
    }

    fun selectWord(index: Int) {
        _state.update { it.copy(editor = editor.select(index), error = null) }
    }

    fun selectNextWord(delta: Int) {
        val current = editor.snapshot.selectedIndex
        val next = if (current < 0) 0 else current + delta
        selectWord(next)
    }

    fun setSelectedInterval(startMs: Long, endMs: Long) {
        persistEditor { editor.setInterval(editor.snapshot.selectedIndex, startMs, endMs) }
    }

    fun nudgeSelected(edge: IntervalEdge, deltaMs: Long) {
        persistEditor { editor.nudge(editor.snapshot.selectedIndex, edge, deltaMs) }
    }

    fun markSelectedStart() {
        persistEditor { editor.markStart(editor.snapshot.selectedIndex, player.positionMs) }
    }

    fun markSelectedEnd() {
        persistEditor { editor.markEnd(editor.snapshot.selectedIndex, player.positionMs) }
    }

    fun verifySelected() {
        persistEditor { editor.verify(editor.snapshot.selectedIndex) }
    }

    fun undoEdit() {
        persistEditor { editor.undo() }
    }

    fun redoEdit() {
        persistEditor { editor.redo() }
    }

    fun seekTo(positionMs: Long) {
        player.seekTo(positionMs)
        _state.update { it.copy(playPositionMs = positionMs, playingWordIndex = editor.playingWordIndex(positionMs)) }
    }

    fun setPlaybackSpeed(speed: Float) {
        player.setSpeed(speed)
        _state.update { it.copy(playbackSpeed = speed) }
    }

    fun setZoom(value: Float) {
        _state.update { it.copy(zoom = value.coerceIn(1f, 8f)) }
    }

    fun playAll() {
        val directory = _state.value.directory ?: return
        val original = store.originalWav(directory)
        if (!original.exists()) {
            _state.update { it.copy(error = "Ainda não há gravação original.") }
            return
        }
        playUntilMs = null
        loopRange = null
        runCatching {
            if (!player.isPlaying) player.load(original)
            player.setSpeed(_state.value.playbackSpeed)
            if (player.positionMs >= player.durationMs - 40) player.seekTo(0)
            player.play()
            _state.update { it.copy(playing = true, looping = false, error = null) }
        }.onFailure { error -> _state.update { it.copy(error = error.message) } }
    }

    fun pausePlayback() {
        playUntilMs = null
        loopRange = null
        player.pause()
        _state.update { it.copy(playing = false, looping = false) }
    }

    fun playSelectedWord() {
        val word = editor.snapshot.selected ?: return
        val start = word.startMs
        val end = word.endMs
        if (start == null || end == null) {
            _state.update { it.copy(error = "Atribua início e fim à palavra antes de a ouvir.") }
            return
        }
        playRange(start, end, loop = _state.value.looping)
    }

    fun playFromSelected() {
        val word = editor.snapshot.selected ?: return
        val start = word.startMs ?: player.positionMs
        playRange(start, _state.value.durationMs.coerceAtLeast(player.durationMs), loop = false)
    }

    fun toggleLoop() {
        val word = editor.snapshot.selected
        if (_state.value.looping) {
            loopRange = null
            playUntilMs = null
            _state.update { it.copy(looping = false) }
            return
        }
        if (word?.isTimed == true) {
            _state.update { it.copy(looping = true) }
            playRange(word.startMs!!, word.endMs!!, loop = true)
        }
    }

    fun onEditorKey(ctrl: Boolean, key: String): Boolean {
        when {
            ctrl && key == "Z" -> undoEdit()
            ctrl && key == "Y" -> redoEdit()
            key == "LEFT" -> selectNextWord(-1)
            key == "RIGHT" -> selectNextWord(1)
            key == "SPACE" -> if (player.isPlaying) pausePlayback() else playAll()
            key == "ENTER" -> verifySelected()
            key == "[" -> nudgeSelected(IntervalEdge.START, -50)
            key == "]" -> nudgeSelected(IntervalEdge.START, 50)
            key == ";" -> nudgeSelected(IntervalEdge.END, -50)
            key == "'" -> nudgeSelected(IntervalEdge.END, 50)
            else -> return false
        }
        return true
    }

    fun dispose() {
        cancelSynchronize()
        recorder.cancel()
        monitor.stop()
        player.stop()
        scope.coroutineContext[Job]?.cancel()
    }

    private fun restartMonitor() {
        if (recorder.isRecording) return
        monitor.start(_state.value.selectedDevice)
    }

    private fun persistEditor(action: () -> SyncEditorState) {
        val snapshot = action()
        val directory = _state.value.directory
        if (directory != null) store.save(directory, currentProject())
        _state.update { it.copy(editor = snapshot, error = null, message = "Alteração guardada.") }
    }

    private fun pushEditor() {
        _state.update { it.copy(editor = editor.snapshot) }
    }

    private fun playRange(startMs: Long, endMs: Long, loop: Boolean) {
        val directory = _state.value.directory ?: return
        val original = store.originalWav(directory)
        if (!original.exists()) return
        playUntilMs = endMs
        loopRange = if (loop) startMs to endMs else null
        runCatching {
            player.load(original)
            player.setSpeed(_state.value.playbackSpeed)
            player.seekTo(startMs)
            player.play()
            _state.update { it.copy(playing = true, looping = loop, error = null) }
        }.onFailure { error -> _state.update { it.copy(error = error.message) } }
    }

    private fun refreshWaveform() {
        val directory = _state.value.directory ?: return
        val source = store.originalWav(directory).takeIf { it.exists() }
            ?: store.recognitionWav(directory).takeIf { it.exists() }
            ?: return
        scope.launch {
            val peaks = runCatching { DesktopWaveform.fromWav(source) }.getOrNull()
            _state.update { it.copy(waveform = peaks, editor = editor.load(editor.snapshot.words, peaks?.durationMs ?: it.durationMs, resetHistory = false)) }
        }
    }

    private fun openLoaded(directory: File, project: StudioProjectFile) {
        val original = store.originalWav(directory)
        val recognition = store.recognitionWav(directory)
        val props = original.takeIf { it.exists() }?.let { runCatching { StreamingWavWriter.inspect(it) }.getOrNull() }
        createdAtEpochMs = project.content.createdAtEpochMs
        val duration = props?.durationMs ?: project.content.durationMs
        val editorState = editor.load(project.content.words, duration)
        if (original.exists()) runCatching { player.load(original) }
        _state.update {
            it.copy(
                directory = directory,
                projectId = project.content.id,
                title = project.content.title,
                text = project.content.text,
                hasOriginal = original.exists(),
                hasRecognition = recognition.exists(),
                durationMs = duration,
                capture = project.capture,
                originalProps = original.takeIf { file -> file.exists() }?.let { file -> describe(file, project.capture) },
                editor = editorState,
                speechStatus = "O reconhecimento local não está incluído nesta versão.",
                error = null,
            )
        }
        refreshWaveform()
    }

    private fun currentProject(durationMs: Long = _state.value.durationMs): StudioProjectFile {
        val now = System.currentTimeMillis()
        val existing = _state.value
        val snapshot = editor.snapshot
        val content = ContentPackage(
            id = existing.projectId ?: java.util.UUID.randomUUID().toString(),
            title = existing.title.ifBlank { "Sem título" },
            text = existing.text,
            audioFile = if (existing.hasOriginal) StudioProjectFile.ORIGINAL_AUDIO else null,
            durationMs = durationMs,
            words = snapshot.words,
            syncStatus = snapshot.status,
            createdAtEpochMs = createdAtEpochMs,
            updatedAtEpochMs = now,
        )
        return StudioProjectFile(
            content = content,
            capture = existing.capture,
            originalAudioFile = if (existing.hasOriginal) StudioProjectFile.ORIGINAL_AUDIO else null,
            recognitionAudioFile = if (existing.hasRecognition) StudioProjectFile.RECOGNITION_AUDIO else null,
        )
    }

    private fun scheduleAutosave() {
        autosaveJob?.cancel()
        autosaveJob = scope.launch {
            delay(800)
            val directory = _state.value.directory ?: return@launch
            store.autosave(directory, currentProject())
        }
    }

    private fun ensureDirectory(): File? {
        val directory = _state.value.directory
        if (directory == null) {
            _state.update { it.copy(error = "Crie ou abra um projecto local antes de gravar.") }
        }
        return directory
    }

    private fun describe(file: File, capture: CaptureInfo?): String {
        val props = StreamingWavWriter.inspect(file)
        val device = capture?.deviceName ?: "desconhecido"
        return "Original: ${props.sampleRate} Hz, ${props.bitsPerSample} bits, " +
            "${props.channels} canal(is), ${props.durationMs / 1000.0} s, ${file.name}, microfone: $device"
    }
}
