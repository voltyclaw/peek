package app.pane.android.data.links

import app.pane.android.data.facebook.FacebookUrls
import app.pane.android.data.instagram.InstagramStories
import app.pane.android.data.reddit.RedditUrls
import app.pane.android.data.x.XUrls
import app.pane.android.data.bluesky.BskyUrls
import app.pane.android.data.tiktok.TikTokUrls
import app.pane.android.data.youtube.YouTubeUrls
import app.pane.android.domain.model.LinkShims
import java.net.URI
import java.util.Locale

/**
 * A profile or page Pane does not render. These skip the viewer and open in the source app.
 * A post URL is never a profile, even when it sits on the same host.
 */
internal data class ProfileLink(
    val url: String,
    val packages: List<String>,
)

internal enum class PaneEntry { Viewer, ProfileHandoff }

internal fun paneEntry(url: String): PaneEntry =
    if (profileLink(url) != null) PaneEntry.ProfileHandoff else PaneEntry.Viewer

/** A profile handoff never writes Recents. The viewer is the only path that does. */
internal fun profileHandoffRecordsRecent(): Boolean = false

/** A profile or page handoff never writes History. Only a successful viewer load does. */
internal fun profileHandoffRecordsHistory(): Boolean = false

/** The mention tap itself does not write History. A later successful post view still can. */
internal fun mentionTapRecordsHistory(): Boolean = false

/** Share-in finishes back to the sender. A hub paste stays on the hub. */
internal fun profileHandoffFinishes(fromExternal: Boolean, started: Boolean): Boolean =
    fromExternal && started

internal fun profileLink(raw: String): ProfileLink? {
    val url = LinkShims.unwrap(raw.trim())
    if (!url.startsWith("http://") && !url.startsWith("https://")) return null
    if (isRenderablePost(url)) return null
    val uri = runCatching { URI(url) }.getOrNull() ?: return null
    val host = uri.host?.lowercase(Locale.US)?.removePrefix("www.") ?: return null
    val segments = uri.path.orEmpty().trim('/').split('/').filter(String::isNotEmpty)
    return xProfile(host, segments)
        ?: facebookProfile(host, segments, uri)
        ?: instagramProfile(host, segments)
        ?: redditProfile(host, segments)
        ?: threadsProfile(host, segments)
        ?: youtubeProfile(host, segments, uri)
        ?: tiktokProfile(host, segments)
        ?: blueskyProfile(url)
}

internal fun isXHost(url: String): Boolean {
    val host = runCatching { URI(LinkShims.unwrap(url.trim())).host }
        .getOrNull()
        ?.lowercase(Locale.US)
        ?.removePrefix("www.")
        ?: return false
    return host in X_HOSTS
}

private fun isRenderablePost(url: String): Boolean {
    if (XUrls.supports(url) || FacebookUrls.supports(url) || FacebookUrls.isMarketplace(url)) return true
    if (YouTubeUrls.supports(url)) return true
    if (TikTokUrls.supports(url)) return true
    if (BskyUrls.parsePost(url) != null) return true
    if (RedditUrls.supports(url)) return true
    if (InstagramStories.parse(url) != null) return true
    return isInstagramPost(url)
}

private fun isInstagramPost(url: String): Boolean {
    val uri = runCatching { URI(url) }.getOrNull() ?: return false
    val host = uri.host?.lowercase(Locale.US)?.removePrefix("www.") ?: return false
    if (host !in IG_HOSTS && host != "l.instagram.com" && host != "instagr.am") return false
    val head = uri.path.orEmpty().trim('/').substringBefore('/')
    return head in IG_POST_HEADS
}

private fun xProfile(host: String, segments: List<String>): ProfileLink? {
    if (host !in X_HOSTS) return null
    val handle = segments.firstOrNull()?.takeIf { it !in X_RESERVED && X_HANDLE.matches(it) } ?: return null
    if (segments.any { it.equals("status", ignoreCase = true) }) return null
    return ProfileLink("https://x.com/$handle", listOf(X_PACKAGE))
}

