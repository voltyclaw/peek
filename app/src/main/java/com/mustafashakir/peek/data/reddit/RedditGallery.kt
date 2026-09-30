package com.mustafashakir.peek.data.reddit

import java.net.URI
import java.util.Locale

/**
 * Gallery posts put `https://www.reddit.com/gallery/{id}` on `content-href`. That is a page,
 * not a picture. The slides live in `<gallery-carousel>` (and in `<peek-gallery>`, which the
 * WebView adds after it has walked the carousel's shadow root and later slides).
 * Comment figures are left alone.
 */
internal object RedditGallery {
    fun media(html: String, postId: String, title: String): List<ParsedRedditMedia> {
        val commentAt = html.indexOf("<shreddit-comment", ignoreCase = true)
        val chunks = mutableListOf<String>()
        CAROUSEL.findAll(html).forEach { match ->
            val owner = attr(match.groupValues[1], "post-id")?.removePrefix("t3_")
            if (owner != null && !owner.equals(postId, ignoreCase = true)) return@forEach
            if (owner == null && commentAt >= 0 && match.range.first > commentAt) return@forEach
            chunks += match.groupValues[2]
        }
        PEEK.findAll(html).forEach { chunks += it.groupValues[1] }
        if (chunks.isEmpty()) return emptyList()

        val ordered = LinkedHashMap<String, ParsedRedditMedia>()
        for (chunk in chunks) {
            for (item in slides(chunk, postId, title)) {
                val key = identity(item) ?: continue
                val existing = ordered[key]
                if (existing == null || prefers(item, existing)) ordered[key] = item
            }
        }
        return ordered.values.toList()
    }

    private fun slides(chunk: String, postId: String, title: String): List<ParsedRedditMedia> {
        val lis = LI.findAll(chunk).toList()
        if (lis.isEmpty()) return images(chunk, postId, title)
        return lis
            .map { match -> pageOf(match.groupValues[1]) to match.groupValues[2] }
            .sortedBy { it.first }
            .flatMap { (_, body) -> images(body, postId, title) }
    }

    private fun images(block: String, postId: String, title: String): List<ParsedRedditMedia> {
        val found = mutableListOf<ParsedRedditMedia>()
        IMG.findAll(block).forEach { match ->
            val tag = match.groupValues[1]
            if (isBackdrop(tag)) return@forEach
            val url = bestUrl(tag) ?: return@forEach
            if (!isDirectImageUrl(url) && !isPlayableFile(url)) return@forEach
            found += item(url, tag, postId, title, found.size)
        }
        SOURCE.findAll(block).forEach { match ->
            val tag = match.groupValues[1]
            val url = attr(tag, "src")?.takeIf { isPlayableFile(it) } ?: return@forEach
            val key = identity(url)
            val existing = found.indexOfFirst { identity(it) == key }
            if (existing >= 0) {
                val current = found[existing]
                if (current.videoUrl == null) found[existing] = current.copy(videoUrl = url)
            } else {
                found += item(url, tag, postId, title, found.size).copy(imageUrl = "", videoUrl = url)
            }
        }
        return found
    }

    private fun item(url: String, tag: String, postId: String, title: String, index: Int): ParsedRedditMedia {
        val playable = isPlayableFile(url)
        return ParsedRedditMedia(
            id = mediaId(url, postId, index),
            imageUrl = if (playable) "" else url,
            videoUrl = if (playable) url else null,
            width = attr(tag, "width")?.toIntOrNull(),
            height = attr(tag, "height")?.toIntOrNull(),
            durationSeconds = null,
            contentDescription = title.take(200),
        )
    }

    private fun prefers(candidate: ParsedRedditMedia, current: ParsedRedditMedia): Boolean {
        if (candidate.videoUrl != null && current.videoUrl == null) return true
        val candidateWidth = listedWidth(candidate)
        val currentWidth = listedWidth(current)
        if (candidateWidth != currentWidth) return candidateWidth > currentWidth
        return pixelCount(candidate) > pixelCount(current)
    }

    private fun listedWidth(item: ParsedRedditMedia): Int =
        widthQuery(item.imageUrl.ifBlank { item.videoUrl.orEmpty() })

    private fun pixelCount(item: ParsedRedditMedia): Int = (item.width ?: 0) * (item.height ?: 0)

    private fun bestUrl(tag: String): String? {
        val fromSet = attr(tag, "srcset")?.let(::bestSrcset)
        val src = attr(tag, "src")?.takeIf { it.startsWith("https://") }
        val srcWidth = src?.let(::widthQuery) ?: -1
        return when {
            fromSet != null && fromSet.width >= srcWidth -> fromSet.url
            src != null -> src
            else -> fromSet?.url
        }
    }

