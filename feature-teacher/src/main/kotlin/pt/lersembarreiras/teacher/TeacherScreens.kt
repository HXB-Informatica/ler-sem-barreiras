package pt.lersembarreiras.teacher

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import pt.lersembarreiras.core.model.ReadingContent
import pt.lersembarreiras.core.model.SyncStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TeacherListScreen(
    viewModel: TeacherListViewModel,
    onBack: () -> Unit,
    onCreate: () -> Unit,
    onOpen: (String) -> Unit,
    pendingImportUri: android.net.Uri? = null,
    onPendingImportConsumed: () -> Unit = {},
) {
    val contents by viewModel.contents.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val importLsb = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { picked ->
            viewModel.importLsb { context.contentResolver.openInputStream(picked) }
        }
    }
    LaunchedEffect(pendingImportUri) {
        val uri = pendingImportUri ?: return@LaunchedEffect
        viewModel.importLsb { context.contentResolver.openInputStream(uri) }
        onPendingImportConsumed()
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Modo Professor") },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.semantics { contentDescription = "Voltar" }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                },
                actions = {
                    TextButton(
                        onClick = { importLsb.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) },
                        modifier = Modifier.semantics { contentDescription = "Importar exercício .lsb" },
                    ) { Text("Importar .lsb") }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onCreate,
                modifier = Modifier.size(72.dp).semantics { contentDescription = "Criar conteúdo" },
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(32.dp))
            }
        },
    ) { padding ->
        message?.let { text ->
            AlertDialog(
                onDismissRequest = viewModel::consumeMessage,
                confirmButton = {
                    TextButton(onClick = viewModel::consumeMessage) { Text("OK") }
                },
                title = { Text("Importação") },
                text = { Text(text) },
            )
        }
        if (contents.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Ainda não existem conteúdos neste tablet.", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(12.dp))
                Text("Crie um texto, grave a narração e sincronize automaticamente, tudo sem internet.")
                Spacer(Modifier.height(16.dp))
                Button(onClick = { importLsb.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) }) {
                    Text("Importar exercício .lsb")
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item { Spacer(Modifier.height(8.dp)) }
                itemsIndexed(contents, key = { _, item -> item.id }) { _, item ->
                    ContentCard(item, onOpen = { onOpen(item.id) }, onDelete = { viewModel.delete(item.id) })
                }
                item { Spacer(Modifier.height(88.dp)) }
            }
        }
    }
}

@Composable
private fun ContentCard(item: ReadingContent, onOpen: () -> Unit, onDelete: () -> Unit) {
    val date = remember(item.updatedAtEpochMs) {
        SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("pt", "PT")).format(Date(item.updatedAtEpochMs))
    }
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen)) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(item.title, style = MaterialTheme.typography.titleLarge)
                Text(syncLabel(item.syncStatus), style = MaterialTheme.typography.bodyMedium)
                Text(date, style = MaterialTheme.typography.bodySmall)
            }
            IconButton(onClick = onDelete, modifier = Modifier.semantics { contentDescription = "Eliminar ${item.title}" }) {
                Icon(Icons.Default.Delete, contentDescription = null)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TeacherEditScreen(
    viewModel: TeacherEditViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var pendingRecord by remember { mutableStateOf(false) }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let { viewModel.importAudio(it) }
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted && pendingRecord) {
            pendingRecord = false
            viewModel.startRecording()
        } else if (!granted) {
            pendingRecord = false
        }
    }

    fun startRecording() {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) {
            viewModel.startRecording()
        } else {
            pendingRecord = true
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Preparar conteúdo") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                },
            )
        },
    ) { padding ->
        if (state.loading) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OutlinedTextField(
                value = state.title,
                onValueChange = viewModel::updateTitle,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Título") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            )
            OutlinedTextField(
                value = state.text,
                onValueChange = viewModel::updateText,
                modifier = Modifier.fillMaxWidth().height(180.dp),
                label = { Text("Texto para os alunos") },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            )
            Text("Narração", style = MaterialTheme.typography.titleLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = { if (state.recording) viewModel.stopRecording() else startRecording() },
                    modifier = Modifier.weight(1f).height(56.dp),
                ) {
                    Text(if (state.recording) "Parar gravação" else "Gravar")
                }
                FilledTonalButton(
                    onClick = { importLauncher.launch(arrayOf("audio/*")) },
                    modifier = Modifier.weight(1f).height(56.dp),
                    enabled = !state.recording,
                ) {
                    Text("Importar áudio")
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                FilledTonalButton(
                    onClick = viewModel::togglePlayback,
                    enabled = state.hasAudio,
                    modifier = Modifier.weight(1f).height(56.dp),
                ) {
                    Text(if (state.playback?.playing == true) "Pausar" else "Reproduzir")
                }
                FilledTonalButton(
                    onClick = viewModel::restartPlayback,
                    enabled = state.hasAudio,
                    modifier = Modifier.weight(1f).height(56.dp),
                ) {
                    Text("Recomeçar")
                }
            }
            state.playback?.let { playback ->
                if (playback.durationMs > 0) {
                    LinearProgressIndicator(
                        progress = { (playback.positionMs.toFloat() / playback.durationMs).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            Button(
                onClick = viewModel::synchronize,
                enabled = !state.syncing && !state.recording,
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) {
                Text("Sincronizar automaticamente")
            }
            if (state.syncing) {
                LinearProgressIndicator(progress = { state.syncProgress }, modifier = Modifier.fillMaxWidth())
                Text(state.syncMessage ?: "A processar neste tablet…")
                TextButton(onClick = viewModel::cancelSync) { Text("Cancelar") }
            } else {
                state.syncMessage?.let { Text(it) }
            }
            state.usedTokenTimestamps?.let { used ->
                Text(
                    if (used) {
                        "O motor local devolveu marcas temporais ao nível de palavras (funcionalidade experimental do whisper.cpp)."
                    } else {
                        "As marcas temporais de palavras não estavam disponíveis; reveja os tempos assinalados."
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            state.savedMessage?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
            if (state.editor.words.isNotEmpty()) {
                SyncEditorPanel(
                    sourceText = state.text,
                    editor = state.editor,
                    waveform = state.waveform,
                    waveformLoading = state.waveformLoading,
                    playbackPositionMs = state.playback?.positionMs ?: 0L,
                    playbackSpeed = state.playbackSpeed,
                    playingWordIndex = state.playingWordIndex,
                    playing = state.playback?.playing == true,
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
                    onPause = viewModel::togglePlayback,
                    onSpeed = viewModel::setPlaybackSpeed,
                )
            }
            Button(
                onClick = viewModel::save,
                modifier = Modifier.fillMaxWidth().height(56.dp).padding(bottom = 24.dp),
            ) {
                Text("Guardar")
            }
        }
    }
}

private fun syncLabel(status: SyncStatus): String = when (status) {
    SyncStatus.NONE -> "Ainda não sincronizado"
    SyncStatus.IN_PROGRESS -> "Sincronização em curso"
    SyncStatus.COMPLETED -> "Pronto para os alunos"
    SyncStatus.COMPLETED_WITH_GAPS -> "Sincronizado — convém rever algumas palavras"
    SyncStatus.FAILED -> "Sincronização falhou"
    SyncStatus.CANCELLED -> "Sincronização cancelada"
}
