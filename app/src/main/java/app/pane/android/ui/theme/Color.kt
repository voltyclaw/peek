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
    ground = Color(0xFFF3F6F3),
    ink = Color(0xFF1E2A22),
    accent = Color(0xFF3A8F6A),
    secondary = Color(0xFF7FAE8A),
    muted = Color(0xFF6A7A6E),
    border = Color(0xFFD5DFD7),
    tile = Color(0xFFE8EEE9),
    chip = Color(0xFF8DBC76),
    fill = Color(0xFF3A8F6A),
    onFill = Color.White,
    night = false,
)

val DarkPaneColors = PaneColors(
    ground = Color(0xFF141C16),
    ink = Color(0xFFE7F0E8),
    accent = Color(0xFF9BC9A5),
    secondary = Color(0xFFA8CDB4),
    muted = Color(0xFF9AADA3),
    border = Color(0xFF2C3A32),
    tile = Color(0xFF1C2820),
    chip = Color(0xFF3A5238),
    fill = Color(0xFF9BC9A5),
    onFill = Color(0xFF141C16),
    night = true,
)

/** Cooler mint for success. Distinct from the brand greens above. */
val PaneSuccess = Color(0xFF5BA8A0)

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
