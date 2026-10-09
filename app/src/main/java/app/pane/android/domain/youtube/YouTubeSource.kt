package app.pane.android.domain.youtube

import java.net.URI
import java.net.URLEncoder
import java.util.Locale

/** One YouTube URL, classified without a network call. */
enum class YouTubeLinkKind {
    Watch,
    Short,
    Live,
    Embed,
    Channel,
    Playlist,
    Other,
}

data class YouTubeLink(
    val kind: YouTubeLinkKind,
    val videoId: String?,
    val canonicalUrl: String?,
    val startSeconds: Int,
    val channelUrl: String?,
    val music: Boolean,
)

/**
 * A Short is one finite video in the normal viewer: rel=0, no next-Short feed, and the end card.
 * There is no block-card path.
 */
/** What the viewer may put in the player slot. The IFrame HTML is absent until playback is allowed. */
enum class YouTubeSlot { Poster, Player, EmbedOff, AgeRestricted }

object YouTubePlayback {
    fun slot(
        kind: YouTubeLinkKind,
        consented: Boolean,
        embeddable: Boolean = true,
        ageRestricted: Boolean = false,
    ): YouTubeSlot {
        if (!consented) return YouTubeSlot.Poster
        if (ageRestricted) return YouTubeSlot.AgeRestricted
        if (!embeddable) return YouTubeSlot.EmbedOff
        return YouTubeSlot.Player
    }

    /** Null unless the slot is the official player. Callers must not load an IFrame before this. */
    fun iframeHtml(
        videoId: String,
        startSeconds: Int,
        kind: YouTubeLinkKind,
        consented: Boolean,
        embeddable: Boolean = true,
        ageRestricted: Boolean = false,
    ): String? {
        if (slot(kind, consented, embeddable, ageRestricted) != YouTubeSlot.Player) return null
        return YouTubePlayer.iframeHtml(videoId, startSeconds)
    }
}

object YouTubeComments {
    const val ORDER = "relevance"
    const val PAGE_SIZE = 20
    const val MAX_PAGES = 10

    sealed interface Query {
        data class Page(val url: String) : Query
        data object Unavailable : Query
        data object HardWall : Query
    }

    fun query(videoId: String, pageToken: String?, pagesLoaded: Int, apiKey: String): Query {
        if (apiKey.isBlank()) return Query.Unavailable
        if (pagesLoaded >= MAX_PAGES) return Query.HardWall
        val token = pageToken?.takeIf { it.isNotBlank() }?.let { value ->
            "&pageToken=${URLEncoder.encode(value, Charsets.UTF_8.name())}"
        }.orEmpty()
        val url = "https://www.googleapis.com/youtube/v3/commentThreads" +
            "?part=snippet&videoId=$videoId&order=$ORDER&maxResults=$PAGE_SIZE&textFormat=html" +
            "&key=$apiKey$token"
        return Query.Page(url)
    }
}

object YouTubeLinks {
    private val ID = Regex("""^[A-Za-z0-9_-]{11}$""")

    fun parse(raw: String): YouTubeLink? {
        val uri = runCatching { URI(raw.trim()) }.getOrNull() ?: return null
        val host = uri.host?.lowercase(Locale.US)?.removePrefix("www.") ?: return null
        val music = host == "music.youtube.com"
        val youtube = host == "youtube.com" || host == "m.youtube.com" || music || host == "youtube-nocookie.com"
        val short = host == "youtu.be" || host == "m.youtu.be"
        if (!youtube && !short) return null
        val segments = uri.path.orEmpty().trim('/').split('/').filter { it.isNotEmpty() }
        val start = startSeconds(query(uri, "t") ?: fragmentValue(uri.fragment, "t"))
        if (short) {
            val id = segments.firstOrNull()?.takeIf { ID.matches(it) }
            return link(if (id == null) YouTubeLinkKind.Other else YouTubeLinkKind.Watch, id, start, null, false)
        }
        val head = segments.firstOrNull()
        if (head != null && head.startsWith("@") && head.length > 1) {
            return link(YouTubeLinkKind.Channel, null, 0, "https://www.youtube.com/$head", music)
        }
        return when (head) {
            "watch" -> {
                val id = query(uri, "v")?.takeIf { ID.matches(it) }
                link(if (id == null) YouTubeLinkKind.Other else YouTubeLinkKind.Watch, id, start, null, music)
            }
            "shorts" -> {
                val id = segments.getOrNull(1)?.takeIf { ID.matches(it) }
                link(if (id == null) YouTubeLinkKind.Other else YouTubeLinkKind.Short, id, start, null, music)
            }
            "live" -> {
                val id = segments.getOrNull(1)?.takeIf { ID.matches(it) }
                link(if (id == null) YouTubeLinkKind.Other else YouTubeLinkKind.Live, id, start, null, music)
            }
            "embed", "v" -> {
                val id = segments.getOrNull(1)?.takeIf { ID.matches(it) }
                val kind = if (id == null) YouTubeLinkKind.Other else if (head == "embed") YouTubeLinkKind.Embed else YouTubeLinkKind.Watch
                link(kind, id, start, null, music)
            }
            "playlist" -> {
                val id = query(uri, "v")?.takeIf { ID.matches(it) }
                if (id != null) link(YouTubeLinkKind.Watch, id, start, null, music)
                else link(YouTubeLinkKind.Playlist, null, 0, null, music)
            }
            "channel" -> channel(segments.getOrNull(1)?.let { "https://www.youtube.com/channel/$it" }, music)
            "c" -> channel(segments.getOrNull(1)?.let { "https://www.youtube.com/c/$it" }, music)
            "user" -> channel(segments.getOrNull(1)?.let { "https://www.youtube.com/user/$it" }, music)
            else -> link(YouTubeLinkKind.Other, null, 0, null, music)
        }
    }

