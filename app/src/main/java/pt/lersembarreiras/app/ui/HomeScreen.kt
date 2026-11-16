package pt.lersembarreiras.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun HomeScreen(
    onTeacher: () -> Unit,
    onStudent: () -> Unit,
    onAbout: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Ler sem Barreiras",
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = "Textos e narração sincronizados, no próprio tablet, sem internet.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(36.dp))
        Button(
            onClick = onTeacher,
            modifier = Modifier.fillMaxWidth().height(72.dp).semantics { contentDescription = "Abrir modo professor" },
        ) {
            Text("Modo Professor", fontSize = 22.sp)
        }
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = onStudent,
            modifier = Modifier.fillMaxWidth().height(72.dp).semantics { contentDescription = "Abrir modo aluno" },
        ) {
            Text("Modo Aluno", fontSize = 22.sp)
        }
        Spacer(Modifier.height(24.dp))
        FilledTonalButton(onClick = onAbout, modifier = Modifier.fillMaxWidth().height(56.dp)) {
            Text("Sobre o projeto")
        }
        Spacer(Modifier.height(24.dp))
        TextButton(onClick = onAbout) {
            Text("Escola Gil Vicente  ·  HXB, parceiro tecnológico")
        }
    }
}
