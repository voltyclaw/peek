package com.mustafashakir.peek.data.reddit

import java.util.Locale
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * The post's `<shreddit-player>` holds a playable file. `content-href` on a video post is
 * only `https://v.redd.it/{id}`, which is not a media file, and it has no poster.
 * Packaged MP4s are muxed (picture and audio). Ads and comment players are ignored.
 */
internal object RedditShredditPlayer {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun media(html: String, postId: String, title: String): ParsedRedditMedia? {
        val tag = PLAYER_TAG.findAll(html).map { it.groupValues[1] }.firstOrNull { tag ->
            samePost(tag, postId) && !hasAttr(tag, "comment-id") && !hasAttr(tag, "post-promoted")
        } ?: return null
        val packaged = packagedMp4(attr(tag, "packaged-media-json"))
        val videoUrl = packaged?.url
            ?: playable(attr(tag, "src"))
            ?: playable(attr(tag, "preview"))
        if (videoUrl == null) return null
        val poster = attr(tag, "poster")?.takeIf { it.startsWith("https://") }.orEmpty()
        return ParsedRedditMedia(
            id = postId,
            imageUrl = poster,
            videoUrl = videoUrl,
            width = packaged?.width,
            height = packaged?.height,
            durationSeconds = packaged?.durationSeconds,
            contentDescription = title.take(200),
        )
    }

    /** `https://v.redd.it/{id}` is a document, not a file. Public HLS is the last resort. */
    fun playableRedditVideo(url: String): String? {
        if (isPlayable(url)) return url
        val id = bareVideoId(url) ?: return null
        return "https://v.redd.it/$id/HLSPlaylist.m3u8"
    }

    private fun packagedMp4(raw: String?): Packaged? {
        if (raw.isNullOrBlank()) return null
        val root = runCatching { json.parseToJsonElement(raw) as? JsonObject }.getOrNull() ?: return null
        val playback = root["playbackMp4s"]?.asObject() ?: return null
        val durationValue = playback["duration"] as? JsonPrimitive
        val duration = durationValue?.intOrNull ?: durationValue?.contentOrNull?.toDoubleOrNull()?.toInt()
        val candidates = playback["permutations"]?.asArray().orEmpty().mapNotNull { element ->
            val source = element.asObject()?.get("source")?.asObject() ?: return@mapNotNull null
            val url = (source["url"] as? JsonPrimitive)?.contentOrNull?.takeIf { isPlayable(it) } ?: return@mapNotNull null
            val dimensions = source["dimensions"]?.asObject()
            Candidate(
                url = url,
                width = (dimensions?.get("width") as? JsonPrimitive)?.intOrNull ?: 0,
                height = (dimensions?.get("height") as? JsonPrimitive)?.intOrNull ?: 0,
                codec = (source["videoCodec"] as? JsonPrimitive)?.contentOrNull.orEmpty(),
            )
        }
        val chosen = candidates
            .filter { it.codec.isBlank() || it.codec.equals("H264", ignoreCase = true) }
            .ifEmpty { candidates }
            .maxByOrNull { it.width.toLong() * it.height.coerceAtLeast(1) }
            ?: return null
        return Packaged(chosen.url, chosen.width, chosen.height, duration)
    }

    private fun playable(url: String?): String? = url?.takeIf(::isPlayable)

    private fun isPlayable(url: String): Boolean {
        if (!url.startsWith("https://")) return false
        val path = url.substringBefore('?').lowercase(Locale.US)
        return path.endsWith(".mp4") || path.endsWith(".webm") || path.endsWith(".m3u8") || path.endsWith(".mpd")
    }

    private fun bareVideoId(url: String): String? {
        if (!url.startsWith("https://")) return null
        val withoutQuery = url.substringBefore('?')
        val hostAndPath = withoutQuery.removePrefix("https://")
        val host = hostAndPath.substringBefore('/').lowercase(Locale.US)
        if (host != "v.redd.it") return null
        val id = hostAndPath.substringAfter('/', "").trim('/')
        if (id.isEmpty() || id.contains('/')) return null
        if (!id.matches(VIDEO_ID)) return null
        return id
    }

    private fun samePost(tag: String, postId: String): Boolean {
        val raw = attr(tag, "post-id") ?: return false
        return raw.removePrefix("t3_").equals(postId, ignoreCase = true)
    }

    private fun hasAttr(tag: String, name: String): Boolean =
        Regex("""\b${Regex.escape(name)}\s*=""", RegexOption.IGNORE_CASE).containsMatchIn(tag)

    private fun attr(tag: String, name: String): String? {
        val match = Regex(
            """\b${Regex.escape(name)}\s*=\s*(["'])(.*?)\1""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
        ).find(tag) ?: return null
        return unescape(match.groupValues[2]).takeIf { it.isNotBlank() }
    }

    private fun unescape(value: String): String {
        var current = value
        repeat(2) {
            current = current
                .replace("&amp;", "&")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&apos;", "'")
                .replace("\\u0026", "&")
        }
        return current
    }

    private fun kotlinx.serialization.json.JsonElement.asObject(): JsonObject? = this as? JsonObject

    private fun kotlinx.serialization.json.JsonElement.asArray(): JsonArray? = this as? JsonArray

    private data class Candidate(val url: String, val width: Int, val height: Int, val codec: String)

    private data class Packaged(val url: String, val width: Int, val height: Int, val durationSeconds: Int?)

    private val PLAYER_TAG = Regex("""<shreddit-player\b([^>]*)>""", RegexOption.IGNORE_CASE)
    private val VIDEO_ID = Regex("""[A-Za-z0-9]+""")
}
