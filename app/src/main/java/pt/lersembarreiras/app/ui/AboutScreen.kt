package pt.lersembarreiras.app.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import pt.lersembarreiras.app.BuildConfig
import pt.lersembarreiras.app.R
import pt.lersembarreiras.app.about.ProjectInfo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val supporters = ProjectInfo.approvedSupporters()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sobre o projeto") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Image(
                painter = painterResource(R.drawable.lsb_brand),
                contentDescription = "Ícone do Ler sem Barreiras",
                modifier = Modifier.size(96.dp).clip(RoundedCornerShape(22.dp)),
            )
            Text(ProjectInfo.NAME, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.fillMaxWidth())
            Text(
                "Versão ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.fillMaxWidth(),
            )
            AboutCard("O que é", "${ProjectInfo.NAME} é uma aplicação educativa, gratuita e integralmente offline. ${ProjectInfo.MISSION}")
            AboutCard("Origem e iniciativa", "${ProjectInfo.SCHOOL} (${ProjectInfo.SCHOOL_SHORT}) é a entidade promotora e responsável pela coordenação pedagógica.")
            AboutCard("Para quem", ProjectInfo.AUDIENCE)
            AboutCard("Como funciona", ProjectInfo.HOW_IT_WORKS)
            AboutCard("Desenvolvimento tecnológico", "${ProjectInfo.TECH_PARTNER}. ${ProjectInfo.TECH_ROLE}")
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Apoiantes e financiadores", style = MaterialTheme.typography.titleLarge)
                    if (supporters.isEmpty()) {
                        Text(
                            "Ainda não há entidades aprovadas pela escola para identificação nesta página. Nomes e logótipos só são apresentados depois dessa validação.",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    } else {
                        supporters.forEach { item ->
                            Text(item.name, style = MaterialTheme.typography.titleLarge)
                            Text(item.supportType, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
            AboutCard(
                "Código aberto",
                "A aplicação é gratuita para outras escolas e instituições. O código está publicado sob a licença ${ProjectInfo.LICENSE}. ${ProjectInfo.COPYRIGHT_NOTE}",
            )
            OutlinedButton(
                onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(ProjectInfo.GITHUB_URL))
                    runCatching { context.startActivity(intent) }
                },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            ) {
                Text("Abrir o repositório GitHub")
            }
            Text(ProjectInfo.GITHUB_URL, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun AboutCard(title: String, body: String) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
            Text(body, style = MaterialTheme.typography.bodyLarge)
        }
    }
}
