package com.mustafashakir.peek.data.reddit

import com.mustafashakir.peek.data.resolver.PageLoadProgressElement
import com.mustafashakir.peek.domain.model.LoadProgress
import com.mustafashakir.peek.domain.model.LoadStage
import com.mustafashakir.peek.domain.repository.LoadProgressListener
import android.util.Log
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
        val pageUrl = RedditUrls.fetchPageUrl(url) ?: followShareLink(url)
        val postId = RedditUrls.direct(pageUrl)?.id
            ?: throw IOException("Reddit share link did not open a public post")
        listener.report(0.35f, LoadStage.FetchingPage)
        val post = loadPost(pageUrl, postId)
        listener.report(1f, LoadStage.ExtractingContent)
        return post
    }

    private suspend fun followShareLink(url: String): String = withContext(Dispatchers.IO) {
        resolveShareTarget(url, hopsLeft = MAX_SHARE_REDIRECTS)
    }

    private fun resolveShareTarget(url: String, hopsLeft: Int): String {
        RedditUrls.fetchPageUrl(url)?.let { return it }
        if (hopsLeft == 0) throw IOException("Reddit share link redirected too many times")
        val connection = connectionFactory(url)
        try {
            prepare(
                connection,
                accept = "text/html,application/xhtml+xml,application/json",
                followRedirects = false,
                userAgent = RedditFetchPlan.DESKTOP_USER_AGENT,
            )
            val status = connection.responseCode
            val landed = connection.url.toString()
            RedditUrls.fetchPageUrl(landed)?.let { return it }
            if (status in 300..399) {
                val location = connection.getHeaderField("Location")?.takeIf { it.isNotBlank() }
                    ?: throw IOException("Reddit share link redirected without a destination (HTTP $status)")
                val next = RedditSharePage.absolutize(location, url)
                if (next == url) throw IOException("Reddit share link redirected to itself")
                return resolveShareTarget(next, hopsLeft - 1)
            }
            val body = readLimited(connection, status)
            return RedditSharePage.postUrl(body)
                ?: throw IOException(
                    if (status in 200..299) {
                        "Reddit share link did not open a public post"
                    } else {
                        "Reddit share link did not open a public post (HTTP $status)"
                    },
                )
        } finally {
            connection.disconnect()
        }
    }

    private fun readLimited(connection: HttpURLConnection, status: Int): String {
        val stream = if (status in 200..299) connection.inputStream else connection.errorStream
        return stream?.bufferedReader(StandardCharsets.UTF_8)?.use { reader ->
            val buffer = CharArray(8_192)
            val body = StringBuilder()
            while (body.length < MAX_SHARE_HTML_CHARS) {
                val read = reader.read(buffer)
                if (read < 0) break
                val remaining = MAX_SHARE_HTML_CHARS - body.length
                body.append(buffer, 0, minOf(read, remaining))
            }
            body.toString()
        }.orEmpty()
    }

    private suspend fun loadPost(pageUrl: String, postId: String): ParsedRedditPost {
        var blocked: String? = null
        var last = "Reddit did not return a public post"
        for (request in RedditFetchPlan.requests(pageUrl, postId)) {
            try {
                val body = get(request)
                parser.parse(body)?.let { return it }
                RedditShredditPost.parse(body)?.let { return it }
                last = "Reddit did not return a public post"
                log("no public post from ${request.url.substringBefore('?')}")
            } catch (error: IOException) {
                last = error.message ?: last
                if (last.contains("blocked", ignoreCase = true) || last.contains("HTTP")) {
                    blocked = last
                }
                log("${request.url.substringBefore('?')} -> $last")
            }
        }
        throw IOException(blocked ?: last)
    }

    private suspend fun get(request: RedditFetchPlan.Request): String = withContext(Dispatchers.IO) {
        read(request, cookie = null, allowCookieRetry = true)
    }

    private fun read(
        request: RedditFetchPlan.Request,
        cookie: String?,
        allowCookieRetry: Boolean,
    ): String {
        val connection = connectionFactory(request.url)
        try {
            prepare(connection, accept = request.accept, userAgent = request.userAgent)
            if (!cookie.isNullOrBlank()) connection.setRequestProperty("Cookie", cookie)
            val status = connection.responseCode
            val response = (if (status in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader(StandardCharsets.UTF_8)
                ?.use { it.readText() }
                .orEmpty()
            if (allowCookieRetry && status == 403) {
                val setCookie = connection.getHeaderField("Set-Cookie")
                    ?.substringBefore(';')
                    ?.takeIf { it.isNotBlank() }
                if (setCookie != null) {
                    connection.disconnect()
                    return read(request, setCookie, allowCookieRetry = false)
                }
            }
            if (status == 429 || status == 403 || response.contains("whoa there", ignoreCase = true)) {
                throw IOException("Reddit blocked the request (HTTP $status)")
            }
            if (status !in 200..299) throw IOException("Reddit returned HTTP $status")
            if (response.isBlank()) throw IOException("Reddit returned an empty response")
            return response
        } finally {
            connection.disconnect()
        }
    }

    private fun prepare(
        connection: HttpURLConnection,
        accept: String,
        followRedirects: Boolean = true,
        userAgent: String,
    ) {
        connection.instanceFollowRedirects = followRedirects
        connection.requestMethod = "GET"
        connection.connectTimeout = CONNECT_TIMEOUT_MILLIS
        connection.readTimeout = READ_TIMEOUT_MILLIS
        connection.setRequestProperty("Accept", accept)
        connection.setRequestProperty("Accept-Language", "en-US,en;q=0.9")
        connection.setRequestProperty("User-Agent", userAgent)
    }

    private fun LoadProgressListener.report(fraction: Float, stage: LoadStage) {
        onProgress(LoadProgress(fraction, stage))
    }

    private fun log(message: String) {
        runCatching { Log.i(LOG_TAG, message) }
    }

    private companion object {
        const val LOG_TAG = "PeekReddit"
        const val CONNECT_TIMEOUT_MILLIS = 15_000
        const val READ_TIMEOUT_MILLIS = 30_000
        const val MAX_SHARE_REDIRECTS = 5
        const val MAX_SHARE_HTML_CHARS = 512_000
    }
}
