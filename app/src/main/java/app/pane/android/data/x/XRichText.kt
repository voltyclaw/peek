package app.pane.android.data.x

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

data class XUrlEntity(
    val url: String,
    val expanded: String,
    val display: String,
)

data class XArticle(
    val title: String,
    val preview: String,
    val body: String,
    val coverUrl: String?,
    val url: String,
)

data class XQuote(
    val authorName: String,
    val handle: String,
    val text: String,
    val url: String,
)

data class XLinkPreview(
    val url: String,
    val label: String,
    val title: String = "",
)

data class XRichPost(
    val text: String,
    val article: XArticle? = null,
    val quote: XQuote? = null,
    val links: List<XLinkPreview> = emptyList(),
)

/**
 * Turns a public status into text Pane can show.
 * A t.co is removed only when its target is actually on screen: attached media,
 * the quote card, or a link card. Every other t.co is replaced in place with its
 * display text, linked to the expanded URL. A removed link does not leave a
 * dangling colon or period. An unknown t.co stays, so a missing entity cannot
 * turn "Full disclosures: …" into "Full disclosures: ."
 */
internal object XRichText {
    fun present(root: JsonObject): XRichPost {
        val article = article(root)
        val quote = quote(root)
        val entities = entities(root)
        val preview = root.string("text").orEmpty()
        val note = root.obj("note_tweet")?.string("text")
        val base = when {
            !note.isNullOrBlank() && note.length > preview.length -> note
            preview.isNotBlank() -> preview
            else -> note.orEmpty()
        }
        val media = entities.filter { isMedia(it.expanded) }
        val quoteLinks = if (quote != null) entities.filter { sameStatus(it.expanded, quote.url) } else emptyList()
        val articleLinks = if (article != null) {
            entities.filter { isArticle(it.expanded) || sameTarget(it.expanded, article.url) }
        } else {
            emptyList()
        }
        val cards = if (linkOnly(base, entities)) {
            entities.filter { entity ->
                entity !in media && entity !in quoteLinks && entity !in articleLinks &&
                    entity.expanded.startsWith("http") && !isMedia(entity.expanded)
            }
        } else {
            emptyList()
        }
        val drop = buildSet {
            article?.url?.let(::add)
            quote?.url?.let(::add)
            (media + quoteLinks + articleLinks + cards).forEach { add(it.expanded) }
        }
        val text = expand(base, entities, drop, stripMedia = media.isNotEmpty())
        val links = cards.mapNotNull { entity ->
            val label = linkLabel(entity)
            if (label.isBlank() || label.contains("t.co")) return@mapNotNull null
            XLinkPreview(url = entity.expanded, label = label)
        }.distinctBy { it.url }
        return XRichPost(
            text = if (visible(text).isBlank()) "" else text,
            article = article,
            quote = quote?.copy(text = expand(quote.text, entities, drop, stripMedia = false)),
            links = links,
        )
    }

    /**
     * Root, thread, and reply text. [stripMedia] is true only when this row shows the
     * attached photo or video. [rendered] holds quote, article, and link-card URLs
     * that are on screen for this row.
     */
    fun expandShortLinks(
        text: String,
        nearby: String = "",
        stripMedia: Boolean = false,
        rendered: Set<String> = emptySet(),
    ): String {
        val entities = entitiesIn("$text\n$nearby")
        return expand(text, entities, rendered, stripMedia)
    }

    fun expand(
        text: String,
        entities: List<XUrlEntity>,
        drop: Set<String>,
        stripMedia: Boolean = false,
    ): String {
        var result = text
        entities.filter { it.url.contains("://t.co/") }.forEach { entity ->
            val rendered = shouldStrip(entity, drop, stripMedia)
            result = if (rendered) {
                stripShortLink(result, entity.url)
            } else {
                result.replace(entity.url, inlineLink(entity))
            }
        }
        return tidy(result)
    }

    /** Label text, without the markdown target. Tests and blank checks use this. */
    fun visible(text: String): String =
        MARKDOWN_LINK.replace(text) { match -> match.groupValues[1] }

