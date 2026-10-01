package app.pane.android.data.x

import android.util.Log
import app.pane.android.data.resolver.PageLoadProgressElement
import app.pane.android.domain.model.LoadProgress
import app.pane.android.domain.model.LoadStage
import app.pane.android.domain.repository.LoadProgressListener
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

/**
 * Fired with the syndication post as soon as that small JSON is parsed, before the
 * slower status HTML (author thread and replies) finishes.
 */
class XPreviewElement(val emit: (ParsedXPost) -> Unit) :
    AbstractCoroutineContextElement(XPreviewElement) {
    companion object Key : CoroutineContext.Key<XPreviewElement>
}

/**
 * Logged-out fetch of one public status. Syndication JSON is the fast caption and media.
 * Status HTML for the author thread and replies is fetched in parallel and does not
 * block the first preview. No X login or token from the user is used.
 */
class XDirectPageLoader(
    private val connectionFactory: (String) -> HttpURLConnection = { url ->
        URI(url).toURL().openConnection() as HttpURLConnection
    },
) : XPageLoader {
    override val resolverId: String = "x-syndication"

    override fun supports(url: String): Boolean = XUrls.supports(url)

    override suspend fun resolve(url: String): ParsedXPost = withContext(Dispatchers.IO) {
        val status = XUrls.parse(url)
            ?: throw IllegalArgumentException("Unsupported X status URL: $url")
        val listener = coroutineContext[PageLoadProgressElement]?.listener ?: LoadProgressListener {}
        val started = System.nanoTime()
        listener.onProgress(LoadProgress(0.08f, LoadStage.Connecting))
        coroutineScope {
            val slices = mutableListOf<Deferred<StatusSlice?>>()
            fun track(pageUrl: String, agent: String) {
                slices += async { fetchSlice(pageUrl, agent, status.id) }
            }
            val knownHandle = handleFrom(url)
            statusPages(status.id, knownHandle).forEach { (pageUrl, agent) -> track(pageUrl, agent) }
            val syndication = runCatching { get(XSyndication.syndicationUrl(status.id), json = true) }.getOrNull()
            val parsed = syndication?.let { XSyndication.parseJson(it, status.id, status.canonicalUrl) }
            if (parsed != null) {
                val learned = parsed.screenName?.trim()?.takeIf { HANDLE.matches(it) }
                if (knownHandle == null && learned != null) {
                    track("https://x.com/$learned/status/${status.id}", USER_AGENT)
                }
                if (slices.any { !it.isCompleted }) {
                    coroutineContext[XPreviewElement]?.emit(parsed)
                    log("x preview ${elapsed(started)}ms id=${status.id}")
                }
                listener.onProgress(LoadProgress(0.72f, LoadStage.ExtractingContent))
                val page = mergeSlices(slices.awaitAll().filterNotNull())
                val text = XConversation.longerCaption(parsed.text, page.note)
                log(
                    "x ready ${elapsed(started)}ms thread=${page.authorThread.size} " +
                        "replies=${page.replies.size} id=${status.id}",
                )
                listener.onProgress(LoadProgress(1f, LoadStage.ExtractingContent))
                parsed.copy(
                    text = text,
                    replies = page.replies,
                    authorThread = page.authorThread.map { item ->
                        if (item.id == parsed.id) item.copy(text = richest(item.text, text)) else item
                    },
                    authorThreadPartial = page.authorThreadPartial,
                )
            } else {
                slices.forEach { it.cancel() }
                listener.onProgress(LoadProgress(0.55f, LoadStage.FetchingPage))
                val encoded = URLEncoder.encode(status.canonicalUrl, StandardCharsets.UTF_8.name())
                val oembed = runCatching {
                    get("https://publish.twitter.com/oembed?url=$encoded&omit_script=true", json = true)
                }.getOrNull()
                val post = oembed?.let { XSyndication.parseOEmbed(it, status.id, status.canonicalUrl) }
                if (post != null) {
                    listener.onProgress(LoadProgress(1f, LoadStage.ExtractingContent))
                    post
                } else {
                    throw IOException(XSyndication.UNAVAILABLE)
                }
            }
        }
    }

    private data class StatusSlice(
        val note: String?,
        val thread: ParsedAuthorThread,
        val replies: List<ParsedXReply>,
    )

    private data class StatusPage(
        val note: String?,
        val replies: List<ParsedXReply>,
        val authorThread: List<ParsedXThreadPost>,
        val authorThreadPartial: Boolean,
    )

    private fun statusPages(statusId: String, screenName: String?): List<Pair<String, String>> {
        val handle = screenName?.trim()?.takeIf { HANDLE.matches(it) }
        return buildList {
            add("https://x.com/i/status/$statusId" to USER_AGENT)
            if (handle != null) add("https://x.com/$handle/status/$statusId" to USER_AGENT)
            add("https://twitter.com/i/status/$statusId" to USER_AGENT)
            add("https://mobile.twitter.com/i/status/$statusId" to MOBILE_USER_AGENT)
        }
    }

    private fun handleFrom(url: String): String? {
        val segments = runCatching { URI(url).path }.getOrNull()
            .orEmpty()
            .trim('/')
            .split('/')
            .filter { it.isNotEmpty() }
        val index = segments.indexOf("status")
        if (index <= 0) return null
        val handle = segments[index - 1]
        if (handle.equals("i", ignoreCase = true)) return null
        return handle.takeIf { HANDLE.matches(it) }
    }

    private suspend fun fetchSlice(url: String, agent: String, statusId: String): StatusSlice? {
        val html = runCatching { get(url, json = false, userAgent = agent) }.getOrNull() ?: return null
        return StatusSlice(
            note = XConversation.parseNoteText(html),
            thread = XConversation.parseAuthorThread(html, statusId),
            replies = XConversation.parseReplies(html, statusId),
        )
    }

    /** Merge in request order so a fast host does not reshuffle replies ahead of an earlier one. */
    private fun mergeSlices(slices: List<StatusSlice>): StatusPage {
        var note: String? = null
        var thread = emptyList<ParsedXThreadPost>()
        var threadPartial = false
        val replies = LinkedHashMap<String, ParsedXReply>()
        for (slice in slices) {
            val foundNote = slice.note
            if (foundNote != null && foundNote.length > (note?.length ?: 0)) note = foundNote
            if (prefers(slice.thread, thread, threadPartial)) {
                thread = slice.thread.posts
                threadPartial = slice.thread.partial
            }
            for (reply in slice.replies) {
                val existing = replies[reply.id]
                if (existing == null || reply.text.length > existing.text.length) {
                    replies[reply.id] = reply
                }
            }
        }
        val threadIds = thread.map { it.id }.toSet()
        return StatusPage(
            note = note,
            replies = replies.values.filter { it.id !in threadIds },
            authorThread = thread,
            authorThreadPartial = threadPartial && thread.size >= 2,
        )
    }

    private fun prefers(found: ParsedAuthorThread, current: List<ParsedXThreadPost>, currentPartial: Boolean): Boolean {
        if (found.posts.size > current.size) return true
        return found.posts.size == current.size && found.posts.isNotEmpty() && currentPartial && !found.partial
    }

    /** Keep the longer body when one public copy continues the other. */
    private fun richest(current: String, candidate: String): String {
        val forward = XConversation.longerCaption(current, candidate)
        val backward = XConversation.longerCaption(candidate, current)
        return if (backward.length > forward.length) backward else forward
    }

    private suspend fun get(url: String, json: Boolean, userAgent: String = USER_AGENT): String = withContext(Dispatchers.IO) {
        val connection = connectionFactory(url)
        try {
            connection.instanceFollowRedirects = true
            connection.requestMethod = "GET"
            connection.connectTimeout = 8_000
            connection.readTimeout = 20_000
            connection.setRequestProperty(
                "Accept",
                if (json) {
                    "application/json,text/plain,*/*"
                } else {
                    "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8"
                },
            )
            if (!json) {
                connection.setRequestProperty("Sec-Fetch-Dest", "document")
                connection.setRequestProperty("Sec-Fetch-Mode", "navigate")
                connection.setRequestProperty("Sec-Fetch-Site", "none")
                connection.setRequestProperty("Upgrade-Insecure-Requests", "1")
            }
            connection.setRequestProperty("Accept-Language", "en-US,en;q=0.9")
            connection.setRequestProperty("User-Agent", userAgent)
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
            if (status !in 200..299 || body.isBlank()) {
                throw IOException("X returned HTTP $status")
            }
            body
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val MAX_CHARS = 2_000_000
        const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
        const val MOBILE_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"
        val HANDLE = Regex("[A-Za-z0-9_]{1,15}")

        fun elapsed(startNanos: Long): Long = (System.nanoTime() - startNanos) / 1_000_000L

        fun log(message: String) {
            runCatching { Log.i("PaneOpen", message) }
        }
    }
}
