package com.mustafashakir.peek.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

data class PeekColors(
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

val LightPeekColors = PeekColors(
    ground = Color(0xFFF5F3EE),
    ink = Color(0xFF1B3A28),
    accent = Color(0xFF2D5E3A),
    secondary = Color(0xFF4A6B52),
    muted = Color(0xFF7A9A80),
    border = Color(0xFFD6DDD0),
    tile = Color(0xFFC8DBBC),
    chip = Color(0xFFE8ECE3),
    fill = Color(0xFF1B3A28),
    onFill = Color.White,
)

val DarkPeekColors = PeekColors(
    ground = Color(0xFF121814),
    ink = Color(0xFFE7F0E4),
    accent = Color(0xFF8FBF8A),
    secondary = Color(0xFFA8C4A4),
    muted = Color(0xFF8AA18C),
    border = Color(0xFF2C3A30),
    tile = Color(0xFF243128),
    chip = Color(0xFF1C2820),
    fill = Color(0xFFD7E8D0),
    onFill = Color(0xFF122018),
)

val LocalPeekColors = staticCompositionLocalOf { LightPeekColors }

val PeekGround: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalPeekColors.current.ground

val PeekInk: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalPeekColors.current.ink

val PeekAccent: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalPeekColors.current.accent

val PeekSecondary: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalPeekColors.current.secondary

val PeekMuted: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalPeekColors.current.muted

val PeekBorder: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalPeekColors.current.border

val PeekTile: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalPeekColors.current.tile

val PeekChip: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalPeekColors.current.chip

val PeekFill: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalPeekColors.current.fill

val PeekOnFill: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalPeekColors.current.onFill
