package app.pane.android.data.x

import java.util.ArrayDeque
import kotlin.math.abs
import kotlin.math.floor
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

data class ParsedXReply(
    val id: String,
    val author: String,
    val text: String,
    val createdAtEpochMillis: Long,
)

/** One post in the author's own chain, in reading order from the root. */
data class ParsedXThreadPost(
    val id: String,
    val author: String,
    val screenName: String,
    val text: String,
)

/**
 * Author posts embedded around the opened status.
 * [partial] is true when the first post still replies to an earlier post by the same account
 * that this page did not include.
 */
data class ParsedAuthorThread(
    val posts: List<ParsedXThreadPost> = emptyList(),
    val partial: Boolean = false,
)

data class ParsedXVideo(
    val url: String,
    val bitrate: Int?,
)

data class ParsedXPost(
    val id: String,
    val canonicalUrl: String,
    val author: String,
    val text: String,
    val imageUrls: List<String>,
    val videoUrl: String?,
    val commentCount: Int,
    val replies: List<ParsedXReply> = emptyList(),
    val screenName: String? = null,
    val authorThread: List<ParsedXThreadPost> = emptyList(),
    val authorThreadPartial: Boolean = false,
    val videos: List<ParsedXVideo> = emptyList(),
)

/**
 * Public syndication JSON from X's embed endpoint, plus a short oEmbed HTML fallback.
 * The token matches the embed page's JavaScript, including its base-36 rounding.
 */
object XSyndication {
    const val UNAVAILABLE = "X didn't return this public post. It may be private or blocked."

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun token(statusId: String): String {
        val value = statusId.toDouble() / 1e15 * kotlin.math.PI
        return jsNumberToString(value, 36).filterNot { it == '0' || it == '.' }
    }

    fun syndicationUrl(statusId: String): String =
        "https://cdn.syndication.twimg.com/tweet-result?id=$statusId&token=${token(statusId)}&lang=en"

    fun parseJson(body: String, id: String, canonicalUrl: String): ParsedXPost? {
        val root = runCatching { json.parseToJsonElement(body) }.getOrNull() as? JsonObject ?: return null
        val type = root.string("__typename")
        if (type == "TweetTombstone" || root.containsKey("tombstone")) return null
        val preview = root.string("text")
        val note = root.obj("note_tweet")?.string("text")
        val text = when {
            preview != null -> XConversation.longerCaption(preview, note)
            else -> note
        } ?: return null
        if (text.isBlank()) return null
        val user = root.obj("user")
        val author = user?.string("name") ?: user?.string("screen_name") ?: "X"
        val screenName = user?.string("screen_name")
        val images = mutableListOf<String>()
        (root["photos"] as? JsonArray)?.forEach { photo ->
            (photo as? JsonObject)?.string("url")?.takeIf { it.startsWith("http") }?.let(images::add)
        }
        val video = root.obj("video")
        video?.string("poster")?.takeIf { it.startsWith("http") }?.let { poster ->
            if (images.none { it == poster }) images += poster
        }
        val videos = mp4Variants(video?.get("variants") as? JsonArray)
        val videoUrl = videos.maxByOrNull { it.bitrate ?: 0 }?.url
        val comments = root.int("conversation_count") ?: root.int("reply_count") ?: 0
        return ParsedXPost(
            id = root.string("id_str") ?: id,
            canonicalUrl = canonicalUrl,
            author = author,
            text = text,
            imageUrls = images.distinct(),
            videoUrl = videoUrl,
            commentCount = comments,
            screenName = screenName,
            videos = videos,
        )
    }

    fun parseOEmbed(body: String, id: String, canonicalUrl: String): ParsedXPost? {
        val root = runCatching { json.parseToJsonElement(body) }.getOrNull() as? JsonObject ?: return null
        val author = root.string("author_name") ?: return null
        val html = root.string("html") ?: return null
        val text = html.replace(Regex("<[^>]+>"), " ")
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace(Regex("\\s+"), " ")
            .substringBefore("—")
            .trim()
            .ifBlank { return null }
        return ParsedXPost(
            id = id,
            canonicalUrl = canonicalUrl,
            author = author,
            text = text,
            imageUrls = emptyList(),
            videoUrl = null,
            commentCount = 0,
        )
    }

    private fun mp4Variants(variants: JsonArray?): List<ParsedXVideo> {
        if (variants == null) return emptyList()
        return variants.mapNotNull { it as? JsonObject }
            .filter { item ->
                val type = item.string("type") ?: item.string("content_type")
                type == "video/mp4"
            }
            .mapNotNull { item ->
                val url = (item.string("src") ?: item.string("url"))?.takeIf { it.startsWith("http") }
                    ?: return@mapNotNull null
                ParsedXVideo(url = url, bitrate = item.int("bitrate"))
            }
            .distinctBy { it.url }
    }

    private fun JsonObject.string(key: String): String? =
        (this[key] as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

    private fun JsonObject.int(key: String): Int? =
        (this[key]?.jsonPrimitive)?.intOrNull ?: (this[key]?.jsonPrimitive)?.longOrNull?.toInt()

    private fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject
}

/**
 * JavaScript Number#toString for a non-decimal radix, including the fractional rounding
 * the X embed token depends on.
 */
internal fun jsNumberToString(value: Double, radix: Int): String {
    if (value.isNaN()) return "NaN"
    if (value == 0.0) return "0"
    if (value.isInfinite()) return if (value < 0) "-Infinity" else "Infinity"
    val alphabet = "0123456789abcdefghijklmnopqrstuvwxyz"
    val sign = value < 0
    val magnitude = abs(value)
    var fraction = magnitude - floor(magnitude)
    var integer = floor(magnitude)
    var delta = maxOf(Double.MIN_VALUE, ulp(magnitude) / 2.0)
    val result = ArrayList<Int>()
    if (fraction >= delta) result.add(-2)
    while (fraction >= delta) {
        delta *= radix
        val scaled = fraction * radix
        val digit = floor(scaled)
        fraction = scaled - digit
        result.add(digit.toInt())
        val needsRounding = fraction > 0.5 || (fraction == 0.5 && digit.toInt() and 1 == 1)
        if (needsRounding && fraction + delta > 1.0) {
            var index = result.lastIndex
            var carried = false
            while (index >= 1) {
                val current = result[index]
                if (current < 0) {
                    index -= 1
                    continue
                }
                if (current + 1 < radix) {
                    result[index] = current + 1
                    carried = true
                    break
                }
                result.removeAt(index)
                index = result.lastIndex
            }
            if (!carried) integer += 1.0
            break
        }
    }
    var whole = integer.toLong()
    val integerDigits = ArrayDeque<Int>()
    integerDigits.addFirst((whole % radix).toInt())
    whole /= radix
    while (whole > 0) {
        integerDigits.addFirst((whole % radix).toInt())
        whole /= radix
    }
    val chars = StringBuilder()
    if (sign) chars.append('-')
    integerDigits.forEach { chars.append(alphabet[it]) }
    result.forEach { digit ->
        if (digit == -2) chars.append('.') else chars.append(alphabet[digit])
    }
    return chars.toString()
}

private fun ulp(value: Double): Double {
    val absValue = abs(value)
    if (absValue == 0.0 || absValue.isNaN() || absValue.isInfinite()) return absValue
    val bits = java.lang.Double.doubleToRawLongBits(absValue)
    return java.lang.Double.longBitsToDouble(bits + 1) - absValue
}
