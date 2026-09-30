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
    val night: Boolean,
)

val LightPaneColors = PaneColors(
    ground = Color(0xFFE8F2EA),
    ink = Color(0xFF1C2822),
    accent = Color(0xFF2E7D5A),
    secondary = Color(0xFF4A6B5C),
    muted = Color(0xFF5F7368),
    border = Color(0xFFC9D8CE),
    tile = Color(0xFFD5E6DB),
    chip = Color(0xFFE4F0E8),
    fill = Color(0xFF2E7D5A),
    onFill = Color.White,
    night = false,
)

val DarkPaneColors = PaneColors(
    ground = Color(0xFF121C16),
    ink = Color(0xFFE4EDE6),
    accent = Color(0xFF6EC496),
    secondary = Color(0xFF9AB5A6),
    muted = Color(0xFF9AADA3),
    border = Color(0xFF2A3830),
    tile = Color(0xFF1A2620),
    chip = Color(0xFF222E28),
    fill = Color(0xFF6EC496),
    onFill = Color(0xFF121C16),
    night = true,
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
