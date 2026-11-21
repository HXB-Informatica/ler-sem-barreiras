package pt.lersembarreiras.studio.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import pt.lersembarreiras.core.model.WordState
import pt.lersembarreiras.core.model.WordTiming
import pt.lersembarreiras.editor.IntervalEdge
import pt.lersembarreiras.editor.SyncEditorState
import pt.lersembarreiras.studio.audio.WaveformPeaks

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StudioSyncPanel(
    editor: SyncEditorState,
    waveform: WaveformPeaks?,
    positionMs: Long,
    playingWordIndex: Int,
    playing: Boolean,
    playbackSpeed: Float,
    zoom: Float,
    looping: Boolean,
    syncing: Boolean,
    syncProgress: Float,
    whisperStatus: String,
    onSelect: (Int) -> Unit,
    onSeek: (Long) -> Unit,
    onIntervalChange: (Long, Long) -> Unit,
    onNudge: (IntervalEdge, Long) -> Unit,
    onMarkStart: () -> Unit,
    onMarkEnd: () -> Unit,
    onVerify: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onPlayAll: () -> Unit,
    onPlayWord: () -> Unit,
    onPlayFromSelected: () -> Unit,
    onToggleLoop: () -> Unit,
    onPause: () -> Unit,
    onSpeed: (Float) -> Unit,
    onZoom: (Float) -> Unit,
    onSynchronize: () -> Unit,
    onCancelSync: () -> Unit,
) {
    val selected = editor.selected
    val aligned = editor.words.count { it.isTimed }
    val review = editor.words.count { it.requiresReview() }
    val verified = editor.words.count { it.resolvedState() == WordState.VERIFIED }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        Text("Sincronização áudio / texto", style = MaterialTheme.typography.titleLarge)
        Text(whisperStatus, style = MaterialTheme.typography.bodyMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = onSynchronize, enabled = !syncing) { Text("Sincronizar localmente") }
            if (syncing) {
                FilledTonalButton(onClick = onCancelSync) { Text("Cancelar") }
                LinearProgressIndicator(progress = { syncProgress }, modifier = Modifier.weight(1f))
            }
        }
        Text("Alinhadas: $aligned   ·   Em revisão: $review   ·   Verificadas: $verified   ·   Total: ${editor.words.size}")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Legend("AUTO", Color(0xFFB3E5FC))
            Legend("REVIEW", Color(0xFFFFCC80))
            Legend("MANUAL", Color(0xFFCE93D8))
            Legend("VERIFIED", Color(0xFFA5D6A7))
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            editor.words.forEachIndexed { index, word ->
                val bg = when {
                    index == playingWordIndex -> Color(0xFFFFF176)
                    else -> stateColor(word.resolvedState())
                }
                val selectedWord = index == editor.selectedIndex
                Text(
                    word.text,
                    color = Color.Black,
                    fontSize = 20.sp,
                    modifier = Modifier
                        .background(bg, MaterialTheme.shapes.medium)
                        .then(if (selectedWord) Modifier.border(3.dp, Color.Black, MaterialTheme.shapes.medium) else Modifier)
                        .clickable { onSelect(index) }
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                )
            }
        }
        editor.issues.take(4).forEach { issue ->
            Text("Conflito: ${editor.words.getOrNull(issue.wordIndex)?.text ?: "?"} — ${issue.message}", color = MaterialTheme.colorScheme.error)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = { onZoom(1f) }) { Text("Zoom 1×") }
            FilledTonalButton(onClick = { onZoom(2f) }) { Text("2×") }
            FilledTonalButton(onClick = { onZoom(4f) }) { Text("4×") }
        }
        val scroll = rememberScrollState()
        Box(Modifier.fillMaxWidth().horizontalScroll(scroll)) {
            WaveformCanvas(
                peaks = waveform,
                durationMs = editor.durationMs.coerceAtLeast(waveform?.durationMs ?: 1),
                positionMs = positionMs,
                selected = selected,
                zoom = zoom,
                onSeek = onSeek,
                onIntervalChange = onIntervalChange,
            )
        }
        selected?.let { word ->
            Text(
                "Selecionada: «${word.text}»  ·  ${word.resolvedState()}" +
                    if (word.isTimed) "  ·  ${word.startMs}–${word.endMs} ms" else "  ·  sem tempo",
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { if (playing) onPause() else onPlayAll() }) { Text(if (playing) "Pausar" else "Ouvir tudo") }
            FilledTonalButton(onClick = onPlayWord, enabled = selected?.isTimed == true) { Text("Ouvir palavra") }
            FilledTonalButton(onClick = onPlayFromSelected, enabled = selected != null) { Text("Ouvir a partir daqui") }
            FilledTonalButton(onClick = onToggleLoop, enabled = selected?.isTimed == true) {
                Text(if (looping) "Parar repetição" else "Repetir intervalo")
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SpeedChip("0,5×", 0.5f, playbackSpeed, onSpeed)
            SpeedChip("0,75×", 0.75f, playbackSpeed, onSpeed)
            SpeedChip("1×", 1f, playbackSpeed, onSpeed)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = onMarkStart, enabled = selected != null) { Text("Início aqui") }
            FilledTonalButton(onClick = onMarkEnd, enabled = selected != null) { Text("Fim aqui") }
            listOf(-100L, -50L, 50L, 100L).forEach { delta ->
                FilledTonalButton(onClick = { onNudge(IntervalEdge.START, delta) }, enabled = selected != null) {
                    Text("I${if (delta > 0) "+" else ""}$delta")
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(-100L, -50L, 50L, 100L).forEach { delta ->
                FilledTonalButton(onClick = { onNudge(IntervalEdge.END, delta) }, enabled = selected != null) {
                    Text("F${if (delta > 0) "+" else ""}$delta")
                }
            }
            IconButton(onClick = onUndo, enabled = editor.canUndo) { Icon(Icons.AutoMirrored.Filled.Undo, "Desfazer") }
            IconButton(onClick = onRedo, enabled = editor.canRedo) { Icon(Icons.AutoMirrored.Filled.Redo, "Refazer") }
            Button(onClick = onVerify, enabled = selected?.isTimed == true) { Text("Validar palavra") }
        }
        Text("Teclado: ← → palavra  ·  Ctrl+Z/Y desfazer  ·  [ ] início  ·  ; ' fim  ·  Espaço ouvir  ·  Enter validar")
    }
}

