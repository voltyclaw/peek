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
    val handle: Color,
    val handlePressed: Color,
    val night: Boolean,
) {
    /** L1 slate at 16% over the surface. Inline pressed wash. */
    val handleWash: Color get() = handle.copy(alpha = 0.16f)

    /** Ink at 6% over the surface. Header and reply row press. */
    val rowWash: Color get() = ink.copy(alpha = 0.06f)
}

/** M2w Warm Coral. This pass is dark-only, so light and dark share the night palette. */
private val M2w = PaneColors(
    ground = Color(0xFF0E0B0A),
    ink = Color(0xFFF4EFEA),
    accent = Color(0xFFD4886A),
    secondary = Color(0xFF8A827A),
    muted = Color(0xFF8A827A),
    border = Color(0xFF262018),
    tile = Color(0xFF191412),
    chip = Color(0xFF191412),
    fill = Color(0xFFD4886A),
    onFill = Color(0xFF0E0B0A),
    handle = Color(0xFF9DB4CC),
    handlePressed = Color(0xFFC3D2E1),
    night = true,
)

val LightPaneColors = M2w

val DarkPaneColors = M2w

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

/** L1 slate. Account handles only. History and hub rows stay mute. */
val PaneHandle: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalPaneColors.current.handle

val PaneHandlePressed: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalPaneColors.current.handlePressed

val PaneHandleWash: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalPaneColors.current.handleWash

val PaneRowWash: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalPaneColors.current.rowWash
