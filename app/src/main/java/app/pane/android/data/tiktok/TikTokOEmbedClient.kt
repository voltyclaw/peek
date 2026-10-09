package app.pane.android.data.tiktok

import app.pane.android.domain.tiktok.TikTokBackoff
import app.pane.android.domain.tiktok.TikTokCopyRetention
import app.pane.android.domain.tiktok.TikTokLinkKind
import app.pane.android.domain.tiktok.TikTokLinks
import app.pane.android.domain.tiktok.TikTokOEmbed
import app.pane.android.domain.tiktok.TikTokOEmbedApi
import app.pane.android.domain.tiktok.TikTokOEmbedResult
import app.pane.android.domain.tiktok.TikTokRedirects
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

data class TikTokHttpResponse(
    val code: Int,
    val body: String,
    val location: String?,
)

fun interface TikTokTransport {
    fun exchange(url: String, method: String, followRedirects: Boolean): TikTokHttpResponse
}

internal object HttpTikTokTransport : TikTokTransport {
    override fun exchange(url: String, method: String, followRedirects: Boolean): TikTokHttpResponse {
        val connection = java.net.URL(url).openConnection() as HttpURLConnection
        connection.instanceFollowRedirects = followRedirects
        connection.requestMethod = method
        connection.connectTimeout = 5_000
        connection.readTimeout = 5_000
        connection.setRequestProperty("Accept", "application/json")
        connection.setRequestProperty("User-Agent", "Pane/1.0.45 (public post viewer)")
        return try {
            val code = connection.responseCode
            val location = connection.getHeaderField("Location")
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            TikTokHttpResponse(code, body, location)
        } catch (_: Exception) {
            TikTokHttpResponse(0, "", null)
        } finally {
            connection.disconnect()
        }
    }
}

interface TikTokOEmbedDisk {
    fun read(videoId: String): Stored?
    fun write(videoId: String, stored: Stored)
    fun remove(videoId: String)

    data class Stored(val atEpochMillis: Long, val json: String)
}

class MemoryTikTokOEmbedDisk : TikTokOEmbedDisk {
    private val rows = mutableMapOf<String, TikTokOEmbedDisk.Stored>()
    override fun read(videoId: String): TikTokOEmbedDisk.Stored? = rows[videoId]
    override fun write(videoId: String, stored: TikTokOEmbedDisk.Stored) {
        rows[videoId] = stored
    }
    override fun remove(videoId: String) {
        rows.remove(videoId)
    }
}

