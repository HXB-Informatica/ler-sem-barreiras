package pt.lersembarreiras.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import pt.lersembarreiras.app.navigation.AppNav
import pt.lersembarreiras.app.ui.theme.LerSemBarreirasTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LerSemBarreirasTheme {
                AppNav()
            }
        }
    }
}
