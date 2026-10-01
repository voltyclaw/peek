package app.pane.android.data.facebook

import app.pane.android.data.resolver.PageLoadProgressElement
import app.pane.android.domain.model.LoadProgress
import app.pane.android.domain.model.LoadStage
import app.pane.android.domain.repository.LoadProgressListener
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Logged-out fetch of a public Facebook post. The embed plugin is tried for ordinary posts.
 * Reels, watch links, and fb.watch open the page itself. No Facebook login or token is used.
 */
class FacebookDirectPageLoader(
    private val connectionFactory: (String) -> HttpURLConnection = { url ->
        URI(url).toURL().openConnection() as HttpURLConnection
    },
) : FacebookPageLoader {
    override val resolverId: String = "facebook-html"

    override fun supports(url: String): Boolean = FacebookUrls.supports(url)

    override suspend fun resolve(url: String): ParsedFacebookPost = withContext(Dispatchers.IO) {
        val post = FacebookUrls.parse(url)
            ?: throw IllegalArgumentException("Unsupported Facebook post URL: $url")
        val listener = coroutineContext[PageLoadProgressElement]?.listener ?: LoadProgressListener {}
        listener.onProgress(LoadProgress(0.08f, LoadStage.Connecting))
        var last = FacebookDocument.UNAVAILABLE
        var best: ParsedFacebookPost? = null
        targets(post).forEachIndexed { index, target ->
            listener.onProgress(LoadProgress(0.2f + index * 0.15f, LoadStage.FetchingPage))
            val html = runCatching { get(target) }.getOrElse { error ->
                last = error.message ?: last
                return@forEachIndexed
            }
            val parsed = FacebookDocument.parse(html, post.id, post.canonicalUrl)
            if (parsed != null) {
                val chosen = longerCaption(best, parsed)
                best = chosen
                if (!FacebookDocument.isTruncatedPreview(chosen.text) && FacebookDocument.hasDistinctAuthor(chosen)) {
                    listener.onProgress(LoadProgress(1f, LoadStage.ExtractingContent))
                    return@withContext chosen
                }
            }
            if (html.contains("login_form", ignoreCase = true)) last = FacebookDocument.LOGIN
        }
        best?.let {
            listener.onProgress(LoadProgress(1f, LoadStage.ExtractingContent))
            return@withContext it
        }
        throw IOException(last)
    }

    private fun longerCaption(current: ParsedFacebookPost?, next: ParsedFacebookPost): ParsedFacebookPost {
        if (current == null) return next
        val longer = if (next.text.length > current.text.length) next else current
        val other = if (longer === next) current else next
        return longer.copy(
            imageUrls = longer.imageUrls.ifEmpty { other.imageUrls },
            videoUrl = longer.videoUrl ?: other.videoUrl,
            author = preferredAuthor(longer, other),
            authorAvatarUrl = longer.authorAvatarUrl ?: other.authorAvatarUrl,
        )
    }

    private fun preferredAuthor(primary: ParsedFacebookPost, other: ParsedFacebookPost): String {
        val primaryAuthor = primary.copy(author = primary.author)
        if (FacebookDocument.hasDistinctAuthor(primaryAuthor)) return primary.author
        if (FacebookDocument.hasDistinctAuthor(other)) return other.author
        return primary.author
    }

    private fun targets(post: FacebookUrls.Post): List<String> {
        val encoded = URLEncoder.encode(post.canonicalUrl, StandardCharsets.UTF_8.name())
        val plugin = "https://www.facebook.com/plugins/post.php?href=$encoded&show_text=true&width=500"
        val extras = mobileCopies(post.canonicalUrl)
        return when (post.kind) {
            FacebookUrls.Kind.Post -> (listOf(plugin) + extras + post.canonicalUrl)
            else -> (listOf(post.canonicalUrl) + extras + plugin)
        }.distinct()
    }

    private fun mobileCopies(canonical: String): List<String> {
        val mbasic = canonical
            .replace("://www.facebook.com/", "://mbasic.facebook.com/")
            .replace("://m.facebook.com/", "://mbasic.facebook.com/")
            .replace("://facebook.com/", "://mbasic.facebook.com/")
        val touch = canonical
            .replace("://www.facebook.com/", "://m.facebook.com/")
            .replace("://mbasic.facebook.com/", "://m.facebook.com/")
        return listOf(mbasic, touch).filter { it != canonical && it.startsWith("https://") }
    }

    private suspend fun get(url: String): String = withContext(Dispatchers.IO) {
        val connection = connectionFactory(url)
        try {
            connection.instanceFollowRedirects = true
            connection.requestMethod = "GET"
            connection.connectTimeout = 8_000
            connection.readTimeout = 12_000
            connection.setRequestProperty("Accept", "text/html,application/xhtml+xml")
            connection.setRequestProperty("Accept-Language", "en-US,en;q=0.9")
            connection.setRequestProperty("User-Agent", USER_AGENT)
            val status = connection.responseCode
            val body = (if (status in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader(StandardCharsets.UTF_8)
                ?.use { reader ->
                    val buffer = CharArray(8_192)
                    val text = StringBuilder()
                    while (text.length < MAX_CHARS) {
                        val count = reader.read(buffer)
                        if (count < 0) break
                        text.append(buffer, 0, minOf(count, MAX_CHARS - text.length))
                    }
                    text.toString()
                }
                .orEmpty()
            if (status !in 200..299) {
                throw IOException("Facebook returned HTTP $status")
            }
            if (body.isBlank()) throw IOException("Facebook returned an empty page")
            body
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val MAX_CHARS = 750_000
        const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
    }
}
