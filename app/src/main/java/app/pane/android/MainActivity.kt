package app.pane.android

import android.content.Intent
import android.os.Bundle
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import app.pane.android.ui.navigation.PeekNavigation
import app.pane.android.ui.theme.PaneGround
import app.pane.android.ui.theme.PaneTheme
import app.pane.android.ui.theme.ThemeMode
import app.pane.android.ui.theme.ThemePreferences

class MainActivity : ComponentActivity() {
    private val viewIntentUrl = mutableStateOf<String?>(null)
    private var themeMode by mutableStateOf(ThemeMode.System)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        themeMode = ThemePreferences.read(this)
        viewIntentUrl.value = extractViewUrl(intent)
        val launchedFromViewLink = intent?.action == Intent.ACTION_VIEW
        val container = (application as PaneApplication).container
        setContent {
            PaneTheme(themeMode) {
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier.fillMaxSize().background(PaneGround),
                ) {
                    PeekNavigation(
                        container = container,
                        viewIntentUrl = viewIntentUrl,
                        launchedFromViewLink = launchedFromViewLink,
                        themeMode = themeMode,
                        onThemeMode = { mode ->
                            themeMode = mode
                            ThemePreferences.write(this@MainActivity, mode)
                        },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        extractViewUrl(intent)?.let { viewIntentUrl.value = it }
    }

    private fun extractViewUrl(intent: Intent?): String? =
        intent?.takeIf { it.action == Intent.ACTION_VIEW }?.data?.toString()
}
