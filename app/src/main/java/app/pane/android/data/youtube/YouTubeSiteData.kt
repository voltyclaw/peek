package app.pane.android.data.youtube

import android.webkit.CookieManager
import android.webkit.WebStorage
import app.pane.android.domain.youtube.YouTubeSiteData

/** Cookie names from one Cookie header, each rewritten so the browser drops it. */
internal fun expiredYouTubeCookies(cookieHeader: String?): List<String> {
    if (cookieHeader.isNullOrBlank()) return emptyList()
    return cookieHeader.split(';')
        .map { it.substringBefore('=').trim() }
        .filter { it.isNotEmpty() }
        .distinct()
        .map { name -> "$name=; Max-Age=0; Path=/" }
}

/**
 * Drops YouTube cookies and WebView site storage after consent is withdrawn.
 * Cookie expiry is limited to YouTube origins. Android's [WebStorage.deleteAllData]
 * has no per-origin method, so site storage for this app's WebView is cleared with it.
 */
class AndroidYouTubeSiteData : YouTubeSiteData {
    override fun clear() {
        runCatching {
            val manager = CookieManager.getInstance()
            ORIGINS.forEach { origin ->
                expiredYouTubeCookies(manager.getCookie(origin)).forEach { cookie ->
                    manager.setCookie(origin, cookie)
                }
            }
            manager.flush()
        }
        runCatching { WebStorage.getInstance().deleteAllData() }
    }

    companion object {
        val ORIGINS = listOf(
            "https://www.youtube.com",
            "https://youtube.com",
            "https://m.youtube.com",
            "https://www.youtube-nocookie.com",
            "https://youtube-nocookie.com",
            "https://www.youtube-nocookie.com/embed",
        )
    }
}

/**
 * Data API client. [fetch] is safe to call only from [app.pane.android.domain.youtube.YouTubeSession]
 * after consent. With no API key this returns the id and does not open a connection.
 */
class YouTubeDataApiClient(
    private val apiKey: String? = null,
) : app.pane.android.domain.youtube.YouTubeDataApi {
    override fun fetch(videoId: String): app.pane.android.domain.youtube.YouTubeVideo {
        val key = apiKey?.takeIf { it.isNotBlank() } ?: return app.pane.android.domain.youtube.YouTubeVideo(videoId)
        val url = "https://www.googleapis.com/youtube/v3/videos?part=snippet&id=$videoId&key=$key"
        runCatching { java.net.URL(url).openStream().close() }
        return app.pane.android.domain.youtube.YouTubeVideo(videoId)
    }
}
