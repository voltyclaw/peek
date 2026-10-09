package app.pane.android.data.instagram

import java.net.URI
import java.util.Locale

/**
 * Instagram story links, including the query a share sheet appends (`igsh`, `utm_source`).
 * The media id stays in the path. Query parameters are kept for the handoff back to Instagram.
 */
object InstagramStories {
    data class Story(
        val username: String,
        val mediaId: String,
        val cacheId: String,
        /** https story URL with the original path and query. */
        val fetchUrl: String,
    )

    fun isStoryUrl(url: String): Boolean {
        val uri = https(url) ?: return false
        return segments(uri).firstOrNull() == "stories"
    }

    /** A single story: `/stories/<user>/<id>` or `/stories/highlights/<id>`. */
    fun parse(url: String): Story? {
        val uri = https(url) ?: return null
        val segments = segments(uri)
        if (segments.firstOrNull() != "stories" || segments.size < 3) return null
        val username = segments[1]
        val mediaId = segments[2]
        if (!TOKEN.matches(username) || !TOKEN.matches(mediaId)) return null
        return Story(
            username = username,
            mediaId = mediaId,
            cacheId = "story:$username:$mediaId",
            fetchUrl = fetchUrl(uri),
        )
    }

    private fun fetchUrl(uri: URI): String {
        val path = uri.rawPath.orEmpty().trimEnd('/').ifBlank { "/" }
        val query = uri.rawQuery?.takeIf { it.isNotBlank() }?.let { "?$it" }.orEmpty()
        return "https://www.instagram.com$path$query"
    }

    private fun https(url: String): URI? {
        val uri = runCatching { URI(url.trim()) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase(Locale.US) ?: return null
        if (scheme != "https") return null
        val host = uri.host?.lowercase(Locale.US)?.removePrefix("www.") ?: return null
        if (host != "instagram.com" && host != "m.instagram.com" && host != "instagr.am") return null
        return uri
    }

    private fun segments(uri: URI): List<String> =
        uri.path.orEmpty().split('/').filter(String::isNotEmpty)

    private val TOKEN = Regex("[A-Za-z0-9._-]{1,}")
}
