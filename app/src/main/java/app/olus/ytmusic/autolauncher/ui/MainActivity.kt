package app.olus.ytmusic.autolauncher.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import app.olus.ytmusic.autolauncher.YTMusicAutoLauncherApp
import app.olus.ytmusic.autolauncher.ui.compose.screens.PlaylistScreen
import app.olus.ytmusic.autolauncher.ui.compose.screens.PlaylistViewModel
import app.olus.ytmusic.autolauncher.ui.compose.theme.YTMusicAutoLauncherTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        handleIntent(intent)
        
        val app = application as YTMusicAutoLauncherApp
        
        setContent {
            YTMusicAutoLauncherTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val viewModel: PlaylistViewModel = viewModel()
                    PlaylistScreen(viewModel = viewModel)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        
        if (intent.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
            if (sharedText != null) {
                val url = extractUrl(sharedText)
                if (url != null) {
                    (application as? YTMusicAutoLauncherApp)?.sharedUrlToProcess = url
                }
            }
        }
    }

    private fun extractUrl(text: String): String? {
        val matcher = android.util.Patterns.WEB_URL.matcher(text)
        while (matcher.find()) {
            val url = matcher.group()
            if (url != null && (url.contains("youtube.com") || url.contains("youtu.be"))) {
                return url
            }
        }
        return null
    }
}
