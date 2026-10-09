package app.pane.android.domain.tiktok

import java.math.BigInteger
import java.net.URI
import java.net.URLEncoder
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.TextStyle
import java.util.Locale

enum class TikTokLinkKind { Video, Photo, Short, Profile, Live, Other }

data class TikTokLink(
    val kind: TikTokLinkKind,
    val videoId: String?,
    val handle: String?,
    val canonicalUrl: String?,
    val code: String?,
)

/** One TikTok URL, classified without a network call. */
object TikTokLinks {
    private val ID = Regex("""^[0-9]{6,22}$""")
    private val HANDLE = Regex("""^[A-Za-z0-9._]{1,24}$""")
    private val SHORT = Regex("""^[A-Za-z0-9]{1,32}$""")
    private val HOSTS = setOf("tiktok.com", "m.tiktok.com", "vm.tiktok.com", "vt.tiktok.com", "live.tiktok.com")

    fun parse(raw: String): TikTokLink? {
        val uri = runCatching { URI(raw.trim()) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase(Locale.US) ?: return null
        if (scheme != "https" && scheme != "http") return null
        val host = uri.host?.lowercase(Locale.US)?.removePrefix("www.") ?: return null
        if (host !in HOSTS) return null
        val segments = uri.path.orEmpty().trim('/').split('/').filter { it.isNotEmpty() }
        if (host == "vm.tiktok.com" || host == "vt.tiktok.com") {
            val code = segments.firstOrNull()?.substringBefore('.')?.takeIf { SHORT.matches(it) }
            return short(code)
        }
        if (host == "live.tiktok.com") return TikTokLink(TikTokLinkKind.Live, null, null, raw.trim(), null)
        val head = segments.firstOrNull() ?: return TikTokLink(TikTokLinkKind.Other, null, null, null, null)
        if (head == "t") return short(segments.getOrNull(1)?.takeIf { SHORT.matches(it) })
        if (head == "v") {
            val id = segments.getOrNull(1)?.removeSuffix(".html")?.takeIf { ID.matches(it) }
            return post(TikTokLinkKind.Video, null, id)
        }
        if (head == "embed" && segments.getOrNull(1) == "v2") {
            val id = segments.getOrNull(2)?.takeIf { ID.matches(it) }
            return post(TikTokLinkKind.Video, null, id)
        }
        if (head.startsWith("@")) {
            val handle = head.removePrefix("@").takeIf { HANDLE.matches(it) }
            val type = segments.getOrNull(1)?.lowercase(Locale.US)
            val id = segments.getOrNull(2)?.takeIf { ID.matches(it) }
            return when (type) {
                "video" -> post(TikTokLinkKind.Video, handle, id)
                "photo" -> post(TikTokLinkKind.Photo, handle, id)
                "live" -> TikTokLink(
                    TikTokLinkKind.Live,
                    null,
                    handle,
                    handle?.let { "https://www.tiktok.com/@$it/live" },
                    null,
                )
                null -> if (handle == null || segments.size != 1) {
                    TikTokLink(TikTokLinkKind.Other, null, handle, null, null)
                } else {
                    TikTokLink(TikTokLinkKind.Profile, null, handle, "https://www.tiktok.com/@$handle", null)
                }
                else -> TikTokLink(TikTokLinkKind.Other, null, handle, null, null)
            }
        }
        return TikTokLink(TikTokLinkKind.Other, null, null, null, null)
    }

    fun isHost(raw: String): Boolean = parse(raw) != null

    fun supports(raw: String): Boolean {
        val kind = parse(raw)?.kind ?: return false
        return kind == TikTokLinkKind.Video || kind == TikTokLinkKind.Photo ||
            kind == TikTokLinkKind.Short || kind == TikTokLinkKind.Live
    }

    fun oembedUrl(pageUrl: String): String =
        "https://www.tiktok.com/oembed?url=${URLEncoder.encode(pageUrl, Charsets.UTF_8.name())}"

    private fun short(code: String?): TikTokLink =
        TikTokLink(if (code == null) TikTokLinkKind.Other else TikTokLinkKind.Short, null, null, null, code)

    private fun post(kind: TikTokLinkKind, handle: String?, id: String?): TikTokLink {
        if (id == null) return TikTokLink(TikTokLinkKind.Other, null, handle, null, null)
        val type = if (kind == TikTokLinkKind.Photo) "photo" else "video"
        val canonical = if (handle != null) {
            "https://www.tiktok.com/@$handle/$type/$id"
        } else {
            "https://www.tiktok.com/embed/v2/$id"
        }
        return TikTokLink(kind, id, handle, canonical, null)
    }
}

object TikTokIds {
    /** 2016-09-01T00:00:00Z. Earlier values are not TikTok snowflakes. */
    const val EARLIEST_EPOCH_SECONDS = 1_472_688_000L

