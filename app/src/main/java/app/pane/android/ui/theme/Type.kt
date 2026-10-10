package app.pane.android.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import app.pane.android.R
import app.pane.android.ui.theme.tokens.PaneTokenType

/**
 * Variable Inter (OFL). Registered weights include 560 and 640, which the static cuts did not have.
 */
val Inter = FontFamily(
    Font(R.font.inter_variable, FontWeight.Normal),
    Font(R.font.inter_variable, FontWeight.Medium),
    Font(R.font.inter_variable, FontWeight(560)),
    Font(R.font.inter_variable, FontWeight.SemiBold),
    Font(R.font.inter_variable, FontWeight(640)),
)

/** T2 display. Tracking belongs on this face only. Variable Inter Tight supplies 600 as well as 500. */
val PaneDisplay = FontFamily(
    Font(R.font.inter_tight_variable, FontWeight.Medium),
    Font(R.font.inter_tight_variable, FontWeight.SemiBold),
)

/** Smallest T2 text style. Replaces the old tracked 8sp meta. */
val PaneMeta: TextStyle = PaneTokenType.Meta(PaneDisplay, Inter)

/** T2 scale in the Material slots. Nothing here is the default Typography. */
internal fun paneTypography(): Typography {
    val display = PaneTokenType.Display(PaneDisplay, Inter)
    val titlePage = PaneTokenType.TitlePage(PaneDisplay, Inter)
    val title = PaneTokenType.Title(PaneDisplay, Inter)
    val body = PaneTokenType.Body(PaneDisplay, Inter)
    val detail = PaneTokenType.BodyDetail(PaneDisplay, Inter)
    val label = PaneTokenType.Label(PaneDisplay, Inter)
    val meta = PaneMeta
    return Typography(
        displayLarge = display,
        displayMedium = display,
        displaySmall = titlePage,
        headlineLarge = title,
        headlineMedium = titlePage,
        headlineSmall = title,
        titleLarge = title,
        titleMedium = titlePage,
        titleSmall = label,
        bodyLarge = body,
        bodyMedium = detail,
        bodySmall = meta,
        labelLarge = label,
        labelMedium = label,
        labelSmall = meta,
    )
}
