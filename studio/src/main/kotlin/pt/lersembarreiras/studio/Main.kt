package pt.lersembarreiras.studio

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import androidx.compose.ui.unit.dp
import pt.lersembarreiras.studio.ui.StudioApp

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Ler sem Barreiras Studio",
        state = rememberWindowState(width = 1280.dp, height = 900.dp),
    ) {
        StudioApp()
    }
}
