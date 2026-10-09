package app.pane.android.domain.model

fun looksLikeUrl(text: String): Boolean {
    val trimmed = text.trim()
    return trimmed.startsWith("http://", ignoreCase = true) ||
        trimmed.startsWith("https://", ignoreCase = true) ||
        trimmed.startsWith("www.", ignoreCase = true)
}

/** Keep a real title when a later star copy is blank or is only a URL. */
fun keepText(incoming: String, existing: String): String {
    val next = incoming.trim()
    val prior = existing.trim()
    if ((next.isBlank() || looksLikeUrl(next)) && prior.isNotBlank() && !looksLikeUrl(prior)) return prior
    return next.ifBlank { prior }
}

object OtherTitles {
    fun displayTitle(title: String, caption: String, url: String): String {
        val candidate = title.trim().ifBlank { caption.trim() }
        if (candidate.isNotBlank() && !looksLikeUrl(candidate)) return candidate
        return app.pane.android.domain.text.LinkLabels.domain(url)
            ?: app.pane.android.domain.text.LinkLabels.domain(candidate)
            ?: "Link"
    }

    /** Public favicon endpoint. Coil loads it. A failure falls back to a globe. */
    fun faviconUrl(pageUrl: String): String? {
        val host = app.pane.android.domain.text.LinkLabels.domain(pageUrl) ?: return null
        return "https://www.google.com/s2/favicons?domain=$host&sz=64"
    }
}
