package com.mustafashakir.peek.data.reddit

import java.time.Instant
import java.util.Locale

/**
 * Reads a public post from Reddit's server-rendered `<shreddit-post>` element when the
 * comments JSON document is a block page. Comments are not in that element, so the
 * fallback post has none. Quarantine and removed posts fail closed.
 */
object RedditShredditPost {
    fun parse(html: String): ParsedRedditPost? {
        val tag = TAG.find(html)?.groupValues?.get(1) ?: return null
        if (tag.contains("quarantined\":true") || attr(tag, "quarantine").equals("true", ignoreCase = true)) {
            return null
        }
        val title = attr(tag, "post-title")?.trim().orEmpty()
        if (title.isBlank() || title == "[deleted]" || title == "[removed]") return null
        val id = attr(tag, "id")?.removePrefix("t3_")?.takeIf { it.isNotBlank() } ?: return null
        val contentHref = attr(tag, "content-href")
        val postType = attr(tag, "post-type").orEmpty()
        val media = RedditShredditPlayer.media(html, id, title)?.let(::listOf)
            ?: media(id, title, contentHref, postType)
        return ParsedRedditPost(
            id = id.lowercase(Locale.US),
            title = title,
            selfText = "",
            author = attr(tag, "author")?.takeIf { it.isNotBlank() } ?: "unknown",
            subreddit = attr(tag, "subreddit-name")?.takeIf { it.isNotBlank() } ?: "reddit",
            score = attr(tag, "score")?.toDoubleOrNull()?.toInt() ?: 0,
            commentCount = attr(tag, "comment-count")?.toDoubleOrNull()?.toInt() ?: 0,
            createdUtcEpochSeconds = epoch(attr(tag, "created-timestamp")),
            permalink = attr(tag, "permalink").orEmpty(),
            over18 = false,
            spoiler = false,
            isSelf = postType.equals("text", ignoreCase = true) || media.isEmpty(),
            media = media,
            comments = emptyList(),
        )
    }

    private fun media(id: String, title: String, contentHref: String?, postType: String): List<ParsedRedditMedia> {
        val url = contentHref?.replace("&amp;", "&")?.takeIf { it.startsWith("https://") } ?: return emptyList()
        val videoUrl = RedditShredditPlayer.playableRedditVideo(url)
        val video = postType.equals("video", ignoreCase = true) ||
            videoUrl?.substringBefore('?')?.lowercase(Locale.US).orEmpty()
                .let { it.endsWith(".mp4") || it.endsWith(".webm") || it.endsWith(".m3u8") || it.endsWith(".mpd") }
        if (video && videoUrl != null) {
            return listOf(
                ParsedRedditMedia(
                    id = id,
                    imageUrl = "",
                    videoUrl = videoUrl,
                    width = null,
                    height = null,
                    durationSeconds = null,
                    contentDescription = title.take(200),
                ),
            )
        }
        return listOf(
            ParsedRedditMedia(
                id = id,
                imageUrl = url,
                videoUrl = null,
                width = null,
                height = null,
                durationSeconds = null,
                contentDescription = title.take(200),
            ),
        )
    }

    private fun attr(tag: String, name: String): String? =
        Regex("""\b${Regex.escape(name)}\s*=\s*["']([^"']*)["']""", RegexOption.IGNORE_CASE)
            .find(tag)
            ?.groupValues
            ?.get(1)
            ?.replace("&amp;", "&")
            ?.replace("\\u0026", "&")

    private fun epoch(value: String?): Long {
        if (value.isNullOrBlank()) return 0L
        val normalized = value.replace(Regex("""([+-]\d{2})(\d{2})$"""), "$1:$2")
        return runCatching { Instant.parse(normalized).epochSecond }.getOrDefault(0L)
    }

    private val TAG = Regex("""<shreddit-post\b([^>]*)>""", RegexOption.IGNORE_CASE)
}
