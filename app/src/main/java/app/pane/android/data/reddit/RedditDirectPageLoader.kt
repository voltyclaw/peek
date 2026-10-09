package app.pane.android.data.reddit

import app.pane.android.data.resolver.PageLoadProgressElement
import app.pane.android.domain.model.LoadProgress
import app.pane.android.domain.model.LoadStage
import app.pane.android.domain.repository.LoadProgressListener
import android.util.Log
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withContext

/** Fired when a small comments document is ready, before the full comment page finishes. */
class RedditPreviewElement(val emit: (ParsedRedditPost) -> Unit) :
    AbstractCoroutineContextElement(RedditPreviewElement) {
    companion object Key : CoroutineContext.Key<RedditPreviewElement>
}

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

    override suspend fun resolve(url: String): ParsedRedditPost = withContext(Dispatchers.IO) {
        if (!supports(url)) throw IllegalArgumentException("Unsupported Reddit post URL: $url")
        val listener = coroutineContext[PageLoadProgressElement]?.listener ?: LoadProgressListener {}
        listener.report(0.05f, LoadStage.Connecting)
        val pageUrl = RedditUrls.fetchPageUrl(url) ?: followShareLink(url)
        val postId = RedditUrls.direct(pageUrl)?.id
            ?: throw IOException("Reddit share link did not open a public post")
        listener.report(0.35f, LoadStage.FetchingPage)
        val post = loadPost(pageUrl, postId)
        listener.report(1f, LoadStage.ExtractingContent)
        post
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

    private suspend fun loadPost(pageUrl: String, postId: String): ParsedRedditPost = coroutineScope {
        val requests = RedditFetchPlan.requests(pageUrl, postId)
        val json = requests.filter { it.url.contains(".json?") || it.url.endsWith(".json") }
        val html = requests.lastOrNull()?.takeIf { !it.url.contains(".json") }
        val old = json.filter { it.url.contains("://old.reddit.com/") }
        val primary = if (old.isNotEmpty()) old else json.take(2)
        val backup = json.filter { it !in primary }
        val failures = mutableListOf<String>()
        val started = System.nanoTime()
        val full = async {
            firstSuccessful(primary, failures) ?: firstSuccessful(backup, failures)
        }
        val previewRequest = primary.firstOrNull()?.let(::previewRequest)
        val preview = previewRequest?.let { request ->
            async {
                try {
                    parseBody(get(request))
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    rememberFailure(failures, error.message)
                    null
                }
            }
        }
        if (preview != null) {
            select {
                preview.onAwait { parsed ->
                    if (parsed != null && !full.isCompleted) {
                        coroutineContext[RedditPreviewElement]?.emit(parsed)
                        logOpen("reddit preview ${elapsed(started)}ms id=$postId")
                    }
                }
                full.onAwait { }
            }
        }
        val fullPost = full.await()
        if (fullPost != null) {
            preview?.cancel()
            logOpen("reddit ready ${elapsed(started)}ms id=$postId comments=${fullPost.comments.size}")
            return@coroutineScope fullPost
        }
        val early = preview?.let { job ->
            if (job.isCancelled) {
                null
            } else {
                try {
                    job.await()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    null
                }
            }
        }
        if (early != null) {
            logOpen("reddit ready ${elapsed(started)}ms id=$postId preview-only")
            return@coroutineScope early
        }
        if (html != null) {
            try {
                parseBody(get(html))?.let { parsed ->
                    logOpen("reddit ready ${elapsed(started)}ms id=$postId html")
                    return@coroutineScope parsed
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                rememberFailure(failures, error.message)
                log("${html.url.substringBefore('?')} -> ${error.message}")
            }
        }
        throw IOException(failureMessage(failures))
    }

    private suspend fun firstSuccessful(
        requests: List<RedditFetchPlan.Request>,
        failures: MutableList<String>,
    ): ParsedRedditPost? = coroutineScope {
        if (requests.isEmpty()) return@coroutineScope null
        val result = CompletableDeferred<ParsedRedditPost?>()
        val pending = AtomicInteger(requests.size)
        val jobs = requests.map { request ->
            async {
                try {
                    val parsed = try {
                        parseBody(get(request))
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Exception) {
                        rememberFailure(failures, error.message)
                        log("${request.url.substringBefore('?')} -> ${error.message}")
                        null
                    }
                    if (parsed != null) result.complete(parsed)
                } finally {
                    if (pending.decrementAndGet() == 0) result.complete(null)
                }
            }
        }
        val parsed = result.await()
        if (parsed != null) jobs.forEach { it.cancel() }
        parsed
    }

    private fun previewRequest(request: RedditFetchPlan.Request): RedditFetchPlan.Request? {
        if (!request.url.contains("limit=")) return null
        val previewUrl = request.url.replace(Regex("limit=\\d+"), "limit=1")
        if (previewUrl == request.url) return null
        return request.copy(url = previewUrl)
    }

    private fun parseBody(body: String): ParsedRedditPost? =
        parser.parse(body) ?: RedditShredditPost.parse(body)

    private fun rememberFailure(failures: MutableList<String>, message: String?) {
        val text = message?.takeIf { it.isNotBlank() } ?: return
        synchronized(failures) { failures += text }
    }

    private fun failureMessage(failures: List<String>): String {
        val snapshot = synchronized(failures) { failures.toList() }
        return snapshot.lastOrNull { it.contains("blocked", ignoreCase = true) || it.contains("HTTP") }
            ?: snapshot.lastOrNull()
            ?: "Reddit did not return a public post"
    }

    private fun elapsed(started: Long): Long = (System.nanoTime() - started) / 1_000_000L

    private fun logOpen(message: String) {
        runCatching { Log.i("PaneOpen", message) }
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
            val response = readBody(connection, status)
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

    private fun readBody(connection: HttpURLConnection, status: Int): String {
        val stream = if (status in 200..299) connection.inputStream else connection.errorStream
        val reader = stream?.bufferedReader(StandardCharsets.UTF_8) ?: return ""
        return if (status in 200..299) {
            reader.use { it.readText() }
        } else {
            reader.use { read ->
                val buffer = CharArray(4_096)
                val body = StringBuilder()
                while (body.length < MAX_ERROR_CHARS) {
                    val count = read.read(buffer)
                    if (count < 0) break
                    body.append(buffer, 0, minOf(count, MAX_ERROR_CHARS - body.length))
                }
                body.toString()
            }
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
        const val CONNECT_TIMEOUT_MILLIS = 6_000
        const val READ_TIMEOUT_MILLIS = 8_000
        const val MAX_SHARE_REDIRECTS = 5
        const val MAX_SHARE_HTML_CHARS = 512_000
        const val MAX_ERROR_CHARS = 16_384
    }
}
