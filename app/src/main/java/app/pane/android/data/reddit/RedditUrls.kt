package app.pane.android.data.reddit

import java.net.URI
import java.util.Locale

/** Recognizes public Reddit post URLs and the logged-out JSON document for a post id. */
object RedditUrls {
    data class DirectPost(val id: String, val canonicalUrl: String)

    fun supports(url: String): Boolean = direct(url) != null || isShareLink(url)

    fun direct(url: String): DirectPost? {
        val uri = parseHttps(url) ?: return null
        val host = uri.host?.lowercase(Locale.US) ?: return null
        val path = normalizedPath(uri)
        val id = when (host) {
            in POST_HOSTS -> commentsId(path) ?: galleryId(path)
            in SHORT_HOSTS -> shortId(path)
            else -> null
        } ?: return null
        val canonicalId = id.lowercase(Locale.US)
        return DirectPost(
            id = canonicalId,
            canonicalUrl = "https://www.reddit.com/comments/$canonicalId/",
        )
    }

    fun isShareLink(url: String): Boolean {
        val uri = parseHttps(url) ?: return false
        val host = uri.host?.lowercase(Locale.US) ?: return false
        if (host !in POST_HOSTS) return false
        return SHARE_PATH.matches(normalizedPath(uri))
    }

    fun jsonUrl(id: String): String = jsonCandidates(id).first()

    /**
     * Logged-out comments documents. The full permalink on `old.reddit.com` is first.
     * Short `/comments/{id}.json` URLs follow, on old and then www.
     * [commentsPath] is the path only (`/r/{sub}/comments/{id}/…`), with tracking query removed.
     */
    fun jsonCandidates(id: String, commentsPath: String? = null): List<String> {
        val canonicalId = id.lowercase(Locale.US)
        val query = "raw_json=1&limit=$COMMENT_LIMIT"
        val urls = LinkedHashSet<String>()
        val path = commentsPath?.substringBefore('?')?.trimEnd('/')
            ?.takeIf { it.contains("/comments/") }
        if (path != null) {
            urls += "https://old.reddit.com$path.json?$query"
            urls += "https://www.reddit.com$path.json?$query"
        }
        urls += "https://old.reddit.com/comments/$canonicalId.json?$query"
        urls += "https://www.reddit.com/comments/$canonicalId.json?$query"
        return urls.toList()
    }

    /** Path of a comments URL, without a query string. Null for short links and galleries. */
    fun commentsPath(url: String): String? {
        val uri = parseHttps(url) ?: return null
        if (direct(url) == null) return null
        val path = normalizedPath(uri)
        return path.takeIf { COMMENTS_PATH.matches(it) && it.contains("/comments/") }
    }

    /**
     * Comments page used after a share link resolves. Keeps `/r/{sub}/comments/{id}/…`
     * and drops tracking parameters. Short links stay on the canonical comments URL.
     */
    fun fetchPageUrl(url: String): String? {
        val direct = direct(url) ?: return null
        val path = commentsPath(url)
        return if (path != null) "https://www.reddit.com$path" else direct.canonicalUrl
    }

    private fun parseHttps(url: String): URI? {
        val uri = runCatching { URI(url) }.getOrNull() ?: return null
        if (!uri.scheme.equals("https", ignoreCase = true)) return null
        return uri
    }

    private fun normalizedPath(uri: URI): String =
        uri.path.orEmpty().trimEnd('/').removeSuffix(".json")

    private fun commentsId(path: String): String? =
        COMMENTS_PATH.matchEntire(path)?.groupValues?.get(1)

    private fun galleryId(path: String): String? =
        GALLERY_PATH.matchEntire(path)?.groupValues?.get(1)

    private fun shortId(path: String): String? =
        SHORT_PATH.matchEntire(path)?.groupValues?.get(1)

    private const val COMMENT_LIMIT = 100

    private val POST_HOSTS = setOf(
        "reddit.com",
        "www.reddit.com",
        "old.reddit.com",
        "np.reddit.com",
        "new.reddit.com",
        "m.reddit.com",
    )
    private val SHORT_HOSTS = setOf("redd.it", "www.redd.it")
    private val COMMENTS_PATH = Regex("""^(?:/r/[A-Za-z0-9_]{1,50})?/comments/([A-Za-z0-9]{1,12})(?:/.*)?$""")
    private val GALLERY_PATH = Regex("""^/gallery/([A-Za-z0-9]{1,12})$""")
    private val SHORT_PATH = Regex("""^/([A-Za-z0-9]{1,12})$""")
    private val SHARE_PATH = Regex("""^/r/[A-Za-z0-9_]{1,50}/s/([A-Za-z0-9_-]{3,32})$""")
}