private fun facebookProfile(host: String, segments: List<String>, uri: URI): ProfileLink? {
    if (host !in FB_HOSTS) return null
    val head = segments.firstOrNull() ?: return null
    if (head in FB_RESERVED || segments.any { it in FB_POST_SEGMENTS }) return null
    val open = if (head == "profile.php") {
        val id = query(uri, "id") ?: return null
        if (query(uri, "story_fbid") != null || query(uri, "fbid") != null) return null
        "https://www.facebook.com/profile.php?id=$id"
    } else {
        if (!FB_VANITY.matches(head)) return null
        "https://www.facebook.com/$head"
    }
    return ProfileLink(open, listOf(FB_PACKAGE, FB_LITE))
}

private fun instagramProfile(host: String, segments: List<String>): ProfileLink? {
    if (host !in IG_HOSTS && host != "instagr.am") return null
    val user = segments.firstOrNull()?.takeIf { it !in IG_RESERVED && IG_USER.matches(it) } ?: return null
    return ProfileLink("https://www.instagram.com/$user/", listOf(IG_PACKAGE))
}

private fun redditProfile(host: String, segments: List<String>): ProfileLink? {
    if (host !in REDDIT_HOSTS) return null
    if (segments.any { it.equals("comments", ignoreCase = true) }) return null
    val open = when (segments.firstOrNull()?.lowercase(Locale.US)) {
        "user", "u" -> {
            val name = segments.getOrNull(1)?.takeIf { REDDIT_NAME.matches(it) } ?: return null
            "https://www.reddit.com/user/$name"
        }
        "r" -> {
            val name = segments.getOrNull(1)?.takeIf { REDDIT_NAME.matches(it) } ?: return null
            "https://www.reddit.com/r/$name"
        }
        else -> return null
    }
    return ProfileLink(open, listOf(REDDIT_PACKAGE))
}

private fun youtubeProfile(host: String, segments: List<String>, uri: URI): ProfileLink? {
    if (host !in YOUTUBE_HOSTS) return null
    if (YouTubeUrls.supports(uri.toString()) || segments.isEmpty()) return null
    val head = segments.first()
    val open = when {
        head.startsWith("@") && head.length > 1 -> "https://www.youtube.com/$head"
        head == "channel" -> segments.getOrNull(1)?.let { "https://www.youtube.com/channel/$it" }
        head == "c" -> segments.getOrNull(1)?.let { "https://www.youtube.com/c/$it" }
        head == "user" -> segments.getOrNull(1)?.let { "https://www.youtube.com/user/$it" }
        else -> null
    } ?: return null
    return ProfileLink(open, listOf(YOUTUBE_PACKAGE))
}

private fun tiktokProfile(host: String, segments: List<String>): ProfileLink? {
    if (host !in TIKTOK_HOSTS) return null
    val head = segments.firstOrNull() ?: return null
    if (!head.startsWith("@") || segments.size != 1) return null
    val handle = head.removePrefix("@")
    if (handle.isBlank()) return null
    return ProfileLink("https://www.tiktok.com/@$handle", listOf(TIKTOK_PACKAGE, TIKTOK_TRILL_PACKAGE))
}

private fun blueskyProfile(url: String): ProfileLink? {
    if (!BskyUrls.isProfile(url)) return null
    return ProfileLink(url, listOf(BskyUrls.PACKAGE))
}

private fun threadsProfile(host: String, segments: List<String>): ProfileLink? {
    if (host !in THREADS_HOSTS) return null
    val raw = segments.firstOrNull() ?: return null
    if (segments.size != 1) return null
    val user = raw.removePrefix("@").takeIf { it.isNotEmpty() && raw.startsWith("@") && THREADS_USER.matches(it) }
        ?: return null
    return ProfileLink("https://www.threads.net/@$user", listOf(THREADS_PACKAGE))
}

