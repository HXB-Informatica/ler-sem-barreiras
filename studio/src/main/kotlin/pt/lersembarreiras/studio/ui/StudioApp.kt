package pt.lersembarreiras.studio.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import java.io.File
import javax.swing.JFileChooser
import pt.lersembarreiras.studio.StudioViewModel
import pt.lersembarreiras.studio.audio.RecorderState

@Composable
fun StudioApp(viewModel: StudioViewModel = remember { StudioViewModel() }) {
    val state by viewModel.state.collectAsState()
    DisposableEffect(viewModel) {
        onDispose { viewModel.dispose() }
    }
    StudioTheme {
        Surface(
            modifier = Modifier.fillMaxSize().onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                val name = when (event.key) {
                    Key.DirectionLeft -> "LEFT"
                    Key.DirectionRight -> "RIGHT"
                    Key.Spacebar -> "SPACE"
                    Key.Enter -> "ENTER"
                    Key.Z -> "Z"
                    Key.Y -> "Y"
                    else -> event.key.toString().substringAfterLast('.')
                }
                viewModel.onEditorKey(event.isCtrlPressed, name)
            },
            color = MaterialTheme.colorScheme.background,
        ) {
            Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Ler sem Barreiras Studio", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "Preparação de conteúdos offline para professores. Escola Gil Vicente  ·  HXB, parceiro tecnológico.",
                    style = MaterialTheme.typography.bodyLarge,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        chooseDirectory("Criar projecto")?.let { viewModel.newProject(it) }
                    }) { Text("Novo projecto") }
                    FilledTonalButton(onClick = {
                        chooseDirectory("Abrir projecto")?.let { viewModel.openProject(it) }
                    }) { Text("Abrir") }
                    FilledTonalButton(onClick = viewModel::saveProject) { Text("Guardar") }
                    FilledTonalButton(onClick = viewModel::refreshDevices) { Text("Actualizar microfones") }
                    FilledTonalButton(onClick = {
                        chooseTextFile()?.let { viewModel.importText(it) }
                    }) { Text("Importar texto") }
                    FilledTonalButton(onClick = {
                        chooseAudioFile()?.let { viewModel.importAudio(it) }
                    }) { Text("Importar áudio") }
                    FilledTonalButton(onClick = {
                        chooseLsbSave()?.let { viewModel.exportLsb(it) }
                    }) { Text("Exportar .lsb") }
                    FilledTonalButton(onClick = {
                        chooseLsbOpen()?.let { viewModel.importLsb(it) }
                    }) { Text("Importar .lsb") }
                }
                state.directory?.let {
                    Text("Pasta: ${it.absolutePath}", style = MaterialTheme.typography.bodyMedium)
                }
                Row(Modifier.fillMaxWidth().weight(0.42f), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = state.title,
                            onValueChange = viewModel::updateTitle,
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Título") },
                            singleLine = true,
                        )
                        OutlinedTextField(
                            value = state.text,
                            onValueChange = viewModel::updateText,
                            modifier = Modifier.fillMaxWidth().weight(1f),
                            label = { Text("Texto a ler") },
                        )
                    }
                    Column(
                        Modifier.weight(1f).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text("Microfone", style = MaterialTheme.typography.titleLarge)
                        DeviceMenu(
                            labels = state.devices.map { it.name + if (it.isUsb) " (USB)" else "" },
                            ids = state.devices.map { it.id },
                            selectedId = state.selectedDeviceId,
                            onSelect = viewModel::selectDevice,
                        )
                        state.negotiated?.let {
                            Text(it.description + if (it.preferred) " (preferido)" else "")
                        }
                        Text("Nível de entrada")
                        LinearProgressIndicator(
                            progress = { state.level.peak },
                            modifier = Modifier.fillMaxWidth().height(18.dp),
                            color = if (state.level.clipping || state.capture?.clipped == true) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.secondary
                            },
                        )
                        if (state.level.clipping || state.capture?.clipped == true) {
                            Text(
                                "Saturação (clipping) detectada. Afaste-se do microfone ou baixe o ganho.",
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = viewModel::startRecording,
                                enabled = state.recorderState == RecorderState.IDLE && !state.busy,
                            ) { Text("Gravar") }
                            FilledTonalButton(
                                onClick = viewModel::pauseOrResume,
                                enabled = state.recorderState == RecorderState.RECORDING ||
                                    state.recorderState == RecorderState.PAUSED,
                            ) {
                                Text(if (state.recorderState == RecorderState.PAUSED) "Continuar" else "Pausar")
                            }
                            FilledTonalButton(
                                onClick = viewModel::stopRecording,
                                enabled = state.recorderState == RecorderState.RECORDING ||
                                    state.recorderState == RecorderState.PAUSED,
                            ) { Text("Terminar") }
                            FilledTonalButton(
                                onClick = viewModel::playRecording,
                                enabled = state.hasOriginal && state.recorderState == RecorderState.IDLE,
                            ) { Text(if (state.playing) "Pausar audição" else "Ouvir") }
                        }
                        Text("Estado: ${state.recorderState}  ·  ${state.durationMs / 1000.0} s")
                        state.originalProps?.let { Text(it) }
                        if (state.hasRecognition) {
                            Text("Cópia para reconhecimento: audio-16k.wav (mono PCM 16 kHz). O original não foi filtrado.")
                        }
                        Box(
                            Modifier.fillMaxWidth().height(8.dp).background(
                                if (state.level.clipping) MaterialTheme.colorScheme.error else Color.Transparent,
                            ),
                        )
                    }
                }
                Box(Modifier.fillMaxWidth().weight(0.58f).verticalScroll(rememberScrollState())) {
                    StudioSyncPanel(
                        editor = state.editor,
                        waveform = state.waveform,
                        positionMs = state.playPositionMs,
                        playingWordIndex = state.playingWordIndex,
                        playing = state.playing,
                        playbackSpeed = state.playbackSpeed,
                        zoom = state.zoom,
                        looping = state.looping,
                        syncing = state.syncing,
                        syncProgress = state.syncProgress,
                        speechStatus = state.speechStatus,
                        onSelect = viewModel::selectWord,
                        onSeek = viewModel::seekTo,
                        onIntervalChange = viewModel::setSelectedInterval,
                        onNudge = viewModel::nudgeSelected,
                        onMarkStart = viewModel::markSelectedStart,
                        onMarkEnd = viewModel::markSelectedEnd,
                        onVerify = viewModel::verifySelected,
                        onUndo = viewModel::undoEdit,
                        onRedo = viewModel::redoEdit,
                        onPlayAll = viewModel::playAll,
                        onPlayWord = viewModel::playSelectedWord,
                        onPlayFromSelected = viewModel::playFromSelected,
                        onToggleLoop = viewModel::toggleLoop,
                        onPause = viewModel::pausePlayback,
                        onSpeed = viewModel::setPlaybackSpeed,
                        onZoom = viewModel::setZoom,
                        onSynchronize = viewModel::synchronize,
                        onCancelSync = viewModel::cancelSynchronize,
                    )
                }
                state.message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        }
    }
}

