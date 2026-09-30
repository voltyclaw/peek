package com.mustafashakir.peek.data.reddit

import java.time.Instant
import java.util.Locale

/**
 * Reads a public post from the HTML a logged-out Reddit page actually renders.
 * JSON listings embedded in the page win when they parse, including `window.__r`
 * and `__NEXT_DATA__`. Otherwise the server-rendered `<shreddit-post>` element is
 * used, with `<shreddit-comment>` bodies when those are already in the document.
 * Quarantine, private communities, and removed posts fail closed.
 */
object RedditPageDocument {
    const val BLOCKED = "Reddit blocked the page in the browser"
    const val LOGIN = "Reddit asked for a login"
    const val HIDDEN = "This Reddit post isn't public"
    const val EMPTY = "Reddit page did not include a public post"

    sealed interface Read {
        data class Ready(val post: ParsedRedditPost) : Read
        data object Pending : Read
        data class Failed(val reason: String) : Read
    }

    fun read(html: String, parser: RedditJsonParser = RedditJsonParser()): Read {
        if (html.isBlank()) return Read.Pending
        val rendered = RedditShredditPost.parse(html)
        if (rendered == null && isBlockPage(html)) return Read.Failed(BLOCKED)
        if (rendered == null && isLoginWall(html)) return Read.Failed(LOGIN)
        if (rendered == null && isPrivateInterstitial(html)) return Read.Failed(HIDDEN)
        if (rendered == null && shredditPostIsHidden(html)) return Read.Failed(HIDDEN)

        val embedded = embeddedPost(html, parser)
        if (embedded is Embedded.Hidden && rendered == null) return Read.Failed(HIDDEN)
        val jsonPost = (embedded as? Embedded.Public)?.post
        val post = when {
            jsonPost != null -> merge(jsonPost, rendered, html)
            rendered != null -> rendered.copy(
                selfText = rendered.selfText.ifBlank { selfText(html) },
                comments = comments(html),
            )
            else -> null
        } ?: return Read.Pending
        return Read.Ready(post)
    }

    private fun merge(json: ParsedRedditPost, rendered: ParsedRedditPost?, html: String): ParsedRedditPost {
        val comments = json.comments.ifEmpty { comments(html) }
        val renderedMedia = rendered?.media.orEmpty()
        val media = preferMedia(json.media, renderedMedia)
        val selfText = json.selfText.ifBlank { rendered?.selfText.orEmpty() }.ifBlank { selfText(html) }
        val jsonCount = json.media.count { it.isDisplayableMedia() }
        val renderedCount = renderedMedia.count { it.isDisplayableMedia() }
        val mediaPending = rendered?.mediaPending == true && jsonCount <= renderedCount
        return json.copy(comments = comments, media = media, selfText = selfText, mediaPending = mediaPending)
    }

    private fun preferMedia(
        primary: List<ParsedRedditMedia>,
        secondary: List<ParsedRedditMedia>,
    ): List<ParsedRedditMedia> {
        val primaryCount = primary.count { it.isDisplayableMedia() }
        val secondaryCount = secondary.count { it.isDisplayableMedia() }
        return when {
            secondaryCount > primaryCount -> secondary
            primaryCount > 0 -> primary
            primary.isNotEmpty() -> primary
            else -> secondary
        }
    }

    private sealed interface Embedded {
        data class Public(val post: ParsedRedditPost) : Embedded
        data object Hidden : Embedded
        data object None : Embedded
    }

    private fun embeddedPost(html: String, parser: RedditJsonParser): Embedded {
        var hidden = false
        for (candidate in candidates(html)) {
            parser.parse(candidate)?.let { return Embedded.Public(it) }
            if (isHiddenDocument(candidate)) hidden = true
        }
        return if (hidden) Embedded.Hidden else Embedded.None
    }

    private fun candidates(html: String): List<String> {
        val found = LinkedHashSet<String>()
        var from = 0
        var scripts = 0
        while (scripts < MAX_SCRIPTS) {
            val start = html.indexOf("<script", from, ignoreCase = true)
            if (start < 0) break
            val tagEnd = html.indexOf('>', start)
            if (tagEnd < 0) break
            val close = html.indexOf("</script>", tagEnd + 1, ignoreCase = true)
            if (close < 0) break
            scripts += 1
            val openTag = html.substring(start, tagEnd + 1)
            val body = unescapeHtml(html.substring(tagEnd + 1, close)).trim()
            val interesting = openTag.contains("application/json", ignoreCase = true) ||
                body.contains("window.__r") ||
                body.contains("__NEXT_DATA__") ||
                body.contains("\"kind\"")
            if (interesting && body.isNotEmpty()) {
                if (body.startsWith("{") || body.startsWith("[")) found += body
                assignment(body, "window.__r")?.let { found += it }
                assignment(body, "window.___r")?.let { found += it }
                listingSlices(body).forEach { found += it }
            }
            from = close + "</script>".length
        }
        return found.toList()
    }

