package app.pane.android.domain.text

import java.text.BreakIterator
import java.util.Locale

/** First user-perceived character, for a letter avatar. */
fun firstGrapheme(name: String): String {
    val trimmed = name.trim().removePrefix("@").removePrefix("r/").trim()
    if (trimmed.isEmpty()) return ""
    val iterator = BreakIterator.getCharacterInstance(Locale.ROOT)
    iterator.setText(trimmed)
    val start = iterator.first()
    val end = iterator.next()
    if (end == BreakIterator.DONE || end <= start) return ""
    val cluster = trimmed.substring(start, end)
    return if (cluster.length == 1 && cluster[0].isLetter()) cluster.uppercase(Locale.ROOT) else cluster
}