    fun postedAtEpochSeconds(id: String, nowEpochSeconds: Long): Long? {
        val value = id.toBigIntegerOrNull() ?: return null
        if (value.signum() < 0) return null
        val seconds = value.shiftRight(32)
        if (seconds.bitLength() > 62) return null
        val epoch = seconds.longValueExact()
        if (epoch < EARLIEST_EPOCH_SECONDS || epoch > nowEpochSeconds) return null
        return epoch
    }

    /** "3 days ago" inside a week, then an absolute date. */
    fun label(epochSeconds: Long, nowEpochMillis: Long): String {
        val posted = Instant.ofEpochSecond(epochSeconds).atZone(ZoneOffset.UTC).toLocalDate()
        val today = Instant.ofEpochMilli(nowEpochMillis).atZone(ZoneOffset.UTC).toLocalDate()
        val days = java.time.temporal.ChronoUnit.DAYS.between(posted, today)
        return when {
            days <= 0L -> "Today"
            days == 1L -> "1 day ago"
            days < 7L -> "$days days ago"
            else -> {
                val month = posted.month.getDisplayName(TextStyle.SHORT, Locale.US)
                "$month ${posted.dayOfMonth}, ${posted.year}"
            }
        }
    }
}

data class TikTokCaptionPiece(val text: String, val handle: String?)

object TikTokCaption {
    private val MENTION = Regex("""@[A-Za-z0-9._]{2,24}""")

    fun pieces(caption: String): List<TikTokCaptionPiece> {
        if (caption.isEmpty()) return emptyList()
        val out = mutableListOf<TikTokCaptionPiece>()
        var cursor = 0
        MENTION.findAll(caption).forEach { match ->
            if (match.range.first > cursor) out += TikTokCaptionPiece(caption.substring(cursor, match.range.first), null)
            out += TikTokCaptionPiece(match.value, match.value.removePrefix("@"))
            cursor = match.range.last + 1
        }
        if (cursor < caption.length) out += TikTokCaptionPiece(caption.substring(cursor), null)
        return out
    }

    fun profileUrl(handle: String): String = "https://www.tiktok.com/@${handle.removePrefix("@")}"

    fun handleFromAuthorUrl(authorUrl: String): String {
        val path = runCatching { URI(authorUrl).path }.getOrNull().orEmpty().trim('/')
        return path.removePrefix("@").substringBefore('/').takeIf { it.isNotBlank() }.orEmpty()
    }
}

object TikTokPlayer {
    fun url(id: String): String =
        "https://www.tiktok.com/player/v1/$id?rel=0&loop=0&autoplay=0&description=0&music_info=0" +
            "&native_context_menu=0&controls=1&progress_bar=1&play_button=1&volume_control=1" +
            "&fullscreen_button=1&timestamp=1&closed_caption=1"

