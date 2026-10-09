package app.pane.android.data.facebook

import app.pane.android.domain.model.AuthorLines
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

data class FacebookPlayable(
    val url: String,
    val width: Int? = null,
    val height: Int? = null,
    val bitrate: Int? = null,
)

data class ParsedFacebookPost(
    val id: String,
    val canonicalUrl: String,
    val author: String,
    val text: String,
    val imageUrls: List<String>,
    val videoUrl: String?,
    val authorAvatarUrl: String? = null,
    val videos: List<FacebookPlayable> = emptyList(),
    /** Open Graph says this is a video, even when the playable file arrives on another host. */
    val videoHint: Boolean = false,
)

/**
 * Reads a public post from Facebook HTML, including the logged-out embed plugin.
 * A login wall without a real description is not a post.
 */
object FacebookDocument {
    const val UNAVAILABLE = "Facebook didn't return this public post. It may be private or blocked."
    const val LOGIN = "Facebook asked for a login"

    private val json = Json { isLenient = true }

    fun parse(html: String, id: String, canonicalUrl: String): ParsedFacebookPost? {
        if (html.isBlank()) return null
        val metas = metas(html)
        val title = metas["og:title"] ?: metas["twitter:title"]
        val description = metas["og:description"] ?: metas["twitter:description"]
        val images = listOfNotNull(metas["og:image"], metas["twitter:image"])
            .map(::cleanUrl)
            .filter(::isRemote)
            .distinct()
        val ogVideo = listOf("og:video:secure_url", "og:video:url", "og:video")
            .firstNotNullOfOrNull { metas[it] }
            ?.let(::cleanUrl)
            ?.takeIf(::isPlayableMedia)
        val embedded = playableVideos(html)
        val videos = (listOfNotNull(ogVideo?.let { FacebookPlayable(it) }) + embedded)
            .distinctBy { it.url }
        val video = bestPlayable(videos)?.url ?: ogVideo
        val text = chooseCaption(description, title, html)
        if (text == null && images.isEmpty() && video == null) return null
        if (isLoginWall(html) && text == null) return null
        val caption = text ?: title?.takeIf { !isGeneric(it) } ?: "Facebook post"
        val actor = findActor(html, caption, images)
        val type = metas["og:type"].orEmpty()
        return ParsedFacebookPost(
            id = id,
            canonicalUrl = canonicalUrl,
            author = actor?.name?.takeUnless(AuthorLines::isSourceLabel).orEmpty(),
            text = caption,
            imageUrls = images,
            videoUrl = video,
            authorAvatarUrl = actor?.avatarUrl,
            videos = videos,
            videoHint = video != null || type.contains("video", ignoreCase = true),
        )
    }

    /** A page name, not the post body that Facebook sometimes copies into og:title. */
    fun hasDistinctAuthor(post: ParsedFacebookPost): Boolean {
        if (post.author.isBlank() || post.author == "Facebook" || AuthorLines.isSourceLabel(post.author)) return false
        return !looksLikeCaption(post.author, post.text)
    }

    /** Facebook's preview tags often end in an ellipsis while the page still has the rest. */
    fun isTruncatedPreview(text: String): Boolean {
        val trimmed = text.trim()
        return trimmed.endsWith("...") || trimmed.endsWith("…")
    }

    private fun chooseCaption(description: String?, title: String?, html: String): String? {
        val preview = description?.trim()?.takeIf { it.isNotEmpty() && !isGeneric(it) }
        val bodies = htmlBodies(html).filter { it.length >= 8 && !isGeneric(it) }
        val messages = messageTexts(html).filter { it.length >= 8 && !isGeneric(it) }
        val extended = (bodies + messages)
            .filter { candidate -> preview == null || extendsPreview(candidate, preview) }
            .maxByOrNull { it.length }
        return when {
            extended != null && (preview == null || extended.length > preview.length) -> extended
            preview != null -> preview
            else -> title?.trim()?.takeIf { it.isNotEmpty() && !isGeneric(it) }
        }
    }

