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
 * A raw https://t.co/… string is never left in the text: entities expand it,
 * and a card URL (article, quote, or photo) is removed instead of printed.
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
        val drop = buildSet {
            article?.url?.let(::add)
            quote?.url?.let(::add)
            entities.filter { isMedia(it.expanded) }.forEach { add(it.expanded) }
        }
        val text = expand(base, entities, drop)
        val links = entities.mapNotNull { entity ->
            val expanded = entity.expanded
            if (!expanded.startsWith("http")) return@mapNotNull null
            if (expanded in drop || isMedia(expanded) || isArticle(expanded) || isStatus(expanded, quote)) {
                return@mapNotNull null
            }
            val label = entity.display.ifBlank { hostPath(expanded) }
            if (label.contains("t.co")) return@mapNotNull null
            XLinkPreview(url = expanded, label = label)
        }.distinctBy { it.url }
        val visible = if (text.isBlank() || links.any { text == it.label }) "" else text
        return XRichPost(
            text = visible,
            article = article,
            quote = quote?.copy(text = expand(quote.text, entities, drop)),
            links = links,
        )
    }

    /** Replies and thread posts: same t.co rule, using entities embedded beside the text. */
    fun expandShortLinks(text: String, nearby: String = ""): String {
        val entities = entitiesIn("$text\n$nearby")
        return expand(text, entities, emptySet()).ifBlank {
            expand(text, entities, emptySet())
        }
    }

    fun expand(text: String, entities: List<XUrlEntity>, drop: Set<String>): String {
        var result = text
        entities.filter { it.url.contains("://t.co/") }.forEach { entity ->
            val replacement = when {
                entity.expanded in drop || isMedia(entity.expanded) || isArticle(entity.expanded) -> ""
                entity.display.isNotBlank() && !entity.display.contains("t.co") -> entity.display
                entity.expanded.startsWith("http") && !entity.expanded.contains("://t.co/") -> hostPath(entity.expanded)
                else -> ""
            }
            result = result.replace(entity.url, replacement)
        }
        result = TCO.replace(result, "")
        return result
            .replace(Regex("[ \\t]{2,}"), " ")
            .replace(Regex(" *\\n *"), "\n")
            .trim()
    }

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
        return "pic.twitter.com" in lower ||
            "pbs.twimg.com" in lower ||
            "video.twimg.com" in lower ||
            "/photo/" in lower ||
            "/video/" in lower
    }

    private fun isArticle(url: String): Boolean = "/i/article/" in url

    private fun isStatus(url: String, quote: XQuote?): Boolean {
        if (quote != null && (url == quote.url || url.contains("/status/"))) return url.contains(quote.url.substringAfterLast('/'))
        return false
    }

    private fun hostPath(url: String): String =
        url.removePrefix("https://").removePrefix("http://").trimEnd('/')

    private fun JsonObject.string(key: String): String? =
        (this[key] as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

    private fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject

    private val TCO = Regex("""https?://t\.co/[A-Za-z0-9]+""", RegexOption.IGNORE_CASE)
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
