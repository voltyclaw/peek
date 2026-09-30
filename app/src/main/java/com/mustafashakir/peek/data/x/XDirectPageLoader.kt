package com.mustafashakir.peek.data.x

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
                listener.onProgress(LoadProgress(1f, LoadStage.ExtractingContent))
                return post
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

    private suspend fun get(url: String, json: Boolean): String = withContext(Dispatchers.IO) {
        val connection = connectionFactory(url)
        try {
            connection.instanceFollowRedirects = true
            connection.requestMethod = "GET"
            connection.connectTimeout = 8_000
            connection.readTimeout = 12_000
            connection.setRequestProperty(
                "Accept",
                if (json) "application/json,text/plain,*/*" else "text/html",
            )
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
            if (status !in 200..299 || body.isBlank()) {
                throw IOException("X returned HTTP $status")
            }
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