    private fun bestSrcset(srcset: String): SizedUrl? {
        var best: SizedUrl? = null
        srcset.split(',').forEach { part ->
            val bits = part.trim().split(Regex("""\s+""")).filter { it.isNotEmpty() }
            val url = bits.firstOrNull()?.takeIf { it.startsWith("https://") } ?: return@forEach
            val width = bits.getOrNull(1)?.removeSuffix("w")?.toIntOrNull() ?: widthQuery(url)
            val chosen = best
            if (chosen == null || width >= chosen.width) best = SizedUrl(url, width)
        }
        return best
    }

    private data class SizedUrl(val url: String, val width: Int)

    private fun widthQuery(url: String): Int =
        url.substringAfter('?', "")
            .split('&')
            .firstOrNull { it.substringBefore('=') == "width" }
            ?.substringAfter('=')
            ?.toIntOrNull()
            ?: 0

    private fun pageOf(tag: String): Int =
        Regex("""\bslot\s*=\s*["']page-(\d+)["']""", RegexOption.IGNORE_CASE)
            .find(tag)
            ?.groupValues
            ?.get(1)
            ?.toIntOrNull()
            ?: Int.MAX_VALUE

    private fun isBackdrop(tag: String): Boolean {
        if (attr(tag, "role").equals("presentation", ignoreCase = true)) return true
        return attr(tag, "class").orEmpty().contains("post-background-image-filter")
    }

    private fun identity(item: ParsedRedditMedia): String? = identity(item.imageUrl.ifBlank { item.videoUrl.orEmpty() })

    private fun identity(url: String): String? {
        if (url.isBlank()) return null
        val uri = runCatching { URI(url) }.getOrNull() ?: return url.substringBefore('?')
        return (uri.host.orEmpty() + uri.path).lowercase(Locale.US)
    }

    private fun mediaId(url: String, postId: String, index: Int): String {
        val name = runCatching { URI(url).path }.getOrNull().orEmpty().substringAfterLast('/')
        val stem = name.substringBeforeLast('.').ifBlank { name }
        return stem.ifBlank { "$postId-$index" }
    }

    private fun isPlayableFile(url: String): Boolean {
        if (!url.startsWith("https://")) return false
        val path = url.substringBefore('?').lowercase(Locale.US)
        return path.endsWith(".mp4") || path.endsWith(".webm") || path.endsWith(".m3u8") || path.endsWith(".mpd")
    }

    private fun attr(tag: String, name: String): String? =
        Regex("""\b${Regex.escape(name)}\s*=\s*["']([^"']*)["']""", RegexOption.IGNORE_CASE)
            .find(tag)
            ?.groupValues
            ?.get(1)
            ?.let(::unescape)
            ?.takeIf { it.isNotBlank() }

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

    private val CAROUSEL = Regex(
        """<gallery-carousel\b([^>]*)>(.*?)</gallery-carousel>""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )
    private val PEEK = Regex(
        """<peek-gallery\b[^>]*>(.*?)</peek-gallery>""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )
    private val LI = Regex(
        """<li\b([^>]*)>(.*?)</li>""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )
    private val IMG = Regex("""<img\b([^>]*)>""", RegexOption.IGNORE_CASE)
    private val SOURCE = Regex("""<source\b([^>]*)>""", RegexOption.IGNORE_CASE)
}

internal fun ParsedRedditMedia.isDisplayableMedia(): Boolean {
    if (!videoUrl.isNullOrBlank() && videoUrl.startsWith("https://")) return true
    return isDirectImageUrl(imageUrl)
}

internal fun isDirectImageUrl(url: String): Boolean {
    if (!url.startsWith("https://")) return false
    val host = runCatching { URI(url).host }.getOrNull()?.lowercase(Locale.US).orEmpty()
    if (
        host == "i.redd.it" ||
        host == "preview.redd.it" ||
        host == "external-preview.redd.it" ||
        host == "i.reddituploads.com"
    ) {
        return true
    }
    if (host == "reddit.com" || host.endsWith(".reddit.com") || host == "redd.it" || host == "www.redd.it") {
        return false
    }
    val path = runCatching { URI(url).path }.getOrNull().orEmpty().lowercase(Locale.US)
    return path.endsWith(".jpg") || path.endsWith(".jpeg") || path.endsWith(".png") ||
        path.endsWith(".gif") || path.endsWith(".webp")
}

/** How long a gallery page may keep polling before the slide list is treated as complete. */
internal object RedditGalleryWait {
    const val MAX_WAITS = 16

    fun shouldWait(mediaPending: Boolean, displayableCount: Int, stablePolls: Int, waits: Int): Boolean {
        if (!mediaPending || waits >= MAX_WAITS) return false
        if (displayableCount == 0) return stablePolls < EMPTY_STABLE_POLLS
        if (waits < MIN_LOADED_WAITS) return true
        return stablePolls < LOADED_STABLE_POLLS
    }

    private const val EMPTY_STABLE_POLLS = 6
    private const val LOADED_STABLE_POLLS = 3
    private const val MIN_LOADED_WAITS = 8
}