    private fun extendsPreview(candidate: String, preview: String): Boolean {
        val full = normalize(candidate)
        val stub = normalize(preview).removeSuffix("...").removeSuffix("…").trim()
        if (stub.length < 8) return false
        val head = stub.take(48)
        return full.startsWith(head) || full.contains(head)
    }

    private fun normalize(value: String): String =
        value.replace('\u00a0', ' ').replace(Regex("\\s+"), " ").trim()

    private fun htmlBodies(html: String): List<String> {
        val bodies = ArrayList<String>()
        for (anchor in BODY_ANCHORS) {
            var from = 0
            while (from < html.length) {
                val at = html.indexOf(anchor, from)
                if (at < 0) break
                val window = html.substring(at, minOf(html.length, at + 20_000))
                val lines = DIR_AUTO.findAll(window)
                    .map { stripTags(it.groupValues[1]) }
                    .filter { it.isNotBlank() }
                    .toList()
                val joined = if (lines.isNotEmpty()) {
                    lines.joinToString("\n")
                } else {
                    stripTags(window.substringBefore("</div></div></div>"))
                }
                if (joined.length >= 8) bodies += joined
                from = at + anchor.length
            }
        }
        return bodies
    }

    private fun messageTexts(html: String): List<String> {
        val found = ArrayList<String>()
        var from = 0
        while (from < html.length) {
            val at = html.indexOf(MESSAGE_KEY, from)
            if (at < 0) break
            var cursor = at + MESSAGE_KEY.length
            while (cursor < html.length && html[cursor].isWhitespace()) cursor += 1
            if (cursor < html.length && html[cursor] == '{') {
                textFieldInObject(html, cursor)?.takeIf { it.isNotBlank() }?.let(found::add)
            }
            from = at + MESSAGE_KEY.length
        }
        return found
    }

    private fun textFieldInObject(html: String, openBrace: Int): String? {
        var depth = 0
        var index = openBrace
        val limit = minOf(html.length, openBrace + 12_000)
        while (index < limit) {
            val char = html[index]
            if (char == '"') {
                val end = endOfString(html, index)
                val literal = html.substring(index + 1, end.coerceAtMost(html.length))
                if (depth == 1 && literal == "text") {
                    var cursor = end + 1
                    while (cursor < html.length && html[cursor].isWhitespace()) cursor += 1
                    if (cursor < html.length && html[cursor] == ':') {
                        cursor += 1
                        while (cursor < html.length && html[cursor].isWhitespace()) cursor += 1
                        if (cursor < html.length && html[cursor] == '"') {
                            val valueEnd = endOfString(html, cursor)
                            return unescapeJson(html.substring(cursor + 1, valueEnd.coerceAtMost(html.length)))
                        }
                    }
                }
                index = end + 1
                continue
            }
            if (char == '{') depth += 1
            else if (char == '}') {
                depth -= 1
                if (depth == 0) return null
            }
            index += 1
        }
        return null
    }

    private fun endOfString(html: String, openQuote: Int): Int {
        var index = openQuote + 1
        while (index < html.length) {
            if (html[index] == '\\') {
                index += 2
                continue
            }
            if (html[index] == '"') return index
            index += 1
        }
        return (html.length - 1).coerceAtLeast(openQuote)
    }

    private fun unescapeJson(raw: String): String =
        runCatching { json.parseToJsonElement("\"$raw\"").jsonPrimitive.contentOrNull ?: raw }
            .getOrDefault(raw)

    private fun stripTags(html: String): String {
        val withBreaks = html
            .replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("</p>", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("<[^>]+>"), "")
        return unescape(withBreaks)
            .replace(Regex("[ \\t\\u00a0]+"), " ")
            .replace(Regex(" *\\n *"), "\n")
            .replace(Regex("\\n{3,}"), "\n\n")
            .trim()
    }

