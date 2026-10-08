package app.pane.android.data.facebook

import android.util.Log
import app.pane.android.data.resolver.PageLoadProgressElement
import app.pane.android.domain.model.LoadProgress
import app.pane.android.domain.model.StoryUnavailableException
import app.pane.android.domain.model.LoadStage
import app.pane.android.domain.repository.LoadProgressListener
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

/** Fired with the first usable Facebook document while other hosts are still merging. */
class FacebookPreviewElement(val emit: (ParsedFacebookPost) -> Unit) :
    AbstractCoroutineContextElement(FacebookPreviewElement) {
    companion object Key : CoroutineContext.Key<FacebookPreviewElement>
}

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
        val opened = FacebookUrls.parse(url)
            ?: throw IllegalArgumentException("Unsupported Facebook post URL: $url")
        val post = resolveShareShort(opened)
        val listener = coroutineContext[PageLoadProgressElement]?.listener ?: LoadProgressListener {}
        listener.onProgress(LoadProgress(0.08f, LoadStage.Connecting))
        val started = System.nanoTime()
        coroutineScope {
            val pages = targets(post)
            val pluginUrl = pages.firstOrNull { it.contains("plugins/post.php") }
            val channel = Channel<FetchedPage>(Channel.UNLIMITED)
            val pending = AtomicInteger(pages.size)
            val lastError = AtomicReference(FacebookDocument.UNAVAILABLE)
            val jobs = pages.map { target ->
                async {
                    var parsed: ParsedFacebookPost? = null
                    try {
                        val html = try {
                            get(target)
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (error: Exception) {
                            lastError.set(error.message ?: lastError.get())
                            null
                        }
                        if (html != null) {
                            if (html.contains("login_form", ignoreCase = true)) {
                                lastError.set(FacebookDocument.LOGIN)
                            }
                            parsed = FacebookDocument.parse(html, post.id, post.canonicalUrl)
                        }
                        channel.send(FetchedPage(target, parsed))
                    } finally {
                        if (pending.decrementAndGet() == 0) channel.close()
                    }
                }
            }
            var best: ParsedFacebookPost? = null
            var emitted = false
            var pluginFinished = pluginUrl == null
            var received = 0
            val expectVideo = post.kind == FacebookUrls.Kind.Reel ||
                post.kind == FacebookUrls.Kind.Watch ||
                post.kind == FacebookUrls.Kind.Short
            try {
                for (fetch in channel) {
                    received += 1
                    if (fetch.url == pluginUrl) pluginFinished = true
                    val parsed = fetch.post ?: continue
                    val current = longerCaption(best, parsed)
                    best = current
                    if (readyToPaint(current, pluginFinished, expectVideo, received == pages.size)) {
                        logOpen(
                            "facebook ready ${elapsed(started)}ms media=${current.imageUrls.size} " +
                                "video=${current.videoUrl != null}",
                        )
                        listener.onProgress(LoadProgress(1f, LoadStage.ExtractingContent))
                        return@coroutineScope current
                    }
                    if (!emitted) {
                        coroutineContext[FacebookPreviewElement]?.emit(current)
                        emitted = true
                        logOpen("facebook preview ${elapsed(started)}ms")
                    }
                }
            } finally {
                jobs.forEach { it.cancel() }
            }
            best?.let { chosen ->
                logOpen("facebook ready ${elapsed(started)}ms merged")
                listener.onProgress(LoadProgress(1f, LoadStage.ExtractingContent))
                return@coroutineScope chosen
            }
            if (post.kind == FacebookUrls.Kind.Story) throw StoryUnavailableException()
            throw IOException(lastError.get())
        }
    }

    private data class FetchedPage(val url: String, val post: ParsedFacebookPost?)

    private fun readyToPaint(
        post: ParsedFacebookPost,
        pluginFinished: Boolean,
        expectVideo: Boolean,
        hostsFinished: Boolean,
    ): Boolean {
        if (hostsFinished) return true
        if (FacebookDocument.isTruncatedPreview(post.text)) return false
        if (!FacebookDocument.hasDistinctAuthor(post)) return false
        if (!post.videoUrl.isNullOrBlank()) return true
        if (expectVideo || post.videoHint) return false
        return post.imageUrls.isNotEmpty() || pluginFinished
    }

    private fun elapsed(started: Long): Long = (System.nanoTime() - started) / 1_000_000L

    private fun logOpen(message: String) {
        runCatching { Log.i("PaneOpen", message) }
    }

    private fun longerCaption(current: ParsedFacebookPost?, next: ParsedFacebookPost): ParsedFacebookPost {
        if (current == null) return next
        val longer = if (next.text.length > current.text.length) next else current
        val other = if (longer === next) current else next
        val videos = (longer.videos + other.videos).distinctBy { it.url }
        val video = videos.maxWithOrNull(
            compareBy<FacebookPlayable> { it.height ?: 0 }.thenBy { it.bitrate ?: 0 },
        )?.url ?: longer.videoUrl ?: other.videoUrl
        return longer.copy(
            imageUrls = longer.imageUrls.ifEmpty { other.imageUrls },
            videoUrl = video,
            videos = videos,
            videoHint = longer.videoHint || other.videoHint,
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
        if (post.kind == FacebookUrls.Kind.Story) {
            return (listOf(post.sourceUrl) + mobileCopies(post.sourceUrl)).distinct()
        }
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

    private fun resolveShareShort(opened: FacebookUrls.Post): FacebookUrls.Post {
        if (opened.kind != FacebookUrls.Kind.ShareShort) return opened
        val landed = followShare(opened.sourceUrl)
        val next = FacebookUrls.parse(landed)
        if (next == null || next.kind == FacebookUrls.Kind.ShareShort) {
            throw IllegalArgumentException("Unsupported Facebook post URL: ${opened.sourceUrl}")
        }
        return next
    }

    /** Follows a short `/share/<id>/` hop until it is a post, reel, or story URL. */
    private fun followShare(start: String): String {
        var current = start
        repeat(4) {
            val known = FacebookUrls.parse(current)
            if (known != null && known.kind != FacebookUrls.Kind.ShareShort) return current
            val connection = connectionFactory(current)
            try {
                connection.instanceFollowRedirects = false
                connection.requestMethod = "GET"
                connection.connectTimeout = 8_000
                connection.readTimeout = 12_000
                connection.setRequestProperty("Accept", "text/html,application/xhtml+xml")
                connection.setRequestProperty("User-Agent", USER_AGENT)
                val status = connection.responseCode
                val location = connection.getHeaderField("Location")
                val html = if (status in 200..299) readLimited(connection) else ""
                val next = FacebookShareRedirect.nextUrl(status, location, html, current)
                if (next.isNullOrBlank() || next == current) return current
                current = next
            } finally {
                connection.disconnect()
            }
        }
        return current
    }

    private fun readLimited(connection: HttpURLConnection): String =
        (connection.inputStream ?: connection.errorStream)
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

    private suspend fun get(url: String): String = withContext(Dispatchers.IO) {
        val connection = connectionFactory(url)
        try {
            connection.instanceFollowRedirects = true
            connection.requestMethod = "GET"
            connection.connectTimeout = 8_000
            connection.readTimeout = 12_000
            connection.setRequestProperty("Accept", "text/html,application/xhtml+xml")
            connection.setRequestProperty("Accept-Language", "en-US,en;q=0.9")
            connection.setRequestProperty(
                "User-Agent",
                if (url.contains("mbasic.facebook.com")) MOBILE_USER_AGENT else USER_AGENT,
            )
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
        const val MOBILE_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"
    }
}
