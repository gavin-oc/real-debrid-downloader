package com.realdebrid.downloader.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import com.realdebrid.downloader.data.repository.SettingsRepository
import com.realdebrid.downloader.ui.navigation.AppNavigation
import com.realdebrid.downloader.ui.theme.RealDebridTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var settingsRepository: SettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        val sharedLink = handleIntent(intent)
        
        setContent {
            val darkMode by settingsRepository.darkMode.collectAsState(initial = "system")
            val useDarkTheme = when (darkMode) {
                "dark" -> true
                "light" -> false
                else -> isSystemInDarkTheme()
            }

            RealDebridTheme(darkTheme = useDarkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation(sharedLink = sharedLink)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)?.let { link ->
            // Handle new shared link
        }
    }

    private fun handleIntent(intent: Intent): String? {
        return when (intent.action) {
            Intent.ACTION_SEND -> {
                intent.getStringExtra(Intent.EXTRA_TEXT)
            }
            Intent.ACTION_VIEW -> {
                intent.dataString
            }
            else -> null
        }
    }
}