    private fun metas(html: String): Map<String, String> {
        val values = linkedMapOf<String, String>()
        META_TAG.findAll(html).forEach { match ->
            val tag = match.value
            val key = attr(tag, "property") ?: attr(tag, "name") ?: return@forEach
            val content = attr(tag, "content") ?: return@forEach
            if (content.isNotBlank()) values.putIfAbsent(key.lowercase(), unescape(content))
        }
        return values
    }

    private fun attr(tag: String, name: String): String? =
        Regex("""\b$name\s*=\s*(?:"([^"]*)"|'([^']*)')""", RegexOption.IGNORE_CASE)
            .find(tag)
            ?.let { it.groupValues[1].ifEmpty { it.groupValues[2] } }

    private data class Actor(val name: String, val avatarUrl: String?)

    private fun findActor(html: String, caption: String, postImages: List<String>): Actor? {
        val fromJson = listOf("\"actors\":", "\"owning_profile\":")
            .firstNotNullOfOrNull { actorNear(html, it, postImages) }
        val fromPage = fromJson
            ?: mbasicActor(html, postImages)
            ?: pluginAuthor(html, caption)
            ?: titleAuthor(html, caption)?.let { Actor(it, null) }
        return fromPage?.takeUnless { looksLikeCaption(it.name, caption) || isGeneric(it.name) }
    }

    private fun actorNear(html: String, key: String, postImages: List<String>): Actor? {
        var from = 0
        while (from < html.length) {
            val at = html.indexOf(key, from)
            if (at < 0) return null
            val window = html.substring(at, minOf(html.length, at + 2_500))
            val name = firstJsonString(window, "name")?.let(::unescape)?.trim()
            if (name != null && acceptableName(name)) {
                return Actor(name, avatarIn(window, postImages))
            }
            from = at + key.length
        }
        return null
    }

    private fun mbasicActor(html: String, postImages: List<String>): Actor? {
        val heading = Regex("""<h3[^>]*>\s*<a\b[^>]*>([^<]{1,80})</a>""", RegexOption.IGNORE_CASE)
            .find(html)
            ?.groupValues
            ?.get(1)
            ?.let(::unescape)
            ?.trim()
            ?: return null
        if (!acceptableName(heading)) return null
        val avatar = Regex("""<img\b[^>]*>""", RegexOption.IGNORE_CASE).findAll(html).firstNotNullOfOrNull { match ->
            val tag = match.value
            val alt = attr(tag, "alt")?.let(::unescape)?.trim()
            val src = attr(tag, "src")?.let(::cleanUrl)
            if (alt == heading && src != null && isAvatar(src, postImages)) src else null
        }
        return Actor(heading, avatar)
    }

    private fun pluginAuthor(html: String, caption: String): Actor? {
        val link = Regex(
            """<a\b[^>]*href="https://(?:www\.)?facebook\.com/(?!plugins|share|sharer|login)[^"]*"[^>]*>([^<]{1,80})</a>""",
            RegexOption.IGNORE_CASE,
        )
        for (match in link.findAll(html)) {
            val name = unescape(match.groupValues[1]).trim()
            if (!acceptableName(name) || looksLikeCaption(name, caption)) continue
            return Actor(name, null)
        }
        return null
    }

    private fun titleAuthor(html: String, caption: String): String? {
        val metas = metas(html)
        val raw = (metas["og:title"] ?: metas["twitter:title"])?.trim()?.takeIf { it.isNotEmpty() && !isGeneric(it) }
            ?: return null
        val name = raw
            .removeSuffix(" - Facebook")
            .removeSuffix(" | Facebook")
            .removeSuffix(" on Facebook")
            .removeSuffix(" on Reels")
            .substringBefore(" - ")
            .trim()
        return name.takeIf { acceptableName(it) && !looksLikeCaption(it, caption) }
    }

    private fun looksLikeCaption(name: String, caption: String): Boolean {
        val authorName = normalize(name)
        val body = normalize(caption)
        if (authorName.length > 80) return true
        if (authorName.count { it == ' ' } >= 12) return true
        if (authorName.length >= 40 && body.startsWith(authorName.take(40))) return true
        if (authorName.length >= 24 && body.isNotEmpty() && authorName == body) return true
        return false
    }

