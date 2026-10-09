package app.pane.android.ui.theme

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import app.pane.android.R

val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
)

/** T2 display. Tracking belongs on this face only. */
val PaneDisplay = FontFamily(
    Font(R.font.inter_tight_medium, FontWeight.Medium),
)

val Geist = Inter

val GeistMono = Inter
