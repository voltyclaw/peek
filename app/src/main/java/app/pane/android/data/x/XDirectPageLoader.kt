package app.pane.android.data.x

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
 * Logged-out fetch of one public status. Syndication JSON is tried first, then oEmbed.
 * No X login or token from the user is used.
 */
class XDirectPageLoader(
    private val connectionFactory: (String) -> HttpURLConnection = { url ->
        URI(url).toURL().openConnection() as HttpURLConnection
    },
) : XPageLoader {
    override val resolverId: String = "x-syndication"

    override fun supports(url: String): Boolean = XUrls.supports(url)

    override suspend fun resolve(url: String): ParsedXPost {
        val status = XUrls.parse(url)
            ?: throw IllegalArgumentException("Unsupported X status URL: $url")
        val listener = coroutineContext[PageLoadProgressElement]?.listener ?: LoadProgressListener {}
        listener.onProgress(LoadProgress(0.08f, LoadStage.Connecting))
        val syndication = runCatching { get(XSyndication.syndicationUrl(status.id), json = true) }.getOrNull()
        if (syndication != null) {
            XSyndication.parseJson(syndication, status.id, status.canonicalUrl)?.let { post ->
                listener.onProgress(LoadProgress(0.72f, LoadStage.ExtractingContent))
                val withReplies = post.copy(replies = loadReplies(status.id))
                listener.onProgress(LoadProgress(1f, LoadStage.ExtractingContent))
                return withReplies
            }
        }
        listener.onProgress(LoadProgress(0.55f, LoadStage.FetchingPage))
        val encoded = URLEncoder.encode(status.canonicalUrl, StandardCharsets.UTF_8.name())
        val oembed = runCatching {
            get("https://publish.twitter.com/oembed?url=$encoded&omit_script=true", json = true)
        }.getOrNull()
        if (oembed != null) {
            XSyndication.parseOEmbed(oembed, status.id, status.canonicalUrl)?.let { post ->
                listener.onProgress(LoadProgress(1f, LoadStage.ExtractingContent))
                return post
            }
        }
        throw IOException(XSyndication.UNAVAILABLE)
    }

    private suspend fun loadReplies(statusId: String): List<ParsedXReply> {
        val attempts = listOf(
            "https://x.com/i/status/$statusId" to USER_AGENT,
            "https://twitter.com/i/status/$statusId" to USER_AGENT,
            "https://mobile.twitter.com/i/status/$statusId" to MOBILE_USER_AGENT,
        )
        for ((url, agent) in attempts) {
            val html = runCatching { get(url, json = false, userAgent = agent) }.getOrNull() ?: continue
            val replies = XConversation.parseReplies(html, statusId)
            if (replies.isNotEmpty()) return replies
        }
        return emptyList()
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
    }
}
