package app.pane.android.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

@Composable
fun PaneTheme(
    mode: ThemeMode = ThemeMode.System,
    content: @Composable () -> Unit,
) {
    val dark = mode.resolve(isSystemInDarkTheme())
    val colors = if (dark) DarkPaneColors else LightPaneColors
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !dark
            controller.isAppearanceLightNavigationBars = !dark
        }
    }
    CompositionLocalProvider(LocalPaneColors provides colors) {
        MaterialTheme(
            colorScheme = paneColorScheme(colors, dark),
            typography = Typography(),
            content = content,
        )
    }
}

private fun paneColorScheme(colors: PaneColors, dark: Boolean): ColorScheme = if (dark) {
    darkColorScheme(
        primary = colors.accent,
        onPrimary = colors.onFill,
        secondary = colors.secondary,
        onSecondary = colors.onFill,
        background = colors.ground,
        onBackground = colors.ink,
        surface = colors.ground,
        onSurface = colors.ink,
        surfaceVariant = colors.chip,
        onSurfaceVariant = colors.secondary,
        surfaceContainer = colors.chip,
        surfaceContainerHigh = colors.tile,
        surfaceContainerLow = colors.ground,
        outline = colors.border,
        outlineVariant = colors.border,
    )
} else {
    lightColorScheme(
        primary = colors.accent,
        onPrimary = colors.onFill,
        secondary = colors.secondary,
        onSecondary = Color.White,
        background = colors.ground,
        onBackground = colors.ink,
        surface = colors.ground,
        onSurface = colors.ink,
        surfaceVariant = colors.chip,
        onSurfaceVariant = colors.secondary,
        surfaceContainer = colors.chip,
        surfaceContainerHigh = colors.tile,
        surfaceContainerLow = colors.ground,
        outline = colors.border,
        outlineVariant = colors.border,
    )
}
