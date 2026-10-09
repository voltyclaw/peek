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

object RowTitles {
    private val shortLink = Regex("""https?://t\.co/\S+""", RegexOption.IGNORE_CASE)

    /** Post text with every t.co removed. A raw URL is not a title. */
    fun withoutShortLinks(text: String): String = shortLink.replace(text, " ")
        .replace(Regex("""\s+"""), " ")
        .trim()
        .trim('.', ':', '…')
        .trim()

    fun display(title: String, caption: String, pageUrl: String, named: String = ""): String {
        val cleaned = sequenceOf(title, caption)
            .map(::withoutShortLinks)
            .firstOrNull { it.isNotBlank() && !looksLikeUrl(it) }
        if (cleaned != null) return cleaned
        val fallback = named.trim()
        if (fallback.isNotBlank() && !looksLikeUrl(fallback)) return fallback
        return app.pane.android.domain.text.LinkLabels.domain(pageUrl) ?: "Link"
    }

    /** A stored host, such as `x.com`, is not the post. */
    fun isDomainOnly(text: String, pageUrl: String): Boolean {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return false
        val domain = app.pane.android.domain.text.LinkLabels.domain(pageUrl) ?: return false
        return trimmed.equals(domain, ignoreCase = true) || trimmed.equals("www.$domain", ignoreCase = true)
    }

    /**
     * X row title. Post text, then the article or card, then `Post by @handle`.
     * The domain is only the last resort.
     */
    fun xTitle(title: String, caption: String, pageUrl: String, named: String = "", handle: String = ""): String {
        val cleaned = sequenceOf(title, caption)
            .map(::withoutShortLinks)
            .firstOrNull { it.isNotBlank() && !looksLikeUrl(it) && !isDomainOnly(it, pageUrl) }
        if (cleaned != null) return cleaned
        val card = withoutShortLinks(named)
        if (card.isNotBlank() && !looksLikeUrl(card) && !isDomainOnly(card, pageUrl)) return card
        val user = handle.trim().removePrefix("@")
        if (user.isNotBlank() && !looksLikeUrl(user)) return "Post by @$user"
        return app.pane.android.domain.text.LinkLabels.domain(pageUrl) ?: "Link"
    }
}

object YouTubeRowCopy {
    fun title(known: String, videoId: String): String {
        val clean = known.trim()
        if (clean.isNotBlank() && !looksLikeUrl(clean)) return clean
        return if (videoId.isBlank()) "YouTube video" else "YouTube video $videoId"
    }

    /** Channel or oEmbed author. Blank when neither is known, so the row shows the source badge. */
    fun identity(channelOrAuthor: String, handle: String): String {
        val name = channelOrAuthor.trim()
        if (name.isNotBlank() && !looksLikeUrl(name)) return name
        val clean = handle.trim().removePrefix("@")
        return if (clean.isNotBlank()) "@$clean" else ""
    }

    fun hostLine(videoId: String): String = "youtube.com · youtu.be/$videoId"
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
