package pt.lersembarreiras.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pt.lersembarreiras.core.model.PresentationMode
import pt.lersembarreiras.core.model.ReadingContent
import pt.lersembarreiras.core.model.WordTiming

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderListScreen(
    viewModel: ReaderListViewModel,
    onBack: () -> Unit,
    onOpen: (String) -> Unit,
) {
    val contents by viewModel.contents.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Modo Aluno") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                },
            )
        },
    ) { padding ->
        if (contents.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Ainda não há textos prontos a ler.", style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                Spacer(Modifier.height(12.dp))
                Text("Peça a um professor para criar um conteúdo e sincronizar a narração neste tablet.", textAlign = TextAlign.Center)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(contents, key = { it.id }) { item ->
                    Card(modifier = Modifier.fillMaxWidth().clickable { onOpen(item.id) }.semantics { contentDescription = "Abrir ${item.title}" }) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Text(item.title, style = MaterialTheme.typography.headlineSmall)
                            Text("${item.words.size} palavras", style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    viewModel: ReaderViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val content = state.content
    val contrast = state.settings.highContrast
    val background = if (contrast) Color.Black else MaterialTheme.colorScheme.background
    val foreground = if (contrast) Color.White else MaterialTheme.colorScheme.onBackground
    val highlight = if (contrast) Color.Yellow else Color(0xFFFFF176)
    val highlightText = if (contrast) Color.Black else Color.Black

    Scaffold(
        containerColor = background,
        topBar = {
            TopAppBar(
                title = { Text(content?.title ?: "Leitura", color = foreground) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar", tint = foreground)
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(background)
                .padding(padding)
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (content == null) {
                Text("A carregar…", color = foreground)
                return@Column
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                contentAlignment = Alignment.Center,
            ) {
                ReadingSurface(
                    content = content,
                    mode = state.settings.presentationMode,
                    currentIndex = state.currentWordIndex,
                    fontScale = state.settings.fontScale,
                    foreground = foreground,
                    highlight = highlight,
                    highlightText = highlightText,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                ModeChip("Texto", PresentationMode.FULL_TEXT, state.settings.presentationMode, viewModel::setMode)
                ModeChip("Palavra", PresentationMode.SINGLE_WORD, state.settings.presentationMode, viewModel::setMode)
                ModeChip("Progressiva", PresentationMode.PROGRESSIVE, state.settings.presentationMode, viewModel::setMode)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilledTonalButton(onClick = viewModel::cycleFont, modifier = Modifier.height(52.dp)) {
                    Text("Letra")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Contraste", color = foreground, modifier = Modifier.padding(end = 8.dp))
                    Switch(
                        checked = contrast,
                        onCheckedChange = { viewModel.toggleContrast() },
                        modifier = Modifier.semantics { contentDescription = "Contraste elevado" },
                    )
                }
            }
            if (state.missingAudio) {
                Text("Este conteúdo não tem áudio neste tablet.", color = MaterialTheme.colorScheme.error)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = viewModel::togglePlayback,
                    enabled = !state.missingAudio,
                    modifier = Modifier.weight(1f).height(64.dp),
                ) {
                    Text(if (state.playback.playing) "Pausar" else "Iniciar", fontSize = 20.sp)
                }
                FilledTonalButton(
                    onClick = viewModel::restart,
                    enabled = !state.missingAudio,
                    modifier = Modifier.weight(1f).height(64.dp),
                ) {
                    Text("Recomeçar", fontSize = 20.sp)
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun ModeChip(
    label: String,
    mode: PresentationMode,
    current: PresentationMode,
    onSelect: (PresentationMode) -> Unit,
) {
    FilterChip(
        selected = current == mode,
        onClick = { onSelect(mode) },
        label = { Text(label) },
    )
}

@Composable
private fun ReadingSurface(
    content: ReadingContent,
    mode: PresentationMode,
    currentIndex: Int,
    fontScale: Float,
    foreground: Color,
    highlight: Color,
    highlightText: Color,
) {
    val fontSize = (26 * fontScale).sp
    when (mode) {
        PresentationMode.FULL_TEXT -> {
            Text(
                text = highlightedText(content, currentIndex, highlight, highlightText, foreground),
                fontSize = fontSize,
                lineHeight = (36 * fontScale).sp,
                color = foreground,
            )
        }
        PresentationMode.SINGLE_WORD -> {
            val word = content.words.getOrNull(currentIndex)?.text ?: content.words.firstOrNull()?.text.orEmpty()
            Text(
                text = word,
                fontSize = (48 * fontScale).sp,
                fontWeight = FontWeight.Bold,
                color = highlightText,
                modifier = Modifier
                    .background(highlight, MaterialTheme.shapes.large)
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                textAlign = TextAlign.Center,
            )
        }
        PresentationMode.PROGRESSIVE -> {
            val visible = if (currentIndex >= 0) content.words.take(currentIndex + 1) else emptyList()
            Text(
                text = progressiveText(content.text, visible, currentIndex, highlight, highlightText, foreground),
                fontSize = fontSize,
                lineHeight = (36 * fontScale).sp,
                color = foreground,
            )
        }
    }
}

private fun highlightedText(
    content: ReadingContent,
    currentIndex: Int,
    highlight: Color,
    highlightText: Color,
    foreground: Color,
) = buildAnnotatedString {
    val text = content.text
    var cursor = 0
    content.words.forEachIndexed { index, word ->
        if (word.startChar > cursor) {
            withStyle(SpanStyle(color = foreground)) {
                append(text.substring(cursor, word.startChar))
            }
        }
        val style = if (index == currentIndex) {
            SpanStyle(background = highlight, color = highlightText, fontWeight = FontWeight.Bold)
        } else {
            SpanStyle(color = foreground)
        }
        withStyle(style) {
            append(text.substring(word.startChar, word.endChar.coerceAtMost(text.length)))
        }
        cursor = word.endChar
    }
    if (cursor < text.length) {
        withStyle(SpanStyle(color = foreground)) { append(text.substring(cursor)) }
    }
}

private fun progressiveText(
    original: String,
    visible: List<WordTiming>,
    currentIndex: Int,
    highlight: Color,
    highlightText: Color,
    foreground: Color,
) = buildAnnotatedString {
    if (visible.isEmpty()) return@buildAnnotatedString
    val last = visible.last()
    val shown = original.substring(0, last.endChar.coerceAtMost(original.length))
    val current = visible.last()
    val start = current.startChar.coerceIn(0, shown.length)
    val end = current.endChar.coerceIn(start, shown.length)
    withStyle(SpanStyle(color = foreground)) { append(shown.substring(0, start)) }
    withStyle(SpanStyle(background = highlight, color = highlightText, fontWeight = FontWeight.Bold)) {
        append(shown.substring(start, end))
    }
    if (end < shown.length) {
        withStyle(SpanStyle(color = foreground)) { append(shown.substring(end)) }
    }
    if (currentIndex < 0) {
        // nothing extra
    }
}