    private fun acceptableName(name: String): Boolean =
        name.isNotEmpty() && name.length <= 80 && !isGeneric(name) && !name.contains("http", ignoreCase = true)

    private fun avatarIn(window: String, postImages: List<String>): String? =
        listOf("uri", "profile_pic_url", "url").firstNotNullOfOrNull { key ->
            firstJsonString(window, key)
                ?.let(::unescape)
                ?.let(::cleanUrl)
                ?.takeIf { isAvatar(it, postImages) }
        }

    private fun isAvatar(url: String, postImages: List<String>): Boolean {
        if (!isRemote(url) || url in postImages) return false
        val host = runCatching { java.net.URI(url).host }.getOrNull()?.lowercase().orEmpty()
        if (host.contains("fbcdn") || host.contains("scontent")) return true
        return url.contains(".jpg", ignoreCase = true) ||
            url.contains(".jpeg", ignoreCase = true) ||
            url.contains(".png", ignoreCase = true) ||
            url.contains(".webp", ignoreCase = true)
    }

    private fun firstJsonString(window: String, key: String): String? {
        val marker = "\"$key\":\""
        var from = 0
        while (from < window.length) {
            val at = window.indexOf(marker, from)
            if (at < 0) return null
            val previous = if (at == 0) ' ' else window[at - 1]
            if (!previous.isLetterOrDigit() && previous != '_') {
                return readJsonString(window, at + marker.length)
            }
            from = at + marker.length
        }
        return null
    }

    private fun readJsonString(source: String, start: Int): String {
        val out = StringBuilder()
        var index = start
        while (index < source.length) {
            val char = source[index]
            if (char == '"') break
            if (char == '\\' && index + 1 < source.length) {
                out.append(source[index + 1])
                index += 2
                continue
            }
            out.append(char)
            index += 1
        }
        return out.toString()
    }

    private fun isGeneric(value: String): Boolean {
        val text = value.trim().lowercase()
        if (text.isEmpty() || text == "facebook") return true
        return GENERIC.any { text.contains(it) }
    }

    private fun isLoginWall(html: String): Boolean {
        val text = html.lowercase()
        return "login_form" in text || "id=\"loginform\"" in text || "/login.php" in text && "password" in text
    }

    private fun playableVideos(html: String): List<FacebookPlayable> {
        val found = LinkedHashMap<String, FacebookPlayable>()
        for (key in VIDEO_KEYS) {
            val marker = "\"$key\""
            var from = 0
            while (from < html.length) {
                val at = html.indexOf(marker, from)
                if (at < 0) break
                val raw = stringAfterKey(html, at + marker.length)
                if (raw != null) {
                    val clean = cleanUrl(raw)
                    if (isPlayableMedia(clean)) {
                        val next = FacebookPlayable(
                            url = clean,
                            width = closestNumber(html, at, key, "original_width") ?: closestNumber(html, at, key, "width"),
                            height = closestNumber(html, at, key, "original_height") ?: closestNumber(html, at, key, "height"),
                            bitrate = closestNumber(html, at, key, "bitrate"),
                        )
                        val previous = found[clean]
                        if (previous == null || (previous.height == null && next.height != null)) {
                            found[clean] = next
                        }
                    }
                }
                from = at + marker.length
            }
        }
        return found.values.toList()
    }

    private fun bestPlayable(videos: List<FacebookPlayable>): FacebookPlayable? =
        videos.maxWithOrNull(
            compareBy<FacebookPlayable> { it.height ?: 0 }
                .thenBy { it.bitrate ?: 0 },
        )

