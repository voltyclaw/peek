package app.pane.android.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

data class PaneColors(
    val ground: Color,
    val ink: Color,
    val accent: Color,
    val secondary: Color,
    val muted: Color,
    val border: Color,
    val tile: Color,
    val chip: Color,
    val fill: Color,
    val onFill: Color,
)

val LightPaneColors = PaneColors(
    ground = Color(0xFFF4F1EA),
    ink = Color(0xFF1A1C1E),
    accent = Color(0xFF3B5BDB),
    secondary = Color(0xFF5A6B8A),
    muted = Color(0xFF686D7E),
    border = Color(0xFFDDD8CE),
    tile = Color(0xFFE6E2D8),
    chip = Color(0xFFEEEBE3),
    fill = Color(0xFF3B5BDB),
    onFill = Color.White,
)

val DarkPaneColors = PaneColors(
    ground = Color(0xFF12141A),
    ink = Color(0xFFEDEAE3),
    accent = Color(0xFF7B93F0),
    secondary = Color(0xFF9AA8C8),
    muted = Color(0xFF9AA3B5),
    border = Color(0xFF2A2E38),
    tile = Color(0xFF1C2028),
    chip = Color(0xFF242830),
    fill = Color(0xFF7B93F0),
    onFill = Color.White,
)

val LocalPaneColors = staticCompositionLocalOf { LightPaneColors }

val PaneGround: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalPaneColors.current.ground

val PaneInk: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalPaneColors.current.ink

val PaneAccent: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalPaneColors.current.accent

val PaneSecondary: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalPaneColors.current.secondary

val PaneMuted: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalPaneColors.current.muted

val PaneBorder: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalPaneColors.current.border

val PaneTile: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalPaneColors.current.tile

val PaneChip: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalPaneColors.current.chip

val PaneFill: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalPaneColors.current.fill

val PaneOnFill: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalPaneColors.current.onFill
