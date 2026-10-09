package app.pane.android.data.instagram

import app.pane.android.data.resolver.PageLoadProgressElement
import app.pane.android.domain.model.LoadProgress
import app.pane.android.domain.model.StoryUnavailableException
import app.pane.android.domain.model.LoadStage
import app.pane.android.domain.repository.LoadProgressListener
import android.util.Log
import java.io.IOException
import java.math.BigInteger
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import java.util.Base64
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Fired when a public embed page has a caption before the GraphQL post arrives. */
class InstagramPreviewElement(val emit: (ParsedInstagramMedia) -> Unit) :
    AbstractCoroutineContextElement(InstagramPreviewElement) {
    companion object Key : CoroutineContext.Key<InstagramPreviewElement>
}

/**
 * Fetches the same logged-out post payload Kittygram uses. The response includes the post's
 * complete media collection and Instagram's initial comments connection.
 */
class InstagramDirectPageLoader(
    private val parser: InstagramHtmlParser = InstagramHtmlParser(),
    private val connectionFactory: (String) -> HttpURLConnection = { url ->
        URI(url).toURL().openConnection() as HttpURLConnection
    },
) : InstagramPageLoader {
    private val json = Json { ignoreUnknownKeys = true }
    private val secureRandom = SecureRandom()

    override val resolverId: String = "instagram-graphql"

    override fun supports(url: String): Boolean =
        extractShortcode(url) != null || InstagramStories.parse(url) != null

    override suspend fun resolve(url: String): ParsedInstagramMedia = withContext(Dispatchers.IO) {
        val story = InstagramStories.parse(url)
        if (story != null) return@withContext resolveStory(url, story)
        val shortcode = extractShortcode(url)
            ?: throw IllegalArgumentException("Unsupported Instagram post URL: $url")
        val listener = coroutineContext[PageLoadProgressElement]?.listener ?: LoadProgressListener {}
        listener.report(0.05f, LoadStage.Connecting)
        val started = System.nanoTime()
        coroutineScope {
            val graphql = async {
                val variables = buildJsonObject {
                    put("media_id", shortcodeToMediaId(shortcode).toString())
                }
                request(url) { lsd -> mediaQueryBody(variables, lsd) }
            }
            val preview = async { runCatching { fetchEmbed(shortcode) }.getOrNull() }
            select {
                preview.onAwait { media ->
                    if (media != null && !graphql.isCompleted) {
                        coroutineContext[InstagramPreviewElement]?.emit(media)
                        logOpen("instagram preview ${elapsed(started)}ms code=$shortcode")
                    }
                }
                graphql.onAwait { }
            }
            val response = graphql.await()
            preview.cancel()
            listener.report(0.85f, LoadStage.ExtractingContent)
            val media = parser.parse(response)
                ?: throw IOException(graphQlErrorMessage(response) ?: "Instagram response contained no public post")
            logOpen("instagram ready ${elapsed(started)}ms code=$shortcode")
            listener.report(1f, LoadStage.ExtractingContent)
            media
        }
    }

    private suspend fun resolveStory(url: String, story: InstagramStories.Story): ParsedInstagramMedia {
        val listener = coroutineContext[PageLoadProgressElement]?.listener ?: LoadProgressListener {}
        listener.report(0.08f, LoadStage.Connecting)
        val html = runCatching { getPublic(story.fetchUrl.ifBlank { url }) }.getOrNull()
        if (html != null) {
            parser.parse(html)?.let { return it }
            parseStoryPage(html, story)?.let { return it }
        }
        if (story.mediaId.all(Char::isDigit)) {
            val response = runCatching {
                val variables = buildJsonObject { put("media_id", story.mediaId) }
                request(story.fetchUrl) { lsd -> mediaQueryBody(variables, lsd) }
            }.getOrNull()
            if (response != null) {
                parser.parse(response)?.let { return it }
            }
        }
        throw StoryUnavailableException()
    }

    private fun getPublic(url: String): String {
        val connection = connectionFactory(url)
        try {
            connection.instanceFollowRedirects = true
            connection.requestMethod = "GET"
            connection.connectTimeout = 8_000
            connection.readTimeout = 12_000
            connection.setRequestProperty("Accept", "text/html")
            connection.setRequestProperty("User-Agent", USER_AGENT)
            val status = connection.responseCode
            if (status !in 200..299) throw IOException("Instagram returned HTTP $status")
            return connection.inputStream?.bufferedReader(StandardCharsets.UTF_8)?.use { reader ->
                val buffer = CharArray(8_192)
                val text = StringBuilder()
                while (text.length < MAX_EMBED_CHARS) {
                    val count = reader.read(buffer)
                    if (count < 0) break
                    text.append(buffer, 0, minOf(count, MAX_EMBED_CHARS - text.length))
                }
                text.toString()
            }.orEmpty()
        } finally {
            connection.disconnect()
        }
    }

    private fun parseStoryPage(html: String, story: InstagramStories.Story): ParsedInstagramMedia? {
        val image = metaContent(html, "og:image")?.takeIf { it.startsWith("http") && !isStaticAsset(it) }
        val video = (metaContent(html, "og:video:secure_url") ?: metaContent(html, "og:video"))
            ?.takeIf { it.startsWith("http") }
        if (image == null && video == null) return null
        val login = html.contains("login", ignoreCase = true) && image == null && video == null
        if (login) return null
        val username = story.username.takeUnless {
            it.equals("highlights", ignoreCase = true) || it.equals("instagram", ignoreCase = true)
        } ?: "unknown"
        val caption = metaContent(html, "og:description")?.takeUnless { it.equals("Instagram", ignoreCase = true) }
        return ParsedInstagramMedia(
            pk = story.mediaId,
            code = story.mediaId,
            videoVersions = video?.let { listOf(VideoVersion(url = it)) }.orEmpty(),
            imageVersions = image?.let { ImageVersions(listOf(ImageCandidate(it))) },
            caption = caption?.let { Caption(it) },
            likeCount = 0,
            commentCount = 0,
            takenAt = 0L,
            owner = Owner(username = username),
            comments = emptyList(),
        )
    }

    private fun isStaticAsset(url: String): Boolean {
        val lower = url.lowercase()
        return "static.cdninstagram.com" in lower || lower.endsWith("/favicon.ico") || "rsrc.php" in lower
    }

    private fun fetchEmbed(shortcode: String): ParsedInstagramMedia? {
        val embedUrl = "https://www.instagram.com/p/$shortcode/embed/captioned/"
        val connection = connectionFactory(embedUrl)
        try {
            connection.instanceFollowRedirects = true
            connection.requestMethod = "GET"
            connection.connectTimeout = 8_000
            connection.readTimeout = 8_000
            connection.setRequestProperty("Accept", "text/html")
            connection.setRequestProperty("User-Agent", USER_AGENT)
            val status = connection.responseCode
            if (status !in 200..299) return null
            val html = connection.inputStream?.bufferedReader(StandardCharsets.UTF_8)?.use { reader ->
                val buffer = CharArray(8_192)
                val text = StringBuilder()
                while (text.length < MAX_EMBED_CHARS) {
                    val count = reader.read(buffer)
                    if (count < 0) break
                    text.append(buffer, 0, minOf(count, MAX_EMBED_CHARS - text.length))
                }
                text.toString()
            }.orEmpty()
            return parseEmbed(html, shortcode)
        } catch (_: Exception) {
            return null
        } finally {
            connection.disconnect()
        }
    }

    private fun parseEmbed(html: String, shortcode: String): ParsedInstagramMedia? {
        val caption = metaContent(html, "og:description")
            ?: metaContent(html, "twitter:description")
            ?: return null
        if (caption.equals("Instagram", ignoreCase = true)) return null
        val image = metaContent(html, "og:image") ?: metaContent(html, "twitter:image") ?: return null
        if (!image.startsWith("http")) return null
        val title = metaContent(html, "og:title").orEmpty()
        val username = title.substringBefore(" on Instagram").trim().ifBlank { "instagram" }
        return ParsedInstagramMedia(
            pk = shortcode,
            code = shortcode,
            videoVersions = emptyList(),
            imageVersions = ImageVersions(listOf(ImageCandidate(url = image))),
            caption = Caption(caption),
            likeCount = 0,
            commentCount = 0,
            takenAt = 0L,
            owner = Owner(username = username),
            comments = emptyList(),
        )
    }

    private fun metaContent(html: String, property: String): String? {
        val escaped = Regex.escape(property)
        val patterns = listOf(
            Regex("""<meta[^>]+(?:property|name)=["']$escaped["'][^>]+content=["']([^"']*)["']""", RegexOption.IGNORE_CASE),
            Regex("""<meta[^>]+content=["']([^"']*)["'][^>]+(?:property|name)=["']$escaped["']""", RegexOption.IGNORE_CASE),
        )
        val raw = patterns.firstNotNullOfOrNull { it.find(html)?.groupValues?.get(1) } ?: return null
        return raw.replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .trim()
            .ifBlank { null }
    }

    private fun elapsed(started: Long): Long = (System.nanoTime() - started) / 1_000_000L

    private fun logOpen(message: String) {
        runCatching { Log.i("PaneOpen", message) }
    }

    override suspend fun loadComments(postId: String, cursor: String): ParsedInstagramCommentsPage {
        require(postId.isNotBlank()) { "Instagram post id is required" }
        require(cursor.isNotBlank()) { "Instagram comment cursor is required" }
        val variables = buildJsonObject {
            put("after", cursor)
            put("first", COMMENTS_PAGE_SIZE)
            put("media_id", postId)
        }
        val response = request("https://www.instagram.com/p/$postId/") { lsd ->
            mediaQueryBody(variables, lsd, COMMENTS_PAGINATION_DOC_ID)
        }
        return parser.parseCommentsPage(response)
            ?: throw IOException(graphQlErrorMessage(response) ?: "Instagram response contained no comments page")
    }

    private suspend fun request(sourceUrl: String, body: (String) -> String): String {
        val lsd = randomLsd()
        return withContext(Dispatchers.IO) { executeRequest(body(lsd), lsd, sourceUrl) }
    }

    private fun mediaQueryBody(
        variables: kotlinx.serialization.json.JsonObject,
        lsd: String,
        docId: String = MEDIA_ID_POST_DOC_ID,
    ): String {
        return formBody(
            "av" to "0",
            "__d" to "www",
            "__user" to "0",
            "__a" to "1",
            "__comet_req" to "7",
            "lsd" to lsd,
            "server_timestamps" to "true",
            "variables" to json.encodeToString(variables),
            "doc_id" to docId,
        )
    }

    private fun executeRequest(body: String, lsd: String, sourceUrl: String): String {
        val connection = connectionFactory(GRAPHQL_ENDPOINT)
        try {
            connection.instanceFollowRedirects = false
            connection.requestMethod = "POST"
            connection.connectTimeout = CONNECT_TIMEOUT_MILLIS
            connection.readTimeout = READ_TIMEOUT_MILLIS
            connection.doOutput = true
            connection.setRequestProperty("Accept", "*/*")
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            connection.setRequestProperty("User-Agent", USER_AGENT)
            connection.setRequestProperty("Origin", "https://www.instagram.com")
            connection.setRequestProperty("Referer", sourceUrl)
            connection.setRequestProperty("Sec-CH-Prefers-Color-Scheme", "dark")
            connection.setRequestProperty(
                "Sec-CH-UA",
                "\"Chromium\";v=\"135\", \"Not)A;Brand\";v=\"24\"",
            )
            connection.setRequestProperty("Sec-CH-UA-Mobile", "?0")
            connection.setRequestProperty("Sec-CH-UA-Model", "\"\"")
            connection.setRequestProperty("Sec-CH-UA-Platform-Version", "\"10.0.19045\"")
            connection.setRequestProperty("Sec-Fetch-Dest", "empty")
            connection.setRequestProperty("Sec-Fetch-Mode", "cors")
            connection.setRequestProperty("Sec-Fetch-Site", "same-origin")
            connection.setRequestProperty("X-IG-App-ID", INSTAGRAM_WEB_APP_ID)
            connection.setRequestProperty("X-CSRFToken", "exampletexthere")
            connection.setRequestProperty("X-IG-Max-Touch-Points", "0")
            connection.setRequestProperty("X-FB-LSD", lsd)
            connection.outputStream.use { it.write(body.toByteArray(StandardCharsets.UTF_8)) }

            val status = connection.responseCode
            val response = (if (status in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader(StandardCharsets.UTF_8)
                ?.use { it.readText() }
                .orEmpty()

            if (status == HttpURLConnection.HTTP_MOVED_TEMP || status == 429) {
                throw IOException("Instagram temporarily rate-limited the request (HTTP $status)")
            }
            if (status !in 200..299) {
                throw IOException("Instagram returned HTTP $status")
            }
            if (response.isBlank()) throw IOException("Instagram returned an empty response")
            return response
        } finally {
            connection.disconnect()
        }
    }

    private fun extractShortcode(url: String): String? {
        val uri = runCatching { URI(url) }.getOrNull() ?: return null
        return POST_PATH.matchEntire(uri.path.orEmpty().trimEnd('/'))?.groupValues?.get(2)
    }

    private fun shortcodeToMediaId(shortcode: String): BigInteger =
        shortcode.fold(BigInteger.ZERO) { value, character ->
            val digit = SHORTCODE_ALPHABET.indexOf(character)
            require(digit >= 0) { "Invalid Instagram shortcode" }
            value * BASE_64 + digit.toBigInteger()
        }

    private fun randomLsd(): String {
        val bytes = ByteArray(12)
        secureRandom.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    private fun formBody(vararg entries: Pair<String, String>): String =
        entries.joinToString("&") { (key, value) ->
            "${encode(key)}=${encode(value)}"
        }

    private fun encode(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.name())

    private fun graphQlErrorMessage(response: String): String? {
        val root = runCatching { json.parseToJsonElement(response) }.getOrNull() ?: return null
        val text = root.toString()
        return when {
            "\"require_login\":true" in text -> "Instagram requires a login for this post"
            "\"errors\"" in text -> "Instagram rejected the post query"
            else -> null
        }
    }

    private fun LoadProgressListener.report(fraction: Float, stage: LoadStage) {
        onProgress(LoadProgress(fraction, stage))
    }

    private companion object {
        const val GRAPHQL_ENDPOINT = "https://www.instagram.com/api/graphql"
        const val MEDIA_ID_POST_DOC_ID = "27130156389949648"
        const val COMMENTS_PAGINATION_DOC_ID = "27261273046856309"
        const val COMMENTS_PAGE_SIZE = 10
        const val INSTAGRAM_WEB_APP_ID = "936619743392459"
        const val USER_AGENT =
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) " +
                "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36"
        const val CONNECT_TIMEOUT_MILLIS = 15_000
        const val READ_TIMEOUT_MILLIS = 30_000
        const val MAX_EMBED_CHARS = 400_000
        const val SHORTCODE_ALPHABET =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"
        val BASE_64: BigInteger = BigInteger.valueOf(64)
        val POST_PATH = Regex("/(p|reel|reels)/([A-Za-z0-9_-]+)")
    }
}
