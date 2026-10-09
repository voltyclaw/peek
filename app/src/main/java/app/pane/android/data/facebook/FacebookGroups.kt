package app.pane.android.data.facebook

import java.net.URI
import java.util.Locale

/**
 * A logged-out share link can land on the group's About page.
 * That page is not the post. A public group permalink that still carries the post is.
 */
object FacebookGroups {
    fun isStandIn(html: String, requestedUrl: String, fetchUrl: String): Boolean {
        if (html.isBlank()) return false
        val metas = metas(html)
        val ogUrl = metas["og:url"]
        val canonical = canonical(html)
        val ogType = metas["og:type"].orEmpty()
        val candidates = listOfNotNull(ogUrl, canonical, fetchUrl).filter { it.isNotBlank() }
        if (candidates.none(::underGroups)) return false
        val typeIsPost = ogType.contains("article", ignoreCase = true) ||
            ogType.contains("video", ignoreCase = true)
        val postIds = candidates.mapNotNull(::groupPostId)
        val requestedId = FacebookUrls.parse(requestedUrl)?.id
        val matchesRequest = !requestedId.isNullOrBlank() && (
            postIds.any { it == requestedId } || candidates.any { requestedId in it }
        )
        if (postIds.isNotEmpty() && (typeIsPost || matchesRequest)) return false
        if (matchesRequest && typeIsPost) return false
        val groupRoot = candidates.any(::isGroupRoot)
        val describesGroup = groupRoot && !typeIsPost
        return describesGroup || postIds.isEmpty()
    }

    private fun underGroups(url: String): Boolean {
        val segments = segments(url)
        return segments.firstOrNull() == "groups"
    }

    private fun isGroupRoot(url: String): Boolean {
        val segments = segments(url)
        return segments.firstOrNull() == "groups" && segments.size <= 2
    }

    private fun groupPostId(url: String): String? {
        val segments = segments(url)
        if (segments.firstOrNull() != "groups") return null
        val marker = segments.getOrNull(2) ?: return null
        if (marker != "permalink" && marker != "posts") return null
        return segments.getOrNull(3)?.takeIf { it.isNotBlank() }
    }

    private fun segments(url: String): List<String> {
        val path = runCatching { URI(url.trim()).path }.getOrNull().orEmpty()
        return path.split('/').filter { it.isNotEmpty() }.map { it.lowercase(Locale.US) }
    }

    private fun metas(html: String): Map<String, String> {
        val out = mutableMapOf<String, String>()
        fun put(key: String, value: String) {
            val name = key.lowercase(Locale.US)
            val text = decode(value)
            if (name.isNotBlank() && text.isNotBlank()) out.putIfAbsent(name, text)
        }
        META_PROP.findAll(html).forEach { put(it.groupValues[1], it.groupValues[2]) }
        META_CONTENT_FIRST.findAll(html).forEach { put(it.groupValues[2], it.groupValues[1]) }
        return out
    }

    private fun canonical(html: String): String? =
        CANONICAL.find(html)?.groupValues?.get(1)?.let(::decode)?.takeIf { it.isNotBlank() }

    private fun decode(value: String): String = value
        .replace("&amp;", "&")
        .replace("&#39;", "'")
        .replace("&quot;", "\"")
        .trim()

    private val META_PROP = Regex(
        """<meta[^>]*?(?:property|name)\s*=\s*["']([^"']+)["'][^>]*?content\s*=\s*["']([^"']*)["']""",
        RegexOption.IGNORE_CASE,
    )
    private val META_CONTENT_FIRST = Regex(
        """<meta[^>]*?content\s*=\s*["']([^"']*)["'][^>]*?(?:property|name)\s*=\s*["']([^"']+)["']""",
        RegexOption.IGNORE_CASE,
    )
    private val CANONICAL = Regex(
        """<link[^>]+rel=["']canonical["'][^>]+href=["']([^"']+)["']""",
        RegexOption.IGNORE_CASE,
    )
}
