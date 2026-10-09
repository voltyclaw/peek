package app.pane.android.data.youtube

import app.pane.android.domain.model.YouTubeCommentsState
import app.pane.android.domain.youtube.YouTubeComment
import app.pane.android.domain.youtube.YouTubeCommentPage
import app.pane.android.domain.youtube.YouTubeVideo
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

internal object YouTubeJson {
    private val json = Json { ignoreUnknownKeys = true }

    fun video(videoId: String, body: String): YouTubeVideo {
        val root = parse(body) ?: return YouTubeVideo(videoId, commentsUnavailable = true)
        if (reason(root) == "quotaExceeded") return YouTubeVideo(videoId, commentsUnavailable = true)
        val item = root.array("items").firstOrNull() ?: return YouTubeVideo(videoId)
        val snippet = item.obj("snippet")
        val status = item.obj("status")
        val rating = item.obj("contentDetails")?.obj("contentRating")?.text("ytRating")
        val thumb = snippet?.obj("thumbnails")
        return YouTubeVideo(
            videoId = videoId,
            title = snippet?.text("title").orEmpty(),
            description = snippet?.text("description").orEmpty(),
            channelId = snippet?.text("channelId").orEmpty(),
            channelName = snippet?.text("channelTitle").orEmpty(),
            thumbnailUrl = thumb?.obj("high")?.text("url")
                ?: thumb?.obj("medium")?.text("url")
                ?: thumb?.obj("default")?.text("url")
                ?: "",
            viewCount = item.obj("statistics")?.text("viewCount").orEmpty(),
            publishedLabel = snippet?.text("publishedAt").orEmpty().substringBefore('T'),
            embeddable = status?.bool("embeddable") ?: true,
            ageRestricted = rating == "ytAgeRestricted",
        )
    }

    fun channel(video: YouTubeVideo, body: String): YouTubeVideo {
        val item = parse(body)?.array("items")?.firstOrNull() ?: return video
        val snippet = item.obj("snippet") ?: return video
        val custom = snippet.text("customUrl").orEmpty()
        val handle = custom.removePrefix("@")
        val thumbs = snippet.obj("thumbnails")
        val avatar = thumbs?.obj("medium")?.text("url")
            ?: thumbs?.obj("default")?.text("url")
            ?: thumbs?.obj("high")?.text("url")
            ?: ""
        val channelUrl = when {
            custom.startsWith("@") -> "https://www.youtube.com/$custom"
            custom.isNotBlank() -> "https://www.youtube.com/@$custom"
            video.channelId.isNotBlank() -> "https://www.youtube.com/channel/${video.channelId}"
            else -> ""
        }
        return video.copy(
            channelName = snippet.text("title").orEmpty().ifBlank { video.channelName },
            handle = handle,
            channelUrl = channelUrl,
            avatarUrl = avatar,
        )
    }

    fun oembed(videoId: String, body: String): YouTubeVideo {
        val root = parse(body) ?: return YouTubeVideo(videoId, commentsUnavailable = true)
        val authorUrl = root.text("author_url").orEmpty()
        return YouTubeVideo(
            videoId = videoId,
            title = root.text("title").orEmpty(),
            channelName = root.text("author_name").orEmpty(),
            handle = authorUrl.substringAfterLast('/').removePrefix("@"),
            channelUrl = authorUrl,
            thumbnailUrl = root.text("thumbnail_url").orEmpty(),
            commentsUnavailable = true,
        )
    }

    fun comments(body: String, pagesLoaded: Int): YouTubeCommentPage {
        val root = parse(body)
        val reason = root?.let { reason(it) }
        if (reason == "commentsDisabled") {
            return YouTubeCommentPage(emptyList(), null, false, pagesLoaded, YouTubeCommentsState.Off)
        }
        if (reason == "quotaExceeded") {
            return YouTubeCommentPage(emptyList(), null, true, pagesLoaded, YouTubeCommentsState.Failed)
        }
        if (root == null) {
            return YouTubeCommentPage(emptyList(), null, false, pagesLoaded, YouTubeCommentsState.Failed)
        }
        val comments = root.array("items").mapIndexedNotNull { index, item ->
            val snippet = item.obj("snippet")?.obj("topLevelComment")?.obj("snippet") ?: return@mapIndexedNotNull null
            val id = item.text("id").orEmpty().ifBlank { "yt-$pagesLoaded-$index" }
            YouTubeComment(
                id = id,
                author = snippet.text("authorDisplayName").orEmpty(),
                body = stripHtml(snippet.text("textDisplay").orEmpty()),
                avatarUrl = snippet.text("authorProfileImageUrl").orEmpty(),
                publishedAt = snippet.text("publishedAt").orEmpty(),
            )
        }
        return YouTubeCommentPage(
            comments = comments,
            nextPageToken = root.text("nextPageToken")?.takeIf { it.isNotBlank() },
            hardWall = false,
            pagesLoaded = pagesLoaded + 1,
            state = YouTubeCommentsState.Ready,
        )
    }

    private fun parse(body: String): JsonObject? =
        runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull()

    private fun reason(root: JsonObject): String? =
        root.obj("error")?.array("errors")?.firstOrNull()?.text("reason")?.takeIf { it.isNotBlank() }

    private fun JsonObject.obj(name: String): JsonObject? =
        (this[name] as? kotlinx.serialization.json.JsonObject)

    private fun JsonObject.array(name: String): List<JsonObject> =
        runCatching { this[name]?.jsonArray?.map { it.jsonObject } }.getOrNull().orEmpty()

    private fun JsonObject.text(name: String): String? =
        runCatching { this[name]?.jsonPrimitive?.contentOrNull }.getOrNull()

    private fun JsonObject.bool(name: String): Boolean? =
        text(name)?.toBooleanStrictOrNull()

    private fun stripHtml(value: String): String =
        value.replace(Regex("""<br\s*/?>""", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("""</p>""", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("""<[^>]+>"""), "")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&#39;", "'")
            .replace("&quot;", "\"")
            .trim()
}
