package app.pane.android.domain.text

import java.net.URI

/** Visible label for a link whose display text is empty or only punctuation. */
object LinkLabels {
    fun display(label: String, url: String): String {
        val trimmed = label.trim()
        if (trimmed.isNotEmpty() && !punctuationOnly(trimmed) && !trimmed.contains("t.co", ignoreCase = true)) {
            return trimmed
        }
        return domain(url) ?: middle(url)
    }

    fun punctuationOnly(text: String): Boolean {
        if (text.isEmpty()) return false
        return text.all { char ->
            char.isWhitespace() || char in ".,;:!?…·•-–—/\\|\"'`“”‘’()[]{}<>"
        }
    }

    fun domain(url: String): String? {
        val raw = url.trim()
        if (raw.isEmpty()) return null
        val withScheme = if (raw.startsWith("www.")) "https://$raw" else raw
        val host = runCatching { URI(withScheme).host }.getOrNull()?.trim()?.removePrefix("www.")
        return host?.takeIf { it.isNotEmpty() && '.' in it }
    }

    fun middle(url: String, max: Int = 48): String {
        val value = url.trim()
        if (value.length <= max) return value
        val keep = (max - 1) / 2
        return value.take(keep) + "…" + value.takeLast(max - keep - 1)
    }
}
