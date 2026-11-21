package pt.lersembarreiras.editor

import pt.lersembarreiras.alignment.TimeValidator
import pt.lersembarreiras.core.model.IntervalIssue
import pt.lersembarreiras.core.model.SyncStatus
import pt.lersembarreiras.core.model.TimestampOrigin
import pt.lersembarreiras.core.model.WordState
import pt.lersembarreiras.core.model.WordTiming

enum class IntervalEdge { START, END }

data class SyncEditorState(
    val words: List<WordTiming> = emptyList(),
    val selectedIndex: Int = -1,
    val durationMs: Long = 0,
    val issues: List<IntervalIssue> = emptyList(),
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
) {
    val selected: WordTiming? get() = words.getOrNull(selectedIndex)

    val status: SyncStatus
        get() = when {
            words.isEmpty() -> SyncStatus.NONE
            words.none { it.isTimed } -> SyncStatus.FAILED
            words.any { it.requiresReview() } -> SyncStatus.COMPLETED_WITH_GAPS
            else -> SyncStatus.COMPLETED
        }
}

/**
 * Estado de edição visual: todas as palavras do texto original, undo/redo
 * e atribuição de intervalos sem alterar o PCM.
 */
class SyncEditor(
    private val historyLimit: Int = 40,
) {
    private val undo = ArrayDeque<List<WordTiming>>()
    private val redo = ArrayDeque<List<WordTiming>>()
    var snapshot: SyncEditorState = SyncEditorState()
        private set

    fun load(words: List<WordTiming>, durationMs: Long, resetHistory: Boolean = true): SyncEditorState {
        if (resetHistory) {
            undo.clear()
            redo.clear()
        }
        snapshot = refresh(words, snapshot.selectedIndex, durationMs)
        return snapshot
    }

    fun select(index: Int): SyncEditorState {
        val clamped = if (snapshot.words.isEmpty()) -1 else index.coerceIn(0, snapshot.words.lastIndex)
        snapshot = snapshot.copy(selectedIndex = clamped)
        return snapshot
    }

    fun setInterval(index: Int, startMs: Long, endMs: Long): SyncEditorState {
        return commit(index) { word ->
            val start = startMs.coerceIn(0, snapshot.durationMs.coerceAtLeast(0))
            val end = endMs.coerceAtLeast(start + 20).coerceAtMost(snapshot.durationMs.coerceAtLeast(start + 20))
            timed(word, start, end, WordState.MANUAL)
        }
    }

    fun nudge(index: Int, edge: IntervalEdge, deltaMs: Long): SyncEditorState {
        val word = snapshot.words.getOrNull(index) ?: return snapshot
        val duration = snapshot.durationMs.coerceAtLeast(0)
        val start = word.startMs ?: playbackFallbackStart(index)
        val end = word.endMs ?: (start + defaultLength(word)).coerceAtMost(duration)
        return if (edge == IntervalEdge.START) {
            setInterval(index, (start + deltaMs).coerceAtLeast(0), end)
        } else {
            setInterval(index, start, (end + deltaMs).coerceAtMost(duration))
        }
    }

    fun markStart(index: Int, positionMs: Long): SyncEditorState {
        val word = snapshot.words.getOrNull(index) ?: return snapshot
        val duration = snapshot.durationMs.coerceAtLeast(0)
        val start = positionMs.coerceIn(0, duration)
        val end = word.endMs?.takeIf { it > start } ?: (start + defaultLength(word)).coerceAtMost(duration)
        return setInterval(index, start, end.coerceAtLeast(start + 20))
    }

    fun markEnd(index: Int, positionMs: Long): SyncEditorState {
        val word = snapshot.words.getOrNull(index) ?: return snapshot
        val duration = snapshot.durationMs.coerceAtLeast(0)
        val end = positionMs.coerceIn(0, duration)
        val start = word.startMs?.takeIf { it < end } ?: (end - defaultLength(word)).coerceAtLeast(0)
        return setInterval(index, start, end.coerceAtLeast(start + 20))
    }

    fun verify(index: Int): SyncEditorState {
        return commit(index) { word ->
            if (!word.isTimed) word else word.copy(
                state = WordState.VERIFIED,
                needsReview = false,
                manuallyAdjusted = true,
                confidence = 1f,
                timestampOrigin = word.resolvedOrigin().takeIf { it != TimestampOrigin.NONE }
                    ?: TimestampOrigin.MANUAL,
            )
        }
    }

    fun undo(): SyncEditorState {
        if (undo.isEmpty()) return snapshot
        redo.addFirst(snapshot.words)
        val previous = undo.removeFirst()
        snapshot = refresh(previous, snapshot.selectedIndex, snapshot.durationMs)
        return snapshot
    }

    fun redo(): SyncEditorState {
        if (redo.isEmpty()) return snapshot
        undo.addFirst(snapshot.words)
        val next = redo.removeFirst()
        snapshot = refresh(next, snapshot.selectedIndex, snapshot.durationMs)
        return snapshot
    }

    fun playingWordIndex(positionMs: Long): Int =
        TimeValidator.currentWordIndex(snapshot.words, positionMs)

    private fun commit(index: Int, transform: (WordTiming) -> WordTiming): SyncEditorState {
        if (index !in snapshot.words.indices) return snapshot
        undo.addFirst(snapshot.words)
        while (undo.size > historyLimit) undo.removeLast()
        redo.clear()
        val updated = snapshot.words.toMutableList()
        updated[index] = transform(updated[index])
        snapshot = refresh(updated, index, snapshot.durationMs)
        return snapshot
    }

    private fun refresh(words: List<WordTiming>, selectedIndex: Int, durationMs: Long): SyncEditorState {
        val inspected = TimeValidator.inspect(words, durationMs)
        val selected = when {
            inspected.words.isEmpty() -> -1
            selectedIndex in inspected.words.indices -> selectedIndex
            else -> 0
        }
        return SyncEditorState(
            words = inspected.words,
            selectedIndex = selected,
            durationMs = durationMs,
            issues = inspected.issues,
            canUndo = undo.isNotEmpty(),
            canRedo = redo.isNotEmpty(),
        )
    }

    private fun timed(word: WordTiming, start: Long, end: Long, state: WordState): WordTiming = word.copy(
        startMs = start,
        endMs = end,
        confidence = 1f,
        manuallyAdjusted = true,
        needsReview = false,
        state = state,
        timestampOrigin = TimestampOrigin.MANUAL,
    )

    private fun defaultLength(word: WordTiming): Long =
        (word.text.length.coerceAtLeast(2) * 80L).coerceIn(160L, 600L)

    private fun playbackFallbackStart(index: Int): Long {
        val previousEnd = snapshot.words.take(index).mapNotNull { it.endMs }.maxOrNull() ?: 0L
        return previousEnd
    }
}
