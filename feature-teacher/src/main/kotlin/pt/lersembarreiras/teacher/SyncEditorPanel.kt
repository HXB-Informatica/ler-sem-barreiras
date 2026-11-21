package pt.lersembarreiras.teacher

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import pt.lersembarreiras.audio.WaveformPeaks
import pt.lersembarreiras.core.model.WordState
import pt.lersembarreiras.core.model.WordTiming
import pt.lersembarreiras.editor.IntervalEdge
import pt.lersembarreiras.editor.SyncEditorState

@Composable
fun SyncEditorPanel(
    sourceText: String,
    editor: SyncEditorState,
    waveform: WaveformPeaks?,
    waveformLoading: Boolean,
    playbackPositionMs: Long,
    playbackSpeed: Float,
    playingWordIndex: Int,
    playing: Boolean,
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
    onPause: () -> Unit,
    onSpeed: (Float) -> Unit,
) {
    val selected = editor.selected
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        Text("Editor de sincronização", style = MaterialTheme.typography.titleLarge)
        Text(
            "Toque numa palavra, ouça a narração e ajuste os marcadores na forma de onda. " +
                "As correções confirmadas são guardadas neste tablet.",
            style = MaterialTheme.typography.bodyMedium,
        )
        StateLegend()
        Text(sourceText, style = MaterialTheme.typography.bodyLarge)
        EditorText(
            words = editor.words,
            selectedIndex = editor.selectedIndex,
            playingIndex = playingWordIndex,
            onSelect = onSelect,
        )
        if (waveformLoading) {
            Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            WaveformView(
                peaks = waveform,
                durationMs = editor.durationMs,
                positionMs = playbackPositionMs,
                selected = selected,
                onSeek = onSeek,
                onIntervalChange = onIntervalChange,
            )
        }
        selected?.let { word ->
            Text(
                "Selecionada: «${word.text}»  ·  ${stateLabel(word.resolvedState())}" +
                    if (word.isTimed) "  ·  ${word.startMs}–${word.endMs} ms" else "  ·  sem tempo",
                style = MaterialTheme.typography.titleMedium,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = { if (playing) onPause() else onPlayAll() },
                modifier = Modifier.weight(1f).height(56.dp).semantics { contentDescription = "Reproduzir gravação inteira" },
            ) { Text(if (playing) "Pausar" else "Ouvir tudo") }
            FilledTonalButton(
                onClick = onPlayWord,
                enabled = selected?.isTimed == true,
                modifier = Modifier.weight(1f).height(56.dp).semantics { contentDescription = "Ouvir palavra selecionada" },
            ) { Text("Ouvir palavra") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SpeedChip("0,5×", 0.5f, playbackSpeed, onSpeed)
            SpeedChip("0,75×", 0.75f, playbackSpeed, onSpeed)
            SpeedChip("1×", 1f, playbackSpeed, onSpeed)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            FilledTonalButton(onClick = onMarkStart, enabled = selected != null, modifier = Modifier.weight(1f).height(52.dp)) {
                Text("Início aqui")
            }
            FilledTonalButton(onClick = onMarkEnd, enabled = selected != null, modifier = Modifier.weight(1f).height(52.dp)) {
                Text("Fim aqui")
            }
        }
        Text("Ajuste fino do início", style = MaterialTheme.typography.labelLarge)
        FineAdjustRow { delta -> onNudge(IntervalEdge.START, delta) }
        Text("Ajuste fino do fim", style = MaterialTheme.typography.labelLarge)
        FineAdjustRow { delta -> onNudge(IntervalEdge.END, delta) }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            IconButton(onClick = onUndo, enabled = editor.canUndo, modifier = Modifier.semantics { contentDescription = "Desfazer" }) {
                Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = null)
            }
            IconButton(onClick = onRedo, enabled = editor.canRedo, modifier = Modifier.semantics { contentDescription = "Refazer" }) {
                Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = null)
            }
            Button(
                onClick = onVerify,
                enabled = selected?.isTimed == true,
                modifier = Modifier.weight(1f).height(56.dp),
            ) { Text("Validar palavra") }
        }
        editor.issues.filter { it.wordIndex == editor.selectedIndex }.forEach { issue ->
            Text(issue.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun StateLegend() {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
    ) {
        LegendChip("AUTO", autoColor())
        LegendChip("REVIEW", reviewColor())
        LegendChip("MANUAL", manualColor())
        LegendChip("VERIFIED", verifiedColor())
    }
}

@Composable
private fun LegendChip(label: String, color: Color) {
    Text(
        label,
        modifier = Modifier.background(color, MaterialTheme.shapes.small).padding(horizontal = 10.dp, vertical = 6.dp),
        style = MaterialTheme.typography.labelLarge,
        color = Color.Black,
    )
}

@Composable
private fun SpeedChip(label: String, speed: Float, current: Float, onSpeed: (Float) -> Unit) {
    FilterChip(
        selected = current == speed,
        onClick = { onSpeed(speed) },
        label = { Text(label) },
        modifier = Modifier.height(48.dp).semantics { contentDescription = "Velocidade $label" },
    )
}

@Composable
private fun FineAdjustRow(onNudge: (Long) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        listOf(-100L, -50L, 50L, 100L).forEach { delta ->
            FilledTonalButton(
                onClick = { onNudge(delta) },
                modifier = Modifier.weight(1f).height(52.dp).semantics {
                    contentDescription = if (delta < 0) "Recuar ${-delta} milissegundos" else "Avançar $delta milissegundos"
                },
            ) {
                Text(if (delta < 0) "$delta" else "+$delta")
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EditorText(
    words: List<WordTiming>,
    selectedIndex: Int,
    playingIndex: Int,
    onSelect: (Int) -> Unit,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Texto para sincronizar" },
    ) {
        words.forEachIndexed { index, word ->
            val background = when {
                index == playingIndex -> Color(0xFFFFF176)
                else -> stateColor(word.resolvedState())
            }
            val selected = index == selectedIndex
            Text(
                text = word.text,
                color = Color.Black,
                fontWeight = if (selected || index == playingIndex) FontWeight.Bold else FontWeight.Normal,
                fontSize = 22.sp,
                modifier = Modifier
                    .background(background, MaterialTheme.shapes.medium)
                    .then(if (selected) Modifier.border(3.dp, Color.Black, MaterialTheme.shapes.medium) else Modifier)
                    .clickable { onSelect(index) }
                    .padding(horizontal = 12.dp, vertical = 10.dp)
                    .semantics { contentDescription = "Palavra ${word.text}" },
            )
        }
    }
}

@Composable
private fun WaveformView(
    peaks: WaveformPeaks?,
    durationMs: Long,
    positionMs: Long,
    selected: WordTiming?,
    onSeek: (Long) -> Unit,
    onIntervalChange: (Long, Long) -> Unit,
) {
    val duration = durationMs.coerceAtLeast(peaks?.durationMs ?: 0).coerceAtLeast(1)
    var previewStart by remember(selected?.id, selected?.startMs, selected?.endMs) { mutableStateOf(selected?.startMs) }
    var previewEnd by remember(selected?.id, selected?.startMs, selected?.endMs) { mutableStateOf(selected?.endMs) }
    val barColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
    val playhead = MaterialTheme.colorScheme.primary
    val interval = Color(0x6681D4FA)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(140.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.medium)
            .semantics { contentDescription = "Forma de onda da narração" }
            .pointerInput(duration, selected?.id) {
                detectTapGestures { offset ->
                    val ms = ((offset.x / size.width) * duration).toLong().coerceIn(0, duration)
                    onSeek(ms)
                }
            }
            .pointerInput(duration, selected?.id, previewStart, previewEnd) {
                detectDragGestures(
                    onDragStart = { offset -> /* hit-test done in onDrag */ },
                    onDrag = { change, _ ->
                        val width = size.width.toFloat().coerceAtLeast(1f)
                        val ms = ((change.position.x / width) * duration).toLong().coerceIn(0, duration)
                        val startX = ((previewStart ?: 0).toFloat() / duration) * width
                        val endX = ((previewEnd ?: duration).toFloat() / duration) * width
                        val handle = when {
                            previewStart == null && previewEnd == null -> IntervalEdge.START
                            abs(change.position.x - startX) <= abs(change.position.x - endX) -> IntervalEdge.START
                            else -> IntervalEdge.END
                        }
                        if (handle == IntervalEdge.START) {
                            previewStart = ms
                            if (previewEnd == null || previewEnd!! <= ms) previewEnd = (ms + 200).coerceAtMost(duration)
                        } else {
                            previewEnd = ms.coerceAtLeast((previewStart ?: 0) + 20)
                            if (previewStart == null) previewStart = (ms - 200).coerceAtLeast(0)
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
        Canvas(Modifier.fillMaxWidth().height(140.dp).padding(horizontal = 8.dp, vertical = 8.dp)) {
            val values = peaks?.peaks ?: FloatArray(48)
            val barWidth = size.width / values.size.coerceAtLeast(1)
            values.forEachIndexed { index, peak ->
                val h = (peak.coerceIn(0.04f, 1f) * size.height)
                drawRect(
                    color = barColor,
                    topLeft = Offset(index * barWidth, (size.height - h) / 2f),
                    size = Size(barWidth * 0.72f, h),
                )
            }
            val start = previewStart
            val end = previewEnd
            if (start != null && end != null) {
                val x0 = (start.toFloat() / duration) * size.width
                val x1 = (end.toFloat() / duration) * size.width
                drawRect(interval, topLeft = Offset(x0, 0f), size = Size((x1 - x0).coerceAtLeast(4f), size.height))
                drawRect(Color(0xFF0277BD), topLeft = Offset(x0 - 6f, 0f), size = Size(12f, size.height))
                drawRect(Color(0xFF0277BD), topLeft = Offset(x1 - 6f, 0f), size = Size(12f, size.height))
            }
            val playX = (positionMs.toFloat() / duration) * size.width
            drawLine(playhead, Offset(playX, 0f), Offset(playX, size.height), strokeWidth = 4f)
        }
    }
}

@Composable
private fun autoColor() = Color(0xFFB3E5FC)

@Composable
private fun reviewColor() = Color(0xFFFFCC80)

@Composable
private fun manualColor() = Color(0xFFCE93D8)

@Composable
private fun verifiedColor() = Color(0xFFA5D6A7)

private fun stateColor(state: WordState): Color = when (state) {
    WordState.AUTO -> Color(0xFFB3E5FC)
    WordState.REVIEW -> Color(0xFFFFCC80)
    WordState.MANUAL -> Color(0xFFCE93D8)
    WordState.VERIFIED -> Color(0xFFA5D6A7)
}

private fun stateLabel(state: WordState): String = state.name