    fun canonical(url: String): String? = parse(url)?.canonicalUrl

    fun startSeconds(raw: String?): Int {
        if (raw.isNullOrBlank()) return 0
        val value = raw.trim().lowercase(Locale.US)
        value.toIntOrNull()?.let { return it.coerceAtLeast(0) }
        val match = TIME.matchEntire(value) ?: return 0
        if (match.groupValues.drop(1).all { it.isEmpty() }) return 0
        val hours = match.groupValues[1].toIntOrNull() ?: 0
        val minutes = match.groupValues[2].toIntOrNull() ?: 0
        val seconds = match.groupValues[3].toIntOrNull() ?: 0
        return (hours * 3600 + minutes * 60 + seconds).coerceAtLeast(0)
    }

    fun videosUrl(videoId: String, apiKey: String): String =
        "https://www.googleapis.com/youtube/v3/videos?part=snippet,statistics,status&id=$videoId&key=$apiKey"

    fun channelsUrl(channelId: String, apiKey: String): String =
        "https://www.googleapis.com/youtube/v3/channels?part=snippet&id=$channelId&key=$apiKey"

    fun oembedUrl(videoId: String): String {
        val watch = URLEncoder.encode("https://www.youtube.com/watch?v=$videoId", Charsets.UTF_8.name())
        return "https://www.youtube.com/oembed?url=$watch&format=json"
    }

    private fun channel(url: String?, music: Boolean): YouTubeLink =
        if (url == null) link(YouTubeLinkKind.Other, null, 0, null, music)
        else link(YouTubeLinkKind.Channel, null, 0, url, music)

    private fun link(
        kind: YouTubeLinkKind,
        videoId: String?,
        startSeconds: Int,
        channelUrl: String?,
        music: Boolean,
    ): YouTubeLink = YouTubeLink(
        kind = kind,
        videoId = videoId,
        canonicalUrl = videoId?.let { "https://www.youtube.com/watch?v=$it" },
        startSeconds = startSeconds,
        channelUrl = channelUrl,
        music = music,
    )

    private fun query(uri: URI, name: String): String? =
        uri.rawQuery?.split('&')?.firstNotNullOfOrNull { part ->
            val key = part.substringBefore('=')
            val value = part.substringAfter('=', "")
            if (key == name && value.isNotEmpty()) decode(value) else null
        }

    private fun fragmentValue(fragment: String?, name: String): String? {
        if (fragment.isNullOrBlank()) return null
        return fragment.removePrefix("#").split('&').firstNotNullOfOrNull { part ->
            val key = part.substringBefore('=')
            val value = part.substringAfter('=', "")
            if (key == name && value.isNotEmpty()) decode(value) else null
        }
    }

    private fun decode(value: String): String =
        runCatching { java.net.URLDecoder.decode(value, Charsets.UTF_8.name()) }.getOrDefault(value)

    private val TIME = Regex("""(?:(\d+)h)?(?:(\d+)m)?(?:(\d+)s)?""")
}

/** Non-authorised YouTube copy is kept for 30 days, then the API fields are cleared. */
object YouTubeCopyExpiry {
    const val WINDOW_MILLIS: Long = 30L * 24L * 60L * 60L * 1000L

    fun stale(lastViewedAtEpochMillis: Long, nowEpochMillis: Long): Boolean =
        lastViewedAtEpochMillis > 0L && nowEpochMillis - lastViewedAtEpochMillis >= WINDOW_MILLIS
}
