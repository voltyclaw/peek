package app.pane.android.ui.text

import android.text.BidiFormatter
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.LayoutDirection

/** User content follows its own direction. Alignment stays at the start. */
fun TextStyle.userContent(): TextStyle = copy(
    textDirection = TextDirection.Content,
    textAlign = TextAlign.Start,
)

/**
 * Ledger rows stay aligned with the row. Hebrew text keeps its glyph order
 * without moving the line to the trailing edge of an LTR row.
 */
@Composable
fun TextStyle.ledgerRow(): TextStyle {
    val align = if (LocalLayoutDirection.current == LayoutDirection.Ltr) TextAlign.Left else TextAlign.Right
    return copy(textDirection = TextDirection.Content, textAlign = align)
}

fun ledgerBidi(text: String): String = BidiFormatter.getInstance().unicodeWrap(text)

object BidiText {
    const val FSI = "\u2068"
    const val PDI = "\u2069"

    fun isolate(value: String): String = "$FSI$value$PDI"

    fun join(parts: List<String>, separator: String = " · "): String =
        parts.filter { it.isNotBlank() }.joinToString(separator) { isolate(it) }
}