    private fun stringAfterKey(html: String, afterKey: Int): String? {
        var cursor = afterKey
        val limit = minOf(html.length, afterKey + 32)
        while (cursor < limit && html[cursor].isWhitespace()) cursor += 1
        if (cursor < html.length && html[cursor] == '\\') cursor += 1
        if (cursor >= html.length || html[cursor] != ':') return null
        cursor += 1
        while (cursor < html.length && (html[cursor].isWhitespace() || html[cursor] == '\\')) cursor += 1
        if (cursor >= html.length || html[cursor] != '"') return null
        val raw = readRawJsonString(html, cursor + 1)
        return unescapeJson(raw).replace("\\/", "/")
    }

    private fun readRawJsonString(source: String, start: Int): String {
        val out = StringBuilder()
        var index = start
        while (index < source.length) {
            val char = source[index]
            if (char == '\\' && index + 1 < source.length) {
                out.append(char).append(source[index + 1])
                index += 2
                continue
            }
            if (char == '"') break
            out.append(char)
            index += 1
        }
        return out.toString()
    }

    private fun closestNumber(html: String, keyAt: Int, videoKey: String, name: String): Int? {
        val pattern = Regex(""""$name"\s*:\s*(\d{2,8})""")
        val after = html.substring(keyAt, minOf(html.length, keyAt + 900))
        pattern.find(after)?.let { match ->
            val between = after.substring(0, match.range.first)
            val otherVideo = VIDEO_KEYS.any { other -> other != videoKey && "\"$other\"" in between }
            if (!otherVideo) {
                return match.groupValues[1].toIntOrNull()?.takeIf { it in 1..50_000_000 }
            }
        }
        val before = html.substring(maxOf(0, keyAt - 350), keyAt)
        val earlier = pattern.findAll(before).lastOrNull() ?: return null
        val tail = before.substring(earlier.range.first)
        if (VIDEO_KEYS.any { "\"$it\"" in tail }) return null
        return earlier.groupValues[1].toIntOrNull()?.takeIf { it in 1..50_000_000 }
    }

    private fun isPlayableMedia(url: String): Boolean {
        if (!isRemote(url)) return false
        val lower = url.lowercase()
        if (IMAGE_EXT.any { ext -> ext in lower }) return false
        return ".mp4" in lower ||
            ".m3u8" in lower ||
            "fbcdn.net" in lower ||
            "fbcdn.com" in lower ||
            "video_redirect" in lower ||
            "/video/playback" in lower
    }

    private fun isRemote(url: String): Boolean = url.startsWith("https://") || url.startsWith("http://")

    private fun cleanUrl(url: String): String = unescape(url).replace("&amp;", "&").trim()

    private fun unescape(value: String): String {
        val named = value
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#039;", "'")
            .replace("&#39;", "'")
            .replace("&apos;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&nbsp;", " ")
        return named
            .replace(Regex("&#(\\d+);")) { match -> match.groupValues[1].toIntOrNull()?.toChar()?.toString() ?: match.value }
            .replace(Regex("&#x([0-9a-fA-F]+);")) { match ->
                match.groupValues[1].toIntOrNull(16)?.toChar()?.toString() ?: match.value
            }
    }

    private val META_TAG = Regex("""<meta\b[^>]*>""", RegexOption.IGNORE_CASE)
    private val DIR_AUTO = Regex("""<div[^>]*\bdir="auto"[^>]*>(.*?)</div>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val BODY_ANCHORS = listOf(
        "data-testid=\"post_message\"",
        "userContent",
        "story_body_content",
        "_5pbx",
    )
    private const val MESSAGE_KEY = "\"message\":"
    private val VIDEO_KEYS = listOf(
        "browser_native_hd_url",
        "playable_url_quality_hd",
        "hd_src_no_ratelimit",
        "hd_src",
        "browser_native_sd_url",
        "playable_url",
        "sd_src_no_ratelimit",
        "sd_src",
    )
    private val IMAGE_EXT = listOf(".jpg", ".jpeg", ".png", ".webp", ".gif")
    private val GENERIC = listOf(
        "log into facebook",
        "log in to facebook",
        "facebook is a social",
        "create new account",
        "sign up for facebook",
    )
}