class FileTikTokOEmbedDisk(private val file: File) : TikTokOEmbedDisk {
    override fun read(videoId: String): TikTokOEmbedDisk.Stored? = load()[videoId]?.let { row ->
        val at = row["at"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: return null
        val json = row["body"]?.jsonPrimitive?.contentOrNull ?: return null
        TikTokOEmbedDisk.Stored(at, json)
    }

    override fun write(videoId: String, stored: TikTokOEmbedDisk.Stored) {
        val root = load().toMutableMap()
        root[videoId] = buildJsonObject {
            put("at", stored.atEpochMillis)
            put("body", stored.json)
        }
        file.parentFile?.mkdirs()
        file.writeText(Json.encodeToString(JsonObject.serializer(), JsonObject(root)))
    }

    override fun remove(videoId: String) {
        val root = load().toMutableMap()
        root.remove(videoId)
        file.parentFile?.mkdirs()
        file.writeText(Json.encodeToString(JsonObject.serializer(), JsonObject(root)))
    }

    private fun load(): Map<String, JsonObject> {
        if (!file.isFile) return emptyMap()
        val root = runCatching { Json.parseToJsonElement(file.readText()).jsonObject }.getOrNull() ?: return emptyMap()
        return root.mapValues { it.value.jsonObject }
    }
}

class TikTokOEmbedClient(
    private val transport: TikTokTransport,
    private val clock: () -> Long = System::currentTimeMillis,
    private val sleep: (Long) -> Unit = { Thread.sleep(it) },
    private val jitterMillis: () -> Long = { 0L },
    private val disk: TikTokOEmbedDisk = MemoryTikTokOEmbedDisk(),
) : TikTokOEmbedApi {
    private val memory = mutableMapOf<String, TikTokOEmbedDisk.Stored>()
    private var lastRequestAt = 0L

    override fun fetch(videoId: String, pageUrl: String): TikTokOEmbedResult {
        cached(videoId)?.let { return TikTokOEmbedResult.Ready(it) }
        val url = TikTokLinks.oembedUrl(pageUrl)
        var retries = 0
        while (true) {
            pace()
            val response = runCatching { transport.exchange(url, "GET", followRedirects = true) }
                .getOrElse { TikTokHttpResponse(0, "", null) }
            if (response.code in 200..299) {
                val parsed = TikTokJson.oembed(response.body) ?: return TikTokOEmbedResult.Failed
                remember(videoId, response.body)
                return TikTokOEmbedResult.Ready(parsed)
            }
            if (TikTokCopyRetention.stripOnHttpStatus(response.code)) return TikTokOEmbedResult.Removed
            val retry = response.code == 429 || response.code == 503
            if (!retry || retries >= TikTokBackoff.MAX_RETRIES) return TikTokOEmbedResult.Failed
            sleep(TikTokBackoff.RETRY_DELAYS_MILLIS[retries] + jitterMillis().coerceAtLeast(0L))
            retries += 1
        }
    }

    override fun invalidate(videoId: String) {
        memory.remove(videoId)
        disk.remove(videoId)
    }

    private fun cached(videoId: String): TikTokOEmbed? {
        val now = clock()
        val stored = memory[videoId] ?: disk.read(videoId)?.also { memory[videoId] = it } ?: return null
        if (now - stored.atEpochMillis >= TikTokBackoff.CACHE_TTL_MILLIS) return null
        return TikTokJson.oembed(stored.json)
    }

    private fun remember(videoId: String, json: String) {
        val stored = TikTokOEmbedDisk.Stored(clock(), json)
        memory[videoId] = stored
        disk.write(videoId, stored)
    }

    private fun pace() {
        val now = clock()
        if (lastRequestAt > 0L) {
            val wait = TikTokBackoff.MIN_INTERVAL_MILLIS - (now - lastRequestAt)
            if (wait > 0L) sleep(wait)
        }
        lastRequestAt = clock()
    }
}

class TikTokRedirectResolver(
    private val transport: TikTokTransport,
) : TikTokRedirects {
    override fun resolve(url: String): String? {
        val parsed = TikTokLinks.parse(url) ?: return null
        if (parsed.kind != TikTokLinkKind.Short) return parsed.canonicalUrl
        var current = url
        repeat(5) {
            var response = transport.exchange(current, "HEAD", followRedirects = false)
            if (response.code == 405 || response.code == 501 || response.code == 403 || response.code == 0) {
                response = transport.exchange(current, "GET", followRedirects = false)
            }
            val next = response.location?.let { absolutize(current, it) }
            if (response.code in 300..399 && !next.isNullOrBlank()) {
                current = next
                val landed = TikTokLinks.parse(current)
                if (landed != null && landed.kind != TikTokLinkKind.Short && landed.videoId != null) {
                    return landed.canonicalUrl
                }
                return@repeat
            }
            return TikTokLinks.parse(current)?.canonicalUrl
        }
        return TikTokLinks.parse(current)?.takeIf { it.videoId != null }?.canonicalUrl
    }

    private fun absolutize(base: String, location: String): String {
        if (location.startsWith("http://") || location.startsWith("https://")) return location
        return runCatching { URI(base).resolve(location).toString() }.getOrDefault(location)
    }
}

internal object TikTokJson {
    private val json = Json { ignoreUnknownKeys = true }

    fun oembed(body: String): TikTokOEmbed? {
        val root = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull() ?: return null
        return TikTokOEmbed(
            caption = root.string("title"),
            authorName = root.string("author_name"),
            authorUrl = root.string("author_url"),
            thumbnailUrl = root.string("thumbnail_url"),
        )
    }

    private fun JsonObject.string(name: String): String =
        this[name]?.jsonPrimitive?.contentOrNull.orEmpty()
}
