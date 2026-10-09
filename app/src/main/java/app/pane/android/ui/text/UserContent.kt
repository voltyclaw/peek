package app.pane.android.ui.text

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection

/** User content follows its own direction. Alignment stays at the start. */
fun TextStyle.userContent(): TextStyle = copy(
    textDirection = TextDirection.Content,
    textAlign = TextAlign.Start,
)

object BidiText {
    const val FSI = "\u2068"
    const val PDI = "\u2069"

    fun isolate(value: String): String = "$FSI$value$PDI"

    fun join(parts: List<String>, separator: String = " · "): String =
        parts.filter { it.isNotBlank() }.joinToString(separator) { isolate(it) }
}