    private fun assignment(source: String, name: String): String? {
        val at = source.indexOf(name)
        if (at < 0) return null
        var index = at + name.length
        while (index < source.length && source[index].isWhitespace()) index++
        if (index >= source.length || source[index] != '=') return null
        index++
        while (index < source.length && source[index].isWhitespace()) index++
        return readJson(source, index)
    }

    private fun listingSlices(source: String): List<String> {
        val slices = mutableListOf<String>()
        var from = 0
        while (slices.size < MAX_LISTING_SLICES) {
            val kind = source.indexOf("\"kind\"", from)
            if (kind < 0) break
            val listing = source.indexOf("Listing", kind)
            if (listing < 0 || listing - kind > 24) {
                from = kind + 6
                continue
            }
            val objectStart = source.lastIndexOf('{', kind)
            if (objectStart < 0) break
            var container = objectStart
            var cursor = objectStart - 1
            while (cursor >= 0 && source[cursor].isWhitespace()) cursor--
            if (cursor >= 0 && source[cursor] == '[') container = cursor
            readJson(source, container)?.let { slices += it }
            from = kind + 6
        }
        return slices
    }

    private fun readJson(source: String, start: Int): String? {
        if (start >= source.length) return null
        val opener = source[start]
        if (opener != '{' && opener != '[') return null
        var depth = 0
        var inString = false
        var escaped = false
        for (index in start until source.length) {
            val character = source[index]
            if (inString) {
                if (escaped) {
                    escaped = false
                } else if (character == '\\') {
                    escaped = true
                } else if (character == '"') {
                    inString = false
                }
                continue
            }
            when (character) {
                '"' -> inString = true
                '{', '[' -> depth++
                '}', ']' -> {
                    depth--
                    if (depth == 0) return source.substring(start, index + 1)
                }
            }
        }
        return null
    }

    private fun isHiddenDocument(document: String): Boolean {
        if (!document.contains("t3")) return false
        return HIDDEN_JSON.containsMatchIn(document)
    }

    private fun comments(html: String): List<ParsedRedditComment> {
        val raws = COMMENT_TAG.findAll(html).mapNotNull { match ->
            val tag = match.groupValues[1]
            val thingId = attr(tag, "thingid")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val body = commentBody(html, thingId, match.range.first)
            if (body.isBlank()) return@mapNotNull null
            RawComment(
                id = thingId.removePrefix("t1_").lowercase(Locale.US),
                author = attr(tag, "author")?.takeIf { it.isNotBlank() } ?: "unknown",
                body = body,
                createdUtcEpochSeconds = epoch(attr(tag, "created")),
                depth = attr(tag, "depth")?.toIntOrNull()?.coerceAtLeast(0) ?: 0,
                isSubmitter = IS_OP.containsMatchIn(tag),
            )
        }.take(MAX_COMMENTS).toList()
        return thread(raws)
    }

    private fun thread(raws: List<RawComment>): List<ParsedRedditComment> {
        val roots = mutableListOf<Node>()
        val stack = ArrayDeque<Node>()
        for (raw in raws) {
            val node = Node(raw)
            while (stack.isNotEmpty() && stack.last().raw.depth >= raw.depth) stack.removeLast()
            if (stack.isEmpty()) roots += node else stack.last().children += node
            stack.addLast(node)
        }
        return roots.map { it.toParsed() }
    }

    private fun commentBody(html: String, thingId: String, from: Int): String {
        val marker = "$thingId-post-rtjson-content"
        val idAt = html.indexOf(marker, from)
        if (idAt < 0) return ""
        val open = html.lastIndexOf('<', idAt)
        if (open < 0) return ""
        return htmlToText(elementInner(html, open))
    }

    private fun selfText(html: String): String {
        val slot = html.indexOf("slot=\"text-body\"")
        if (slot < 0) return ""
        val open = html.lastIndexOf('<', slot)
        if (open < 0) return ""
        return htmlToText(elementInner(html, open))
    }

    private fun elementInner(html: String, openIndex: Int): String {
        val tagEnd = html.indexOf('>', openIndex)
        if (tagEnd < 0) return ""
        var index = tagEnd + 1
        val contentStart = index
        var depth = 1
        while (index < html.length && depth > 0) {
            val next = html.indexOf('<', index)
            if (next < 0) break
            if (html.startsWith("</", next)) {
                depth -= 1
                val close = html.indexOf('>', next)
                if (close < 0) break
                if (depth == 0) return html.substring(contentStart, next)
                index = close + 1
                continue
            }
            if (html.startsWith("<!", next) || html.startsWith("<?", next)) {
                val close = html.indexOf('>', next)
                if (close < 0) break
                index = close + 1
                continue
            }
            val close = html.indexOf('>', next)
            if (close < 0) break
            val selfClosing = html[close - 1] == '/' || tagName(html, next) in VOID_TAGS
            if (!selfClosing) depth += 1
            index = close + 1
        }
        return html.substring(contentStart, minOf(contentStart + MAX_TEXT_CHARS, html.length))
    }

