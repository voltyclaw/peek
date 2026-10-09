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

fun interface YouTubeTransport {
    fun get(url: String): String
}

internal object HttpYouTubeTransport : YouTubeTransport {
    override fun get(url: String): String {
        val connection = java.net.URL(url).openConnection() as java.net.HttpURLConnection
        connection.instanceFollowRedirects = true
        connection.connectTimeout = 12_000
        connection.readTimeout = 12_000
        connection.setRequestProperty("Accept", "application/json")
        return try {
            val stream = if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream
            stream?.bufferedReader()?.use { it.readText() }.orEmpty()
        } finally {
            connection.disconnect()
        }
    }
}

/**
 * Data API client. [fetch] and [commentPage] run only after consent.
 * With no API key, [fetch] uses oEmbed and comments stay unavailable. It does not invent a key.
 */
class YouTubeDataApiClient(
    private val apiKey: String? = null,
    private val transport: YouTubeTransport = HttpYouTubeTransport,
) : app.pane.android.domain.youtube.YouTubeDataApi {
    private val cache = mutableMapOf<String, app.pane.android.domain.youtube.YouTubeVideo>()

    override fun fetch(videoId: String): app.pane.android.domain.youtube.YouTubeVideo =
        cache[videoId] ?: load(videoId).also { cache[videoId] = it }

    override fun invalidate(videoId: String) {
        cache.remove(videoId)
    }

    override fun commentPage(
        videoId: String,
        pageToken: String?,
        pagesLoaded: Int,
    ): app.pane.android.domain.youtube.YouTubeCommentPage {
        val query = app.pane.android.domain.youtube.YouTubeComments.query(
            videoId,
            pageToken,
            pagesLoaded,
            apiKey.orEmpty(),
        )
        return when (query) {
            is app.pane.android.domain.youtube.YouTubeComments.Query.Unavailable ->
                app.pane.android.domain.youtube.YouTubeCommentPage(
                    emptyList(),
                    null,
                    hardWall = false,
                    pagesLoaded = pagesLoaded,
                    state = app.pane.android.domain.model.YouTubeCommentsState.Unavailable,
                )
            is app.pane.android.domain.youtube.YouTubeComments.Query.HardWall ->
                app.pane.android.domain.youtube.YouTubeCommentPage(
                    emptyList(),
                    null,
                    hardWall = true,
                    pagesLoaded = pagesLoaded,
                    state = app.pane.android.domain.model.YouTubeCommentsState.Ready,
                )
            is app.pane.android.domain.youtube.YouTubeComments.Query.Page ->
                runCatching { YouTubeJson.comments(transport.get(query.url), pagesLoaded) }.getOrElse {
                    app.pane.android.domain.youtube.YouTubeCommentPage(
                        emptyList(),
                        null,
                        hardWall = false,
                        pagesLoaded = pagesLoaded,
                        state = app.pane.android.domain.model.YouTubeCommentsState.Failed,
                    )
                }
        }
    }

    private fun load(videoId: String): app.pane.android.domain.youtube.YouTubeVideo {
        val key = apiKey?.takeIf { it.isNotBlank() } ?: return runCatching {
            YouTubeJson.oembed(videoId, transport.get(app.pane.android.domain.youtube.YouTubeLinks.oembedUrl(videoId)))
        }.getOrElse { app.pane.android.domain.youtube.YouTubeVideo(videoId, commentsUnavailable = true) }
        val video = runCatching {
            YouTubeJson.video(videoId, transport.get(app.pane.android.domain.youtube.YouTubeLinks.videosUrl(videoId, key)))
        }.getOrElse { return app.pane.android.domain.youtube.YouTubeVideo(videoId, commentsUnavailable = true) }
        if (video.channelId.isBlank()) return video
        return runCatching {
            YouTubeJson.channel(video, transport.get(app.pane.android.domain.youtube.YouTubeLinks.channelsUrl(video.channelId, key)))
        }.getOrDefault(video)
    }
}
