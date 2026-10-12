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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
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
        viewIntentUrl.value = IncomingLink.urlFrom(intent)
        val launchedFromViewLink = IncomingLink.isExternalOpen(intent)
        val container = (application as PaneApplication).container
        setContent {
            PaneTheme(themeMode) {
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(PaneGround)
                        .semantics { testTagsAsResourceId = true },
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
        setIntent(intent)
        IncomingLink.urlFrom(intent)?.let { viewIntentUrl.value = it }
    }
}