@Composable
private fun Legend(label: String, color: Color) {
    Text(label, modifier = Modifier.background(color, MaterialTheme.shapes.small).padding(horizontal = 8.dp, vertical = 4.dp), color = Color.Black)
}

@Composable
private fun SpeedChip(label: String, speed: Float, current: Float, onSpeed: (Float) -> Unit) {
    FilterChip(selected = current == speed, onClick = { onSpeed(speed) }, label = { Text(label) })
}

@Composable
private fun WaveformCanvas(
    peaks: WaveformPeaks?,
    durationMs: Long,
    positionMs: Long,
    selected: WordTiming?,
    zoom: Float,
    onSeek: (Long) -> Unit,
    onIntervalChange: (Long, Long) -> Unit,
) {
    val duration = durationMs.coerceAtLeast(1)
    var previewStart by remember(selected?.id, selected?.startMs, selected?.endMs) { mutableStateOf(selected?.startMs) }
    var previewEnd by remember(selected?.id, selected?.startMs, selected?.endMs) { mutableStateOf(selected?.endMs) }
    val width = (900 * zoom).dp
    Box(
        Modifier.width(width).height(130.dp).background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.medium)
            .pointerInput(duration, selected?.id, zoom) {
                detectTapGestures { offset ->
                    onSeek(((offset.x / size.width) * duration).toLong().coerceIn(0, duration))
                }
            }
            .pointerInput(duration, selected?.id, previewStart, previewEnd, zoom) {
                detectDragGestures(
                    onDrag = { change, _ ->
                        val ms = ((change.position.x / size.width.toFloat().coerceAtLeast(1f)) * duration).toLong().coerceIn(0, duration)
                        val startX = ((previewStart ?: 0).toFloat() / duration) * size.width
                        val endX = ((previewEnd ?: duration).toFloat() / duration) * size.width
                        if (abs(change.position.x - startX) <= abs(change.position.x - endX)) {
                            previewStart = ms
                            if (previewEnd == null || previewEnd!! <= ms) previewEnd = (ms + 200).coerceAtMost(duration)
                        } else {
                            previewEnd = ms.coerceAtLeast((previewStart ?: 0) + 20)
                        }
                    },
                    onDragEnd = {
                        val start = previewStart
                        val end = previewEnd
                        if (start != null && end != null) onIntervalChange(start, end)
                    },
                )
            },
    ) {
        Canvas(Modifier.fillMaxWidth().height(130.dp).padding(8.dp)) {
            val values = peaks?.peaks ?: FloatArray(64)
            val barWidth = size.width / values.size.coerceAtLeast(1)
            values.forEachIndexed { index, peak ->
                val h = peak.coerceIn(0.04f, 1f) * size.height
                drawRect(Color(0xFF1A4B84).copy(alpha = 0.45f), Offset(index * barWidth, (size.height - h) / 2f), Size(barWidth * 0.7f, h))
            }
            val start = previewStart
            val end = previewEnd
            if (start != null && end != null) {
                val x0 = (start.toFloat() / duration) * size.width
                val x1 = (end.toFloat() / duration) * size.width
                drawRect(Color(0x6681D4FA), Offset(x0, 0f), Size((x1 - x0).coerceAtLeast(4f), size.height))
                drawRect(Color(0xFF0277BD), Offset(x0 - 5f, 0f), Size(10f, size.height))
                drawRect(Color(0xFF0277BD), Offset(x1 - 5f, 0f), Size(10f, size.height))
            }
            val playX = (positionMs.toFloat() / duration) * size.width
            drawLine(Color(0xFF1F7A6B), Offset(playX, 0f), Offset(playX, size.height), strokeWidth = 3f)
        }
    }
}

private fun stateColor(state: WordState): Color = when (state) {
    WordState.AUTO -> Color(0xFFB3E5FC)
    WordState.REVIEW -> Color(0xFFFFCC80)
    WordState.MANUAL -> Color(0xFFCE93D8)
    WordState.VERIFIED -> Color(0xFFA5D6A7)
}
