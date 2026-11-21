package pt.lersembarreiras.app

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import pt.lersembarreiras.app.navigation.AppNav
import pt.lersembarreiras.app.ui.theme.LerSemBarreirasTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIncoming(intent)
        enableEdgeToEdge()
        setContent {
            LerSemBarreirasTheme {
                AppNav()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncoming(intent)
    }

    private fun handleIncoming(intent: Intent?) {
        if (intent == null) return
        val uri: Uri? = when (intent.action) {
            Intent.ACTION_VIEW -> intent.data
            Intent.ACTION_SEND -> extraStream(intent)
            else -> intent.data
        }
        val looksLikeLsb = uri?.toString()?.endsWith(".lsb", ignoreCase = true) == true ||
            intent.type?.contains("zip") == true ||
            intent.type?.contains("octet") == true
        if (uri != null && looksLikeLsb) {
            (application as LerSemBarreirasApp).container.pendingLsbUri.value = uri
        }
    }

    private fun extraStream(intent: Intent): Uri? {
        return if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(Intent.EXTRA_STREAM)
        }
    }
}
