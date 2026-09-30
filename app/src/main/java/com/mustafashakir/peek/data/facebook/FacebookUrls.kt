package com.mustafashakir.peek.data.facebook

import java.net.URI
import java.util.Locale

/**
 * Public Facebook post URLs. Profiles, feeds, and login pages are not posts.
 * Host matching stays here so a later applicationId or brand change does not touch it.
 */
object FacebookUrls {
    enum class Kind { Post, Reel, Watch, Short }

    data class Post(
        val id: String,
        val canonicalUrl: String,
        val kind: Kind,
    )

    fun supports(url: String): Boolean = parse(url) != null

    fun parse(url: String): Post? {
        val uri = runCatching { URI(url.trim()) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase(Locale.US) ?: return null
        if (scheme != "https" && scheme != "http") return null
        val host = uri.host?.lowercase(Locale.US)?.removePrefix("www.") ?: return null
        if (host !in HOSTS) return null
        val path = uri.path.orEmpty().trimEnd('/')
        if (path.isEmpty() || path == "/" || isRejectedPath(path)) return null

        if (host == "fb.watch") {
            val code = path.removePrefix("/").takeIf(::isId) ?: return null
            return Post(id = code, canonicalUrl = "https://fb.watch/$code", kind = Kind.Short)
        }

        val segments = path.split('/').filter(String::isNotEmpty)
        val storyId = query(uri, "story_fbid")
        val photoId = query(uri, "fbid")
        val watchId = query(uri, "v")

        return when {
            segments.firstOrNull() == "share" && segments.size >= 3 && isId(segments[2]) -> {
                val kind = when (segments[1]) {
                    "r", "v" -> Kind.Reel
                    else -> Kind.Post
                }
                Post(
                    id = segments[2],
                    canonicalUrl = "https://www.facebook.com/share/${segments[1]}/${segments[2]}",
                    kind = kind,
                )
            }
            segments.firstOrNull() == "reel" || segments.firstOrNull() == "reels" -> {
                val id = segments.getOrNull(1)?.takeIf(::isId) ?: return null
                Post(id, "https://www.facebook.com/reel/$id", Kind.Reel)
            }
            segments.firstOrNull() == "watch" -> {
                val id = watchId?.takeIf(::isId) ?: segments.getOrNull(1)?.takeIf(::isId) ?: return null
                Post(id, "https://www.facebook.com/watch/?v=$id", Kind.Watch)
            }
            segments.firstOrNull() == "permalink.php" || segments.firstOrNull() == "story.php" -> {
                val id = storyId?.takeIf(::isId) ?: return null
                Post(id, "https://www.facebook.com/permalink.php?story_fbid=$id", Kind.Post)
            }
            segments.firstOrNull() == "photo.php" || segments.firstOrNull() == "photo" || segments.firstOrNull() == "photos" -> {
                val id = photoId?.takeIf(::isId)
                    ?: segments.getOrNull(1)?.takeIf(::isId)
                    ?: return null
                Post(id, "https://www.facebook.com/photo/?fbid=$id", Kind.Post)
            }
            segments.size >= 3 && segments[1] == "posts" && isId(segments[2]) ->
                Post(segments[2], "https://www.facebook.com/${segments[0]}/posts/${segments[2]}", Kind.Post)
            segments.size >= 3 && segments[1] == "videos" && isId(segments[2]) ->
                Post(segments[2], "https://www.facebook.com/${segments[0]}/videos/${segments[2]}", Kind.Watch)
            segments.size >= 4 && segments[0] == "groups" &&
                (segments[2] == "permalink" || segments[2] == "posts") && isId(segments[3]) ->
                Post(
                    segments[3],
                    "https://www.facebook.com/groups/${segments[1]}/${segments[2]}/${segments[3]}",
                    Kind.Post,
                )
            else -> null
        }
    }

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
        "stories",
        "settings",
        "messages",
        "watchparty",
        "gaming",
        "friends",
        "notifications",
    )
}