    /**
     * Bundled page so postMessage reaches the official player.
     * autoplay stays 0. Mute and play are messages, never muted=1.
     * Ended seeks to 0 and pauses. Navigation away from the player is left to the WebView client.
     */
    fun iframeHtml(id: String): String {
        val src = url(id)
        return """
            <!DOCTYPE html>
            <html>
            <head>
              <meta name="viewport" content="width=device-width, initial-scale=1">
              <style>html,body{margin:0;height:100%;background:#0E0B0A}iframe{border:0;width:100%;height:100%}</style>
            </head>
            <body>
              <iframe id="tt" src="$src" allow="fullscreen" allowfullscreen></iframe>
              <script>
                function send(type, value) {
                  var frame = document.getElementById('tt');
                  if (!frame || !frame.contentWindow) return;
                  var message = {type: type, "x-tiktok-player": true};
                  if (value !== undefined) message.value = value;
                  frame.contentWindow.postMessage(message, '*');
                }
                window.addEventListener('message', function(event) {
                  var data = event.data || {};
                  if (data.type === 'onStateChange' && data.value === 0) {
                    send('seekTo', 0);
                    send('pause');
                    if (window.Pane) Pane.onEnded();
                  }
                  if (data.type === 'onPlayerError' && window.Pane) Pane.onError(Number(data.value));
                });
              </script>
            </body>
            </html>
        """.trimIndent()
    }
}

object TikTokPlayback {
    const val VISIBLE_FRACTION = 0.5f

    fun shouldPlay(visibleFraction: Float): Boolean = visibleFraction >= VISIBLE_FRACTION

    /** Null until consent. The WebView must not be created before this. */
    fun iframeHtml(videoId: String, consented: Boolean): String? {
        if (!consented || videoId.isBlank()) return null
        return TikTokPlayer.iframeHtml(videoId)
    }
}

object TikTokBackoff {
    val RETRY_DELAYS_MILLIS = longArrayOf(1_000L, 2_000L, 4_000L)
    const val MAX_RETRIES = 3
    const val MIN_INTERVAL_MILLIS = 1_000L
    const val CACHE_TTL_MILLIS = 24L * 60L * 60L * 1000L
}

/**
 * TikTok oEmbed fields are a display cache. The 30-day strip is on.
 * A successful refresh stamps [fetchedAt]. Gone posts strip immediately. 429 and 5xx do not.
 */
object TikTokCopyRetention {
    const val ENFORCED = true
    const val CAPTION_LIMIT = 2_000
    const val WINDOW_MILLIS: Long = 30L * 24L * 60L * 60L * 1000L

    fun caption(raw: String): String = raw.take(CAPTION_LIMIT)

    fun stale(fetchedAtEpochMillis: Long, nowEpochMillis: Long): Boolean =
        ENFORCED && fetchedAtEpochMillis > 0L && nowEpochMillis - fetchedAtEpochMillis >= WINDOW_MILLIS

    fun stripOnHttpStatus(status: Int): Boolean = status == 400 || status == 404 || status == 410

    fun keepOnHttpStatus(status: Int): Boolean = status == 429 || status == 503 || status >= 500 || status <= 0
}

object TikTokThumbs {
    fun expiresEpochSeconds(thumbnailUrl: String?): Long? {
        if (thumbnailUrl.isNullOrBlank()) return null
        val query = thumbnailUrl.substringAfter('?', "")
        val raw = query.split('&').firstNotNullOfOrNull { part ->
            val key = part.substringBefore('=')
            val value = part.substringAfter('=', "")
            if (key == "x-expires" && value.isNotEmpty()) value else null
        } ?: return null
        return raw.toLongOrNull()
    }

    /** Missing thumbnail, or a signed URL whose x-expires has passed. */
    fun expired(thumbnailUrl: String?, nowEpochSeconds: Long): Boolean {
        if (thumbnailUrl.isNullOrBlank()) return true
        val expires = expiresEpochSeconds(thumbnailUrl) ?: return false
        return nowEpochSeconds >= expires
    }
}

data class TikTokVisibleRow(
    val id: String,
    val pageUrl: String,
    val thumbnailUrl: String?,
)

object TikTokScrollRefresh {
    /** One visible row whose thumbnail is missing or past x-expires. */
    fun next(visible: List<TikTokVisibleRow>, nowEpochSeconds: Long): TikTokVisibleRow? =
        visible.firstOrNull { row -> row.id.isNotBlank() && TikTokThumbs.expired(row.thumbnailUrl, nowEpochSeconds) }
}

private fun String.toBigIntegerOrNull(): BigInteger? = runCatching { BigInteger(this) }.getOrNull()