private fun query(uri: URI, name: String): String? =
    uri.rawQuery?.split('&')?.firstNotNullOfOrNull { part ->
        val key = part.substringBefore('=')
        val value = part.substringAfter('=', "")
        if (key == name && value.isNotBlank()) {
            runCatching { java.net.URLDecoder.decode(value, Charsets.UTF_8.name()) }.getOrDefault(value)
        } else {
            null
        }
    }

private val X_HANDLE = Regex("[A-Za-z0-9_]{1,15}")
private val FB_VANITY = Regex("[A-Za-z0-9.]{2,}")
private val IG_USER = Regex("[A-Za-z0-9._]{1,30}")
private val REDDIT_NAME = Regex("[A-Za-z0-9_]{2,21}")
private val THREADS_USER = Regex("[A-Za-z0-9._]{1,30}")

private val X_HOSTS = setOf("x.com", "mobile.x.com", "twitter.com", "mobile.twitter.com")
private val FB_HOSTS = setOf(
    "facebook.com",
    "m.facebook.com",
    "mbasic.facebook.com",
    "web.facebook.com",
    "touch.facebook.com",
    "fb.com",
)
private val IG_HOSTS = setOf("instagram.com", "m.instagram.com")
private val REDDIT_HOSTS = setOf(
    "reddit.com",
    "old.reddit.com",
    "m.reddit.com",
    "np.reddit.com",
    "new.reddit.com",
)
private val THREADS_HOSTS = setOf("threads.net", "www.threads.net")
private val YOUTUBE_HOSTS = setOf("youtube.com", "m.youtube.com", "music.youtube.com")
private val TIKTOK_HOSTS = setOf("tiktok.com", "m.tiktok.com", "vm.tiktok.com", "vt.tiktok.com", "live.tiktok.com")

private val X_RESERVED = setOf(
    "home",
    "i",
    "search",
    "explore",
    "settings",
    "hashtag",
    "intent",
    "share",
    "compose",
    "messages",
    "notifications",
    "login",
    "signup",
    "tos",
    "privacy",
    "jobs",
    "download",
    "account",
    "oauth",
)
private val FB_RESERVED = setOf(
    "share",
    "sharer",
    "sharer.php",
    "watch",
    "reel",
    "reels",
    "stories",
    "story.php",
    "permalink.php",
    "photo.php",
    "photo",
    "photos",
    "groups",
    "posts",
    "login",
    "login.php",
    "marketplace",
    "events",
    "gaming",
    "messages",
    "help",
    "settings",
    "search",
    "hashtag",
    "dialog",
    "plugins",
)
private val FB_POST_SEGMENTS = setOf("posts", "videos", "permalink", "share", "watch", "reel", "reels", "stories")
private val IG_POST_HEADS = setOf("p", "reel", "reels", "tv", "stories")
private val IG_RESERVED = IG_POST_HEADS + setOf(
    "explore",
    "accounts",
    "about",
    "directory",
    "legal",
    "emails",
    "challenge",
    "oauth",
    "direct",
    "privacy",
)

internal const val X_PACKAGE = "com.twitter.android"
internal const val FB_PACKAGE = "com.facebook.katana"
internal const val FB_LITE = "com.facebook.lite"
internal const val IG_PACKAGE = "com.instagram.android"
internal const val REDDIT_PACKAGE = "com.reddit.frontpage"
internal const val THREADS_PACKAGE = "com.instagram.barcelona"
internal const val YOUTUBE_PACKAGE = "com.google.android.youtube"
internal const val YOUTUBE_MUSIC_PACKAGE = "com.google.android.apps.youtube.music"
internal const val TIKTOK_PACKAGE = "com.zhiliaoapp.musically"
internal const val TIKTOK_TRILL_PACKAGE = "com.ss.android.ugc.trill"
