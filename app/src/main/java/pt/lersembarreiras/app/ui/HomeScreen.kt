package pt.lersembarreiras.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pt.lersembarreiras.app.R
import pt.lersembarreiras.app.about.ProjectInfo
import pt.lersembarreiras.app.ui.theme.LsbColors

@Composable
fun HomeScreen(
    onTeacher: () -> Unit,
    onStudent: () -> Unit,
    onAbout: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 28.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            painter = painterResource(R.drawable.lsb_brand),
            contentDescription = "Ícone do Ler sem Barreiras",
            modifier = Modifier.size(128.dp).clip(RoundedCornerShape(28.dp)),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = ProjectInfo.NAME,
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = ProjectInfo.TAGLINE,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(36.dp))
        Button(
            onClick = onStudent,
            colors = ButtonDefaults.buttonColors(containerColor = LsbColors.Teal),
            modifier = Modifier.fillMaxWidth().height(72.dp).semantics { contentDescription = "Abrir modo aluno, para ler e ouvir textos" },
        ) {
            Text("Modo Aluno", fontSize = 22.sp)
        }
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = onTeacher,
            colors = ButtonDefaults.buttonColors(containerColor = LsbColors.Primary),
            modifier = Modifier.fillMaxWidth().height(72.dp).semantics { contentDescription = "Abrir modo professor, para criar e gerir conteúdos" },
        ) {
            Text("Modo Professor", fontSize = 22.sp)
        }
        Spacer(Modifier.height(28.dp))
        OutlinedButton(onClick = onAbout, modifier = Modifier.fillMaxWidth().height(56.dp)) {
            Text("Sobre o projeto")
        }
        Spacer(Modifier.height(24.dp))
        Text(
            text = "${ProjectInfo.SCHOOL_SHORT}  ·  ${ProjectInfo.SCHOOL_ROLE}",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