    private fun tagName(html: String, openIndex: Int): String {
        var index = openIndex + 1
        val start = index
        while (index < html.length && (html[index].isLetterOrDigit() || html[index] == '-')) index++
        return html.substring(start, index).lowercase(Locale.US)
    }

    private fun htmlToText(html: String): String {
        val withBreaks = html
            .replace(BR_TAG, "\n")
            .replace(PARAGRAPH_END, "\n")
            .replace(TAG, "")
        return unescapeHtml(withBreaks)
            .lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString("\n")
            .take(MAX_TEXT_CHARS)
    }

    private fun attr(tag: String, name: String): String? =
        Regex("""\b${Regex.escape(name)}\s*=\s*["']([^"']*)["']""", RegexOption.IGNORE_CASE)
            .find(tag)
            ?.groupValues
            ?.get(1)
            ?.let(::unescapeHtml)

    private fun epoch(value: String?): Long {
        if (value.isNullOrBlank()) return 0L
        val normalized = value.replace(TZ_WITHOUT_COLON, "$1:$2")
        return runCatching { Instant.parse(normalized).epochSecond }.getOrDefault(0L)
    }

    private fun unescapeHtml(value: String): String {
        var current = value
        repeat(2) {
            current = current
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&apos;", "'")
                .replace(DECIMAL_ENTITY) { match ->
                    match.groupValues[1].toIntOrNull()?.toChar()?.toString() ?: match.value
                }
        }
        return current
    }

    private fun isBlockPage(html: String): Boolean {
        val lower = html.lowercase(Locale.US)
        return lower.contains("whoa there") || lower.contains("you've been blocked") || lower.contains("you’ve been blocked")
    }

    private fun isLoginWall(html: String): Boolean {
        val lower = html.lowercase(Locale.US)
        if (lower.contains("<shreddit-post")) return false
        val password = lower.contains("type=\"password\"") || lower.contains("type='password'")
        if (!password) return false
        return lower.contains("/login") || lower.contains("log in")
    }

    private fun isPrivateInterstitial(html: String): Boolean {
        val lower = html.lowercase(Locale.US)
        if (lower.contains("<shreddit-post")) return false
        return lower.contains("this community is private") ||
            lower.contains("you must be invited") ||
            lower.contains("this community has been banned")
    }

    private fun shredditPostIsHidden(html: String): Boolean {
        val post = SHREDDIT_POST.find(html)?.groupValues?.get(1) ?: return false
        if (attr(post, "quarantine").equals("true", ignoreCase = true)) return true
        val title = attr(post, "post-title")?.trim().orEmpty()
        return title == "[deleted]" || title == "[removed]"
    }

    private data class RawComment(
        val id: String,
        val author: String,
        val body: String,
        val createdUtcEpochSeconds: Long,
        val depth: Int,
        val isSubmitter: Boolean,
    )

    private class Node(val raw: RawComment) {
        val children = mutableListOf<Node>()

        fun toParsed(): ParsedRedditComment = ParsedRedditComment(
            id = raw.id,
            author = raw.author,
            body = raw.body,
            createdUtcEpochSeconds = raw.createdUtcEpochSeconds,
            isSubmitter = raw.isSubmitter,
            replies = children.map { it.toParsed() },
        )
    }

    private const val MAX_SCRIPTS = 12
    private const val MAX_LISTING_SLICES = 4
    private const val MAX_COMMENTS = 40
    private const val MAX_TEXT_CHARS = 8_000
    private val COMMENT_TAG = Regex("""<shreddit-comment\b([^>]*)>""", RegexOption.IGNORE_CASE)
    private val SHREDDIT_POST = Regex("""<shreddit-post\b([^>]*)>""", RegexOption.IGNORE_CASE)
    private val IS_OP = Regex("""\bis-op\b""", RegexOption.IGNORE_CASE)
    private val HIDDEN_JSON = Regex(
        """"(?:quarantine"\s*:\s*true|subreddit_type"\s*:\s*"(?:private|gold_only|employees_only)"|title"\s*:\s*"\[(?:deleted|removed)\]")""",
    )
    private val BR_TAG = Regex("""<br\s*/?>""", RegexOption.IGNORE_CASE)
    private val PARAGRAPH_END = Regex("""</p>""", RegexOption.IGNORE_CASE)
    private val TAG = Regex("""<[^>]+>""")
    private val DECIMAL_ENTITY = Regex("""&#(\d+);""")
    private val TZ_WITHOUT_COLON = Regex("""([+-]\d{2})(\d{2})$""")
    private val VOID_TAGS = setOf(
        "area", "base", "br", "col", "embed", "hr", "img", "input", "link", "meta", "param", "source", "track", "wbr",
    )
}
