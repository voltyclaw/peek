package app.pane.android.data.facebook

import java.net.URI
import java.util.Locale

/**
 * Public Facebook post URLs. Profiles, feeds, and login pages are not posts.
 * Host matching stays here so a later applicationId or brand change does not touch it.
 */
object FacebookUrls {
    enum class Kind { Post, Reel, Watch, Short, Story }

    data class Post(
        val id: String,
        val canonicalUrl: String,
        val kind: Kind,
        /** The URL Pane was given, including story query params. */
        val sourceUrl: String = canonicalUrl,
    )

    fun supports(url: String): Boolean = parse(url) != null

    /** Marketplace has no public post document Pane can read. */
    fun isMarketplace(url: String): Boolean {
        val uri = runCatching { URI(url.trim()) }.getOrNull() ?: return false
        val host = uri.host?.lowercase(Locale.US)?.removePrefix("www.") ?: return false
        if (host !in HOSTS || host == "fb.watch") return false
        val head = uri.path.orEmpty().removePrefix("/").substringBefore('/')
        return head == "marketplace"
    }

    fun parse(url: String): Post? {
        val original = url.trim()
        val uri = runCatching { URI(original) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase(Locale.US) ?: return null
        if (scheme != "https" && scheme != "http") return null
        val host = uri.host?.lowercase(Locale.US)?.removePrefix("www.") ?: return null
        if (host !in HOSTS) return null
        val path = uri.path.orEmpty().trimEnd('/')
        if (path.isEmpty() || path == "/" || isRejectedPath(path)) return null

        fun hit(id: String, canonical: String, kind: Kind): Post {
            val kept = if (kind == Kind.Story) original else canonical
            return Post(id = id, canonicalUrl = kept, kind = kind, sourceUrl = original)
        }

        if (host == "fb.watch") {
            val parts = path.removePrefix("/").split('/').filter(String::isNotEmpty)
            val code = when {
                parts.firstOrNull() == "story" -> parts.getOrNull(1)
                else -> parts.firstOrNull()
            }?.takeIf(::isId) ?: return null
            val kind = if (isStoryQuery(uri) || parts.firstOrNull() == "story") Kind.Story else Kind.Short
            val canonical = if (kind == Kind.Story) original else "https://fb.watch/$code"
            return hit(code, canonical, kind)
        }

        val segments = path.split('/').filter(String::isNotEmpty)
        val storyId = query(uri, "story_fbid")
        val photoId = query(uri, "fbid")
        val watchId = query(uri, "v")

        if (segments.firstOrNull() == "stories") {
            val ids = segments.drop(1).filter(::isId)
            if (ids.isEmpty()) return null
            return hit(ids.joinToString("/"), original, Kind.Story)
        }

        return when {
            segments.firstOrNull() == "share" && segments.size >= 3 && isId(segments[2]) -> {
                val kind = when (segments[1]) {
                    "r", "v" -> Kind.Reel
                    "s" -> Kind.Story
                    else -> Kind.Post
                }
                val canonical = if (kind == Kind.Story) {
                    original
                } else {
                    "https://www.facebook.com/share/${segments[1]}/${segments[2]}"
                }
                hit(segments[2], canonical, kind)
            }
            segments.firstOrNull() == "reel" || segments.firstOrNull() == "reels" -> {
                val id = segments.getOrNull(1)?.takeIf(::isId) ?: return null
                hit(id, "https://www.facebook.com/reel/$id", Kind.Reel)
            }
            segments.firstOrNull() == "watch" -> {
                val id = watchId?.takeIf(::isId) ?: segments.getOrNull(1)?.takeIf(::isId) ?: return null
                hit(id, "https://www.facebook.com/watch/?v=$id", Kind.Watch)
            }
            segments.firstOrNull() == "permalink.php" || segments.firstOrNull() == "story.php" -> {
                val id = storyId?.takeIf(::isId) ?: return null
                if (isStoryQuery(uri)) {
                    hit(id, original, Kind.Story)
                } else {
                    hit(id, "https://www.facebook.com/permalink.php?story_fbid=$id", Kind.Post)
                }
            }
            segments.firstOrNull() == "photo.php" || segments.firstOrNull() == "photo" || segments.firstOrNull() == "photos" -> {
                val id = photoId?.takeIf(::isId)
                    ?: segments.getOrNull(1)?.takeIf(::isId)
                    ?: return null
                hit(id, "https://www.facebook.com/photo/?fbid=$id", Kind.Post)
            }
            segments.size >= 3 && segments[1] == "posts" && isId(segments[2]) ->
                hit(segments[2], "https://www.facebook.com/${segments[0]}/posts/${segments[2]}", Kind.Post)
            segments.size >= 3 && segments[1] == "videos" && isId(segments[2]) ->
                hit(segments[2], "https://www.facebook.com/${segments[0]}/videos/${segments[2]}", Kind.Watch)
            segments.size >= 4 && segments[0] == "groups" &&
                (segments[2] == "permalink" || segments[2] == "posts") && isId(segments[3]) ->
                hit(
                    segments[3],
                    "https://www.facebook.com/groups/${segments[1]}/${segments[2]}/${segments[3]}",
                    Kind.Post,
                )
            else -> null
        }
    }

    /** Page slug from a post or story URL, when it is not a numeric id or a route name. */
    fun pageHandle(url: String): String? {
        val post = parse(url) ?: return null
        val uri = runCatching { URI(post.sourceUrl) }.getOrNull() ?: return null
        val segments = uri.path.orEmpty().split('/').filter(String::isNotEmpty)
        val candidate = when (post.kind) {
            Kind.Story -> segments.getOrNull(1)
            else -> segments.firstOrNull()
        } ?: return null
        if (candidate.lowercase(Locale.US) in RESERVED_HANDLES) return null
        if (candidate.all(Char::isDigit)) return null
        if (!HANDLE.matches(candidate)) return null
        return candidate
    }

    private fun isStoryQuery(uri: URI): Boolean =
        query(uri, "bucket_id") != null || query(uri, "view_single") != null

    private fun isRejectedPath(path: String): Boolean {
        val head = path.removePrefix("/").substringBefore('/')
        return head in REJECTED
    }

    private fun query(uri: URI, name: String): String? {
        val query = uri.rawQuery ?: return null
        return query.split('&').firstNotNullOfOrNull { part ->
            val key = part.substringBefore('=')
            val value = part.substringAfter('=', "")
            if (key == name && value.isNotBlank()) decode(value) else null
        }
    }

    private fun decode(value: String): String =
        runCatching { java.net.URLDecoder.decode(value, Charsets.UTF_8.name()) }.getOrDefault(value)

    private fun isId(value: String): Boolean = ID.matches(value)

    private val ID = Regex("[A-Za-z0-9._-]{2,}")
    private val HANDLE = Regex("[A-Za-z0-9.]{2,}")
    private val RESERVED_HANDLES = setOf(
        "stories",
        "share",
        "reel",
        "reels",
        "watch",
        "photo",
        "photos",
        "groups",
        "story.php",
        "permalink.php",
        "photo.php",
        "highlights",
    )
    private val HOSTS = setOf(
        "facebook.com",
        "m.facebook.com",
        "mbasic.facebook.com",
        "fb.com",
        "fb.watch",
    )
    private val REJECTED = setOf(
        "login",
        "login.php",
        "recover",
        "checkpoint",
        "marketplace",
        "events",
        "settings",
        "messages",
        "watchparty",
        "gaming",
        "friends",
        "notifications",
    )
}
