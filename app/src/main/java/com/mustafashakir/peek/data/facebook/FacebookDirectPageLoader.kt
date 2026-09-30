package com.mustafashakir.peek.data.facebook

import com.mustafashakir.peek.data.resolver.PageLoadProgressElement
import com.mustafashakir.peek.domain.model.LoadProgress
import com.mustafashakir.peek.domain.model.LoadStage
import com.mustafashakir.peek.domain.repository.LoadProgressListener
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

    override suspend fun resolve(url: String): ParsedFacebookPost {
        val post = FacebookUrls.parse(url)
            ?: throw IllegalArgumentException("Unsupported Facebook post URL: $url")
        val listener = coroutineContext[PageLoadProgressElement]?.listener ?: LoadProgressListener {}
        listener.onProgress(LoadProgress(0.08f, LoadStage.Connecting))
        var last = FacebookDocument.UNAVAILABLE
        targets(post).forEachIndexed { index, target ->
            listener.onProgress(LoadProgress(0.2f + index * 0.25f, LoadStage.FetchingPage))
            val html = runCatching { get(target) }.getOrElse { error ->
                last = error.message ?: last
                return@forEachIndexed
            }
            FacebookDocument.parse(html, post.id, post.canonicalUrl)?.let {
                listener.onProgress(LoadProgress(1f, LoadStage.ExtractingContent))
                return it
            }
            if (html.contains("login_form", ignoreCase = true)) last = FacebookDocument.LOGIN
        }
        throw IOException(last)
    }

    private fun targets(post: FacebookUrls.Post): List<String> {
        val encoded = URLEncoder.encode(post.canonicalUrl, StandardCharsets.UTF_8.name())
        val plugin = "https://www.facebook.com/plugins/post.php?href=$encoded&show_text=true&width=500"
        return when (post.kind) {
            FacebookUrls.Kind.Post -> listOf(plugin, post.canonicalUrl)
            else -> listOf(post.canonicalUrl, plugin)
        }
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