@Composable
private fun DeviceMenu(
    labels: List<String>,
    ids: List<String>,
    selectedId: String?,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = ids.indexOf(selectedId).takeIf { it >= 0 }?.let { labels[it] } ?: "Nenhum microfone"
    Box {
        FilledTonalButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Text(selectedLabel)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            ids.forEachIndexed { index, id ->
                DropdownMenuItem(
                    text = { Text(labels[index]) },
                    onClick = {
                        onSelect(id)
                        expanded = false
                    },
                )
            }
        }
    }
}

private fun chooseDirectory(title: String): File? {
    val chooser = JFileChooser().apply {
        dialogTitle = title
        fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
        isAcceptAllFileFilterUsed = true
    }
    val result = chooser.showOpenDialog(null)
    return if (result == JFileChooser.APPROVE_OPTION) chooser.selectedFile else null
}

private fun chooseTextFile(): File? {
    val chooser = JFileChooser().apply {
        dialogTitle = "Importar texto"
        fileSelectionMode = JFileChooser.FILES_ONLY
    }
    val result = chooser.showOpenDialog(null)
    return if (result == JFileChooser.APPROVE_OPTION) chooser.selectedFile else null
}

private fun chooseLsbSave(): File? {
    val chooser = JFileChooser().apply {
        dialogTitle = "Exportar exercício .lsb"
        selectedFile = File("exercicio.lsb")
    }
    val result = chooser.showSaveDialog(null)
    return if (result == JFileChooser.APPROVE_OPTION) chooser.selectedFile else null
}

private fun chooseLsbOpen(): File? {
    val chooser = JFileChooser().apply {
        dialogTitle = "Importar exercício .lsb"
        fileSelectionMode = JFileChooser.FILES_ONLY
    }
    val result = chooser.showOpenDialog(null)
    return if (result == JFileChooser.APPROVE_OPTION) chooser.selectedFile else null
}

private fun chooseAudioFile(): File? {
    val chooser = JFileChooser().apply {
        dialogTitle = "Importar áudio (WAV, AIFF, AU)"
        fileSelectionMode = JFileChooser.FILES_ONLY
    }
    val result = chooser.showOpenDialog(null)
    return if (result == JFileChooser.APPROVE_OPTION) chooser.selectedFile else null
}
