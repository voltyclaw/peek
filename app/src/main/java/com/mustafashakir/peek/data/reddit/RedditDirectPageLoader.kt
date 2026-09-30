package com.mustafashakir.peek.data.reddit

import com.mustafashakir.peek.data.resolver.PageLoadProgressElement
import com.mustafashakir.peek.domain.model.LoadProgress
import com.mustafashakir.peek.domain.model.LoadStage
import com.mustafashakir.peek.domain.repository.LoadProgressListener
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.nio.charset.StandardCharsets
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Loads a public post from Reddit's logged-out comments JSON document.
 * No OAuth client or API key is used; private and quarantined posts that Reddit
 * hides from logged-out clients fail closed.
 */
class RedditDirectPageLoader(
    private val parser: RedditJsonParser = RedditJsonParser(),
    private val connectionFactory: (String) -> HttpURLConnection = { url ->
        URI(url).toURL().openConnection() as HttpURLConnection
    },
) : RedditPageLoader {
    override val resolverId: String = "reddit-json"

    override fun supports(url: String): Boolean = RedditUrls.supports(url)

    override suspend fun resolve(url: String): ParsedRedditPost {
        if (!supports(url)) throw IllegalArgumentException("Unsupported Reddit post URL: $url")
        val listener = coroutineContext[PageLoadProgressElement]?.listener ?: LoadProgressListener {}
        listener.report(0.05f, LoadStage.Connecting)
        val pageUrl = RedditUrls.direct(url)?.canonicalUrl ?: followShareLink(url)
        val postId = RedditUrls.direct(pageUrl)?.id
            ?: throw IOException("Reddit share link did not open a public post")
        listener.report(0.35f, LoadStage.FetchingPage)
        val response = get(RedditUrls.jsonUrl(postId))
        listener.report(0.85f, LoadStage.ExtractingContent)
        val post = parser.parse(response)
            ?: throw IOException("Reddit response contained no public post")
        listener.report(1f, LoadStage.ExtractingContent)
        return post
    }

    private suspend fun followShareLink(url: String): String = withContext(Dispatchers.IO) {
        val connection = connectionFactory(url)
        try {
            prepare(connection, accept = "text/html,application/json")
            val status = connection.responseCode
            val landed = connection.url.toString()
            if (RedditUrls.direct(landed) != null) return@withContext landed
            throw IOException("Reddit share link did not open a public post (HTTP $status)")
        } finally {
            connection.disconnect()
        }
    }

    private suspend fun get(url: String): String = withContext(Dispatchers.IO) {
        val connection = connectionFactory(url)
        try {
            prepare(connection, accept = "application/json")
            val status = connection.responseCode
            val response = (if (status in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader(StandardCharsets.UTF_8)
                ?.use { it.readText() }
                .orEmpty()
            if (status == 429) throw IOException("Reddit temporarily rate-limited the request (HTTP $status)")
            if (status !in 200..299) throw IOException("Reddit returned HTTP $status")
            if (response.isBlank()) throw IOException("Reddit returned an empty response")
            if (response.trimStart().startsWith("<")) {
                throw IOException("Reddit did not return a public post")
            }
            response
        } finally {
            connection.disconnect()
        }
    }

    private fun prepare(connection: HttpURLConnection, accept: String) {
        connection.instanceFollowRedirects = true
        connection.requestMethod = "GET"
        connection.connectTimeout = CONNECT_TIMEOUT_MILLIS
        connection.readTimeout = READ_TIMEOUT_MILLIS
        connection.setRequestProperty("Accept", accept)
        connection.setRequestProperty("Accept-Language", "en-US,en;q=0.9")
        connection.setRequestProperty("User-Agent", USER_AGENT)
    }

    private fun LoadProgressListener.report(fraction: Float, stage: LoadStage) {
        onProgress(LoadProgress(fraction, stage))
    }

    private companion object {
        const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/148.0.0.0 Mobile Safari/537.36"
        const val CONNECT_TIMEOUT_MILLIS = 15_000
        const val READ_TIMEOUT_MILLIS = 30_000
    }
}
