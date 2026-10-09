package app.pane.android.data.youtube

import app.pane.android.domain.youtube.YouTubePlayer
import java.net.URI
import java.util.Locale

/** A YouTube watch, short, live, embed, or youtu.be link. Home pages are not videos. */
object YouTubeUrls {
    private val ID = Regex("""^[A-Za-z0-9_-]{11}$""")

    fun videoId(url: String): String? {
        val uri = runCatching { URI(url.trim()) }.getOrNull() ?: return null
        val host = uri.host?.lowercase(Locale.US)?.removePrefix("www.") ?: return null
        val path = uri.path.orEmpty().trim('/')
        val id = when {
            host == "youtu.be" || host == "m.youtu.be" -> path.substringBefore('/').takeIf { it.isNotEmpty() }
            host == "youtube.com" || host == "m.youtube.com" || host == "music.youtube.com" ||
                host == "youtube-nocookie.com" -> when (path.substringBefore('/')) {
                "watch" -> queryParam(uri.rawQuery, "v")
                "shorts", "embed", "live", "v" -> path.substringAfter('/').substringBefore('/')
                else -> null
            }
            else -> null
        } ?: return null
        return id.takeIf { ID.matches(it) }
    }

    fun supports(url: String): Boolean = videoId(url) != null

    fun embedUrl(url: String): String? = videoId(url)?.let(YouTubePlayer::embedUrl)

    private fun queryParam(query: String?, name: String): String? {
        if (query.isNullOrBlank()) return null
        return query.split('&').firstNotNullOfOrNull { part ->
            val key = part.substringBefore('=')
            if (key == name) part.substringAfter('=', "").takeIf { it.isNotEmpty() } else null
        }
    }
}
