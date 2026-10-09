package app.pane.android.domain.model

import app.pane.android.domain.text.UnicodeEscapes

/** One-time rewrite of escaped Facebook text and domain-only X titles. Idempotent. */
object StoredTextRepair {
    data class HistoryFields(
        val title: String,
        val authorName: String,
        val handle: String,
        val caption: String,
    )

    fun history(source: SourceApp, url: String, fields: HistoryFields): HistoryFields? {
        val decoded = HistoryFields(
            title = UnicodeEscapes.decode(fields.title),
            authorName = UnicodeEscapes.decode(fields.authorName),
            handle = UnicodeEscapes.decode(fields.handle),
            caption = UnicodeEscapes.decode(fields.caption),
        )
        val titled = if (source == SourceApp.X) {
            decoded.copy(
                title = RowTitles.xTitle(decoded.title, decoded.caption, url, handle = decoded.handle),
            )
        } else {
            decoded
        }
        return titled.takeIf { it != fields }
    }

    fun linkContent(content: LinkContent): LinkContent? {
        val authorName = UnicodeEscapes.decode(content.author.name)
        val metadata = UnicodeEscapes.decode(content.author.metadata)
        val rawTitle = UnicodeEscapes.decode(content.title)
        val named = UnicodeEscapes.decode(namedFallback(content))
        val title = if (content.source == LinkSource.X) {
            RowTitles.xTitle(rawTitle, rawTitle, content.url, named, handleOf(content, metadata))
        } else {
            rawTitle
        }
        if (title == content.title && authorName == content.author.name && metadata == content.author.metadata) {
            return null
        }
        return content.copy(title = title, author = content.author.copy(name = authorName, metadata = metadata))
    }

    private fun namedFallback(content: LinkContent): String {
        val meta = content.sourceMetadata as? ExternalPostMetadata ?: return ""
        return meta.articleTitle?.takeIf { it.isNotBlank() }
            ?: meta.linkCards.firstOrNull { it.title.isNotBlank() }?.title
            ?: meta.linkCards.firstOrNull { it.label.isNotBlank() }?.label
            ?: ""
    }

    private fun handleOf(content: LinkContent, metadata: String): String {
        val fromMeta = when (val source = content.sourceMetadata) {
            is TikTokMetadata -> source.handle
            is YouTubeMetadata -> source.handle
            is InstagramMetadata -> source.authorUsername
            is BlueskyMetadata -> source.handle
            else -> ""
        }
        if (fromMeta.isNotBlank()) return fromMeta.removePrefix("@").trim()
        val line = metadata.trim()
        if (!line.startsWith("@")) return ""
        return line.removePrefix("@").substringBefore(' ').substringBefore('·').trim()
    }
}
