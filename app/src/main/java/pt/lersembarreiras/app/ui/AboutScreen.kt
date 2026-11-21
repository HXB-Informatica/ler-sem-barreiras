package pt.lersembarreiras.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import pt.lersembarreiras.app.BuildConfig

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sobre") },
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
        ) {
            Text("Ler sem Barreiras", style = MaterialTheme.typography.headlineSmall)
            Text("Versão ${BuildConfig.VERSION_NAME}", modifier = Modifier.padding(top = 8.dp, bottom = 16.dp))
            Text(
                "Iniciativa educativa do Agrupamento de Escolas Gil Vicente, Portugal, " +
                    "destinada a apoiar crianças com dificuldades de leitura, comunicação e aprendizagem.",
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                "A HXB Informática e Tecnologia, Unipessoal Lda. participa como parceiro tecnológico, " +
                    "disponibilizando o desenvolvimento de forma voluntária e sem custos. " +
                    "O projeto pertence à iniciativa da escola. A HXB não reivindica titularidade exclusiva.",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(top = 12.dp),
            )
            Text(
                "A aplicação é gratuita, de código aberto (GNU GPL v3.0) e funciona integralmente offline. " +
                    "Não existem contas, publicidade, analytics nem envio de dados de alunos para o exterior.",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(top = 12.dp),
            )
            Text(
                "Os direitos de autor sobre o código pertencem aos respetivos autores e colaboradores, " +
                    "licenciados nos termos da GNU GPL v3.0. Não se presume qualquer cessão de direitos " +
                    "ainda não formalizada.",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
    }
}