    private fun shouldStrip(entity: XUrlEntity, drop: Set<String>, stripMedia: Boolean): Boolean {
        if (stripMedia && isMedia(entity.expanded)) return true
        if (entity.expanded in drop || entity.url in drop) return true
        val id = statusId(entity.expanded) ?: return false
        return drop.any { statusId(it) == id && id.isNotBlank() }
    }

    private fun inlineLink(entity: XUrlEntity): String {
        val target = entity.expanded
        if (!target.startsWith("http") || target.contains("://t.co/")) return entity.url
        val label = linkLabel(entity).ifBlank { return entity.url }
        return "[${label.replace("]", "")}]($target)"
    }

    private fun linkLabel(entity: XUrlEntity): String =
        app.pane.android.domain.text.LinkLabels.display(entity.display, entity.expanded)

    /**
     * Removes one short link. A trailing link-only tail also drops the colon or
     * period that existed only to introduce that link. A link in the middle of a
     * sentence leaves the surrounding words and their punctuation alone.
     */
    private fun stripShortLink(text: String, token: String): String {
        val start = text.indexOf(token)
        if (start < 0) return text
        val end = start + token.length
        val after = text.substring(end)
        val trailingTail = after.isBlank() || after.trim().all { it == '.' || it == '…' || it.isWhitespace() }
        val cutEnd = if (trailingTail) text.length else end
        var cutStart = start
        if (trailingTail) {
            var index = start
            while (index > 0 && text[index - 1].isWhitespace()) index -= 1
            if (index > 0 && text[index - 1] == ':') cutStart = index - 1
        }
        return text.removeRange(cutStart, cutEnd).replace(token, "")
    }

    private fun linkOnly(text: String, entities: List<XUrlEntity>): Boolean {
        var rest = text
        entities.forEach { rest = rest.replace(it.url, " ") }
        rest = TCO.replace(rest, " ")
        return rest.all { it.isWhitespace() || it == '.' || it == ':' || it == '…' }
    }

    private fun tidy(text: String): String = text
        .replace(Regex("[ \\t]{2,}"), " ")
        .replace(Regex(" *\\n *"), "\n")
        .trim()

    private fun article(root: JsonObject): XArticle? {
        val node = root.obj("article")
            ?: root.obj("article_results")?.obj("result")?.obj("article")
            ?: root.obj("article_results")?.obj("result")
            ?: return null
        val title = node.string("title") ?: return null
        val preview = node.string("preview_text") ?: node.string("preview") ?: ""
        val body = articleBody(node)
        val id = node.string("id") ?: node.string("rest_id")
        val url = node.string("url")
            ?: id?.let { "https://x.com/i/article/$it" }
            ?: return null
        return XArticle(
            title = title,
            preview = preview,
            body = body.ifBlank { preview },
            coverUrl = cover(node),
            url = url,
        )
    }

    private fun articleBody(node: JsonObject): String {
        val content = node.obj("content") ?: return ""
        val blocks = content["blocks"] as? JsonArray ?: return content.string("text").orEmpty()
        return blocks.mapNotNull { item ->
            (item as? JsonObject)?.string("text")?.trim()?.takeIf { it.isNotEmpty() }
        }.joinToString("\n\n")
    }

    private fun cover(node: JsonObject): String? {
        val media = node.obj("cover_media") ?: node.obj("cover")
        val nested = media?.obj("media_info")
        return listOf(
            nested?.string("original_img_url"),
            nested?.string("url"),
            media?.string("original_img_url"),
            media?.string("url"),
            node.string("cover_url"),
        ).firstOrNull { !it.isNullOrBlank() && it.startsWith("http") }
    }

    private fun quote(root: JsonObject): XQuote? {
        val node = root.obj("quote") ?: root.obj("quoted_tweet") ?: root.obj("quoted_status") ?: return null
        val id = node.string("id_str") ?: node.string("id") ?: return null
        val user = node.obj("author") ?: node.obj("user")
        val name = user?.string("name").orEmpty()
        val handle = user?.string("screen_name").orEmpty().removePrefix("@")
        val text = node.obj("note_tweet")?.string("text") ?: node.string("text").orEmpty()
        if (name.isBlank() && handle.isBlank() && text.isBlank()) return null
        return XQuote(
            authorName = name.ifBlank { handle },
            handle = handle,
            text = text,
            url = "https://x.com/i/status/$id",
        )
    }

    private fun entities(root: JsonObject): List<XUrlEntity> {
        val found = mutableListOf<XUrlEntity>()
        found += urlsOf(root.obj("entities"))
        found += urlsOf(root.obj("note_tweet")?.obj("entities"))
        found += entitiesIn(root.toString())
        return found.distinctBy { it.url }
    }

    private fun urlsOf(entities: JsonObject?): List<XUrlEntity> {
        val urls = entities?.get("urls") as? JsonArray ?: return emptyList()
        return urls.mapNotNull { item ->
            val obj = item as? JsonObject ?: return@mapNotNull null
            val url = obj.string("url") ?: return@mapNotNull null
            XUrlEntity(
                url = url,
                expanded = obj.string("expanded_url").orEmpty(),
                display = obj.string("display_url").orEmpty(),
            )
        }
    }

    internal fun entitiesIn(blob: String): List<XUrlEntity> {
        if (!blob.contains("t.co")) return emptyList()
        val found = LinkedHashMap<String, XUrlEntity>()
        ENTITY_PATTERNS.forEach { pattern ->
            pattern.findAll(blob).forEach { match ->
                val url = match.groupValues[1]
                if (url in found) return@forEach
                found[url] = XUrlEntity(
                    url = url,
                    expanded = unescape(match.groupValues[2]),
                    display = unescape(match.groupValues[3]),
                )
            }
        }
        return found.values.toList()
    }

    private fun unescape(value: String): String = value.replace("\\/", "/")

    private fun isMedia(url: String): Boolean {
        val lower = url.lowercase()
        if (
            "pic.twitter.com" in lower ||
            "pic.x.com" in lower ||
            "pbs.twimg.com" in lower ||
            "video.twimg.com" in lower
        ) {
            return true
        }
        val host = runCatching { java.net.URI(url).host }.getOrNull()
            ?.lowercase()
            ?.removePrefix("www.")
            ?: return false
        if (host !in MEDIA_HOSTS) return false
        return "/photo/" in lower || "/video/" in lower
    }

    private fun isArticle(url: String): Boolean = "/i/article/" in url

    private fun sameStatus(url: String, quoteUrl: String): Boolean {
        if (url == quoteUrl) return true
        val id = statusId(quoteUrl) ?: return false
        return statusId(url) == id
    }

    private fun sameTarget(url: String, other: String): Boolean =
        url.substringBefore('?').trimEnd('/') == other.substringBefore('?').trimEnd('/')

    private fun statusId(url: String): String? {
        val segments = url.substringBefore('?').split('/').filter { it.isNotEmpty() }
        val index = segments.indexOf("status")
        if (index < 0 || index + 1 >= segments.size) return null
        return segments[index + 1].takeIf { it.all(Char::isDigit) }
    }

    private fun hostPath(url: String): String =
        url.removePrefix("https://").removePrefix("http://").trimEnd('/')

    private fun JsonObject.string(key: String): String? =
        (this[key] as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

    private fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject

    private val TCO = Regex("""https?://t\.co/[A-Za-z0-9]+""", RegexOption.IGNORE_CASE)
    private val MARKDOWN_LINK = Regex("""\[([^\[\]]+)\]\((https?://[^)\s]+)\)""")
    private val MEDIA_HOSTS = setOf(
        "x.com",
        "mobile.x.com",
        "twitter.com",
        "mobile.twitter.com",
    )
    private val ENTITY_PATTERNS = listOf(
        Regex(
            """"url"\s*:\s*"(https://t\.co/[^"\\]+)".{0,240}?"expanded_url"\s*:\s*"([^"\\]+)".{0,240}?"display_url"\s*:\s*"([^"\\]+)"""",
            setOf(RegexOption.DOT_MATCHES_ALL),
        ),
        Regex(
            """url:\\"(https://t\.co/[^"\\]+)\\".{0,240}?expanded_url:\\"([^"\\]+)\\".{0,240}?display_url:\\"([^"\\]+)""",
            setOf(RegexOption.DOT_MATCHES_ALL),
        ),
    )
}
