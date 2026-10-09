package app.pane.android.data.history

import app.pane.android.data.facebook.FacebookUrls
import app.pane.android.data.instagram.InstagramStories
import app.pane.android.data.reddit.RedditUrls
import app.pane.android.data.x.XUrls
import app.pane.android.data.bluesky.BskyUrls
import app.pane.android.data.tiktok.TikTokUrls
import app.pane.android.data.youtube.YouTubeUrls
import app.pane.android.domain.model.LinkShims
import app.pane.android.domain.model.LinkSource
import app.pane.android.domain.model.SourceApp
import java.net.URI
import java.util.Locale

/** One canonical post URL per history row, using the parsers the viewers already use. */
internal object HistoryUrls {
    fun canonical(url: String): String {
        val unwrapped = LinkShims.unwrap(url.trim())
        YouTubeUrls.canonical(unwrapped)?.let { return it }
        TikTokUrls.canonical(unwrapped)?.let { return it }
        BskyUrls.parsePost(unwrapped)?.let { return it.https() }
        XUrls.parse(unwrapped)?.let { return it.canonicalUrl }
        RedditUrls.direct(unwrapped)?.let { return it.canonicalUrl }
        FacebookUrls.parse(unwrapped)?.let { return it.canonicalUrl }
        instagram(unwrapped)?.let { return it }
        return unwrapped
    }

    fun sourceApp(url: String, hint: String = ""): SourceApp {
        val key = canonical(url)
        val host = hostOf(key) ?: hostOf(url)
        if (host != null && "threads.net" in host) return SourceApp.Threads
        if (YouTubeUrls.videoId(key) != null || YouTubeUrls.videoId(url) != null) return SourceApp.YouTube
        if (TikTokUrls.supports(key) || TikTokUrls.supports(url)) return SourceApp.TikTok
        if (BskyUrls.parsePost(key) != null || BskyUrls.parsePost(url) != null || BskyUrls.isHost(key) || BskyUrls.isHost(url)) {
            return SourceApp.Bluesky
        }
        return when {
            XUrls.parse(key) != null -> SourceApp.X
            RedditUrls.direct(key) != null -> SourceApp.Reddit
            FacebookUrls.parse(key) != null -> SourceApp.Facebook
            instagram(key) != null -> SourceApp.Instagram
            hint.equals("X", ignoreCase = true) || hint.equals("Twitter", ignoreCase = true) -> SourceApp.X
            hint.equals("Reddit", ignoreCase = true) -> SourceApp.Reddit
            hint.equals("Facebook", ignoreCase = true) -> SourceApp.Facebook
            hint.equals("Instagram", ignoreCase = true) -> SourceApp.Instagram
            hint.equals("Threads", ignoreCase = true) -> SourceApp.Threads
            hint.equals("YouTube", ignoreCase = true) -> SourceApp.YouTube
            hint.equals("TikTok", ignoreCase = true) -> SourceApp.TikTok
            hint.equals("Bluesky", ignoreCase = true) -> SourceApp.Bluesky
            else -> SourceApp.Other
        }
    }

    fun sourceLabel(url: String): String {
        val key = canonical(url)
        return when {
            XUrls.parse(key) != null -> LinkSource.X.name
            RedditUrls.direct(key) != null -> LinkSource.Reddit.name
            FacebookUrls.parse(key) != null -> LinkSource.Facebook.name
            instagram(key) != null -> LinkSource.Instagram.name
            else -> "Unknown"
        }
    }

    private fun hostOf(url: String): String? =
        runCatching { URI(url).host }.getOrNull()?.lowercase(Locale.US)?.removePrefix("www.")

    private fun instagram(url: String): String? {
        InstagramStories.parse(url)?.let { return it.fetchUrl }
        val uri = runCatching { URI(url) }.getOrNull() ?: return null
        if (!uri.scheme.equals("https", ignoreCase = true)) return null
        val host = uri.host?.lowercase(Locale.US)?.removePrefix("www.") ?: return null
        if (host !in INSTAGRAM_HOSTS) return null
        val match = POST_PATH.matchEntire(uri.path.orEmpty().trimEnd('/')) ?: return null
        val route = match.groupValues[1]
        val shortcode = match.groupValues[2]
        return "https://www.instagram.com/$route/$shortcode/"
    }

    private val POST_PATH = Regex("/(p|reel|reels|tv)/([A-Za-z0-9_-]+)")
    private val INSTAGRAM_HOSTS = setOf(
        "instagram.com",
        "m.instagram.com",
        "instagr.am",
    )
}
