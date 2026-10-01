package app.pane.android.data.reddit

import java.net.URI
import java.util.Locale
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

data class ParsedRedditMedia(
    val id: String,
    val imageUrl: String,
    val videoUrl: String?,
    val width: Int?,
    val height: Int?,
    val durationSeconds: Int?,
    val contentDescription: String,
)

data class ParsedRedditComment(
    val id: String,
    val author: String,
    val body: String,
    val createdUtcEpochSeconds: Long,
    val isSubmitter: Boolean,
    val replies: List<ParsedRedditComment>,
)

data class ParsedRedditPost(
    val id: String,
    val title: String,
    val selfText: String,
    val author: String,
    val subreddit: String,
    val score: Int,
    val commentCount: Int,
    val createdUtcEpochSeconds: Long,
    val permalink: String,
    val over18: Boolean,
    val spoiler: Boolean,
    val isSelf: Boolean,
    val media: List<ParsedRedditMedia>,
    val comments: List<ParsedRedditComment>,
    /** Gallery pages keep loading slides after the first HTML snapshot. */
    val mediaPending: Boolean = false,
    /** Top-level comment ids Reddit left behind a `more` child. */
    val moreCommentIds: List<String> = emptyList(),
)

/** One public `morechildren` page: new comments, plus any further ids. */
data class MoreComments(
    val comments: List<ParsedRedditComment>,
    val moreIds: List<String>,
)

/** Parses the public listing document Reddit returns for `.../comments/{id}.json`. */
class RedditJsonParser {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    fun parse(document: String): ParsedRedditPost? {
        val root = runCatching { json.parseToJsonElement(document) }.getOrNull() ?: return null
        val listings = when (root) {
            is JsonArray -> root.mapNotNull { it as? JsonObject }
            is JsonObject -> listOf(root)
            else -> return null
        }
        val post = listings.firstOrNull()
            ?.obj("data")
            ?.array("children")
            ?.firstOrNull { it.obj()?.string("kind") == "t3" }
            ?.obj()
            ?.obj("data")
            ?: return null
        val title = post.string("title")?.trim().orEmpty()
        if (title.isBlank() || title == "[deleted]" || title == "[removed]") return null
        if (post.bool("quarantine")) return null
        when (post.string("subreddit_type")) {
            "private", "gold_only", "employees_only" -> return null
        }
        val id = post.string("id")?.takeIf { it.isNotBlank() }
            ?: post.string("name")?.removePrefix("t3_")?.takeIf { it.isNotBlank() }
            ?: return null
        val description = title.take(200)
        val ownMedia = extractMedia(post, id, description)
        val parent = post.array("crosspost_parent_list")?.firstOrNull()?.obj()
        val parentMedia = parent?.let { extractMedia(it, it.string("id") ?: id, description) }.orEmpty()
        val media = when {
            ownMedia.any { it.videoUrl != null } -> ownMedia
            parentMedia.any { it.videoUrl != null } -> parentMedia
            parentMedia.size > ownMedia.size -> parentMedia
            ownMedia.isNotEmpty() -> ownMedia
            else -> parentMedia
        }
        val (comments, moreIds) = parseCommentListing(
            listings.getOrNull(1)?.obj("data")?.array("children").orEmpty(),
        )
        return ParsedRedditPost(
            id = id.lowercase(Locale.US),
            title = title,
            selfText = displaySelfText(post.string("selftext")),
            author = post.string("author")?.takeIf { it.isNotBlank() } ?: "unknown",
            subreddit = post.string("subreddit")?.takeIf { it.isNotBlank() } ?: "reddit",
            score = post.int("score"),
            commentCount = post.int("num_comments"),
            createdUtcEpochSeconds = post.epoch("created_utc"),
            permalink = post.string("permalink").orEmpty(),
            over18 = post.bool("over_18"),
            spoiler = post.bool("spoiler"),
            isSelf = post.bool("is_self"),
            media = media,
            comments = comments,
            moreCommentIds = moreIds,
        )
    }

    /** Flat `morechildren` things. Nested `more` nodes (parent `t1_`) are left for a later pass. */
    fun parseMoreChildren(document: String): MoreComments {
        val root = runCatching { json.parseToJsonElement(document) }.getOrNull()?.obj()
            ?: return MoreComments(emptyList(), emptyList())
        val things = root.obj("json")?.obj("data")?.array("things").orEmpty()
        val flat = mutableListOf<Pair<String, ParsedRedditComment>>()
        val moreIds = mutableListOf<String>()
        for (element in things) {
            val node = element.obj() ?: continue
            when (node.string("kind")) {
                "t1" -> {
                    val data = node.obj("data") ?: continue
                    val comment = parseComment(element, depth = 0) ?: continue
                    flat += data.string("parent_id").orEmpty() to comment
                }
                "more" -> {
                    val data = node.obj("data") ?: continue
                    if (data.string("parent_id").orEmpty().startsWith("t1_")) continue
                    moreIds += moreChildIds(data)
                }
            }
        }
        return MoreComments(threadComments(flat), moreIds.distinct())
    }

    private fun parseCommentListing(children: List<JsonElement>): Pair<List<ParsedRedditComment>, List<String>> {
        val comments = mutableListOf<ParsedRedditComment>()
        val moreIds = mutableListOf<String>()
        for (element in children) {
            val node = element.obj() ?: continue
            when (node.string("kind")) {
                "t1" -> parseComment(element, depth = 0)?.let { comments += it }
                "more" -> {
                    val data = node.obj("data") ?: continue
                    if (data.string("parent_id").orEmpty().startsWith("t1_")) continue
                    moreIds += moreChildIds(data)
                }
            }
        }
        return comments to moreIds.distinct()
    }

    private fun threadComments(flat: List<Pair<String, ParsedRedditComment>>): List<ParsedRedditComment> {
        if (flat.isEmpty()) return emptyList()
        val byParent = flat.groupBy { (parent, _) ->
            if (parent.startsWith("t1_")) parent.removePrefix("t1_") else parent
        }
        fun nest(comment: ParsedRedditComment): ParsedRedditComment {
            val kids = byParent[comment.id].orEmpty().map { nest(it.second) }
            val replies = (comment.replies + kids).distinctBy { it.id }
            return comment.copy(replies = replies)
        }
        return flat
            .filter { (parent, _) -> parent.isBlank() || parent.startsWith("t3_") }
            .map { nest(it.second) }
            .distinctBy { it.id }
    }

    private fun moreChildIds(data: JsonObject): List<String> =
        data.array("children")
            ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
            ?.filter { it.isNotBlank() && it != "_" }
            .orEmpty()

    private fun extractMedia(data: JsonObject, fallbackId: String, description: String): List<ParsedRedditMedia> {
        val gallery = galleryMedia(data, description)
        if (gallery.isNotEmpty()) return gallery
        redditVideo(data, fallbackId, description)?.let { return listOf(it) }
        directVideo(data, fallbackId, description)?.let { return listOf(it) }
        directImage(data, fallbackId, description)?.let { return listOf(it) }
        previewImage(data, fallbackId, description)?.let { return listOf(it) }
        return emptyList()
    }

    private fun galleryMedia(data: JsonObject, description: String): List<ParsedRedditMedia> {
        val items = data.obj("gallery_data")?.array("items") ?: return emptyList()
        val metadata = data.obj("media_metadata") ?: return emptyList()
        return items.mapNotNull { item ->
            val mediaId = item.obj()?.string("media_id") ?: return@mapNotNull null
            val entry = metadata.obj(mediaId) ?: return@mapNotNull null
            if (entry.string("status") == "failed") return@mapNotNull null
            val source = entry.obj("s") ?: return@mapNotNull null
            val videoUrl = source.string("mp4")?.let(::httpsUrl)
            val imageUrl = source.string("u")?.let(::httpsUrl)
                ?: source.string("gif")?.let(::httpsUrl)
                ?: videoUrl
                ?: return@mapNotNull null
            ParsedRedditMedia(
                id = mediaId,
                imageUrl = if (imageUrl == videoUrl) "" else imageUrl,
                videoUrl = videoUrl,
                width = source.intOrNull("x"),
                height = source.intOrNull("y"),
                durationSeconds = null,
                contentDescription = description,
            )
        }
    }

    private fun redditVideo(data: JsonObject, fallbackId: String, description: String): ParsedRedditMedia? {
        val video = data.obj("secure_media")?.obj("reddit_video")
            ?: data.obj("media")?.obj("reddit_video")
            ?: return null
        val fallback = video.string("fallback_url")?.let(::httpsUrl)
        val dash = video.string("dash_url")?.let(::httpsUrl)
        val hls = video.string("hls_url")?.let(::httpsUrl)
        val audioIsSeparate = video.bool("has_audio") || video["has_audio"] == null
        val videoUrl = when {
            audioIsSeparate && dash != null -> dash
            audioIsSeparate && hls != null -> hls
            fallback != null -> fallback
            else -> hls ?: dash
        } ?: return null
        return ParsedRedditMedia(
            id = data.string("id") ?: fallbackId,
            imageUrl = previewUrl(data).orEmpty(),
            videoUrl = videoUrl,
            width = video.intOrNull("width"),
            height = video.intOrNull("height"),
            durationSeconds = video.intOrNull("duration"),
            contentDescription = description,
        )
    }

    private fun directVideo(data: JsonObject, fallbackId: String, description: String): ParsedRedditMedia? {
        val url = data.string("url")?.let(::httpsUrl) ?: return null
        if (!isVideoUrl(url)) return null
        return ParsedRedditMedia(
            id = data.string("id") ?: fallbackId,
            imageUrl = previewUrl(data).orEmpty(),
            videoUrl = url,
            width = null,
            height = null,
            durationSeconds = null,
            contentDescription = description,
        )
    }

    private fun directImage(data: JsonObject, fallbackId: String, description: String): ParsedRedditMedia? {
        val url = data.string("url")?.let(::httpsUrl) ?: return null
        if (!isImageUrl(url)) return null
        return ParsedRedditMedia(
            id = data.string("id") ?: fallbackId,
            imageUrl = url,
            videoUrl = null,
            width = null,
            height = null,
            durationSeconds = null,
            contentDescription = description,
        )
    }

    private fun previewImage(data: JsonObject, fallbackId: String, description: String): ParsedRedditMedia? {
        if (data.bool("is_self")) return null
        val imageUrl = previewUrl(data) ?: return null
        return ParsedRedditMedia(
            id = data.string("id") ?: fallbackId,
            imageUrl = imageUrl,
            videoUrl = null,
            width = null,
            height = null,
            durationSeconds = null,
            contentDescription = description,
        )
    }

    private fun previewUrl(data: JsonObject): String? =
        data.obj("preview")
            ?.array("images")
            ?.firstOrNull()
            ?.obj()
            ?.obj("source")
            ?.string("url")
            ?.let(::httpsUrl)

    private fun parseComment(element: JsonElement, depth: Int): ParsedRedditComment? {
        if (depth > MAX_COMMENT_DEPTH) return null
        val node = element.obj() ?: return null
        if (node.string("kind") != "t1") return null
        val data = node.obj("data") ?: return null
        val id = data.string("id")?.takeIf { it.isNotBlank() } ?: return null
        val replies = when (val repliesElement = data["replies"]) {
            is JsonObject -> repliesElement.obj("data")?.array("children").orEmpty()
                .mapNotNull { parseComment(it, depth + 1) }
            else -> emptyList()
        }
        return ParsedRedditComment(
            id = id,
            author = data.string("author")?.takeIf { it.isNotBlank() } ?: "[deleted]",
            body = data.string("body").orEmpty(),
            createdUtcEpochSeconds = data.epoch("created_utc"),
            isSubmitter = data.bool("is_submitter"),
            replies = replies,
        )
    }

    private fun displaySelfText(value: String?): String {
        val text = value?.trim().orEmpty()
        return if (text.isBlank() || text == "[deleted]" || text == "[removed]") "" else text
    }

    private fun httpsUrl(value: String): String? {
        val cleaned = value.replace("&amp;", "&").trim()
        if (!cleaned.startsWith("https://")) return null
        return cleaned
    }

    private fun isImageUrl(url: String): Boolean {
        val uri = runCatching { URI(url) }.getOrNull() ?: return false
        val host = uri.host?.lowercase(Locale.US).orEmpty()
        if (host == "i.redd.it") return true
        return IMAGE_EXTENSION.containsMatchIn(uri.path.orEmpty().lowercase(Locale.US))
    }

    private fun isVideoUrl(url: String): Boolean {
        val path = runCatching { URI(url).path }.getOrNull().orEmpty().lowercase(Locale.US)
        return path.endsWith(".mp4") || path.endsWith(".webm")
    }

    private fun JsonElement.obj(): JsonObject? = this as? JsonObject

    private fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject

    private fun JsonObject.array(key: String): JsonArray? = this[key] as? JsonArray

    private fun JsonObject.string(key: String): String? =
        (this[key] as? JsonPrimitive)?.contentOrNull

    private fun JsonObject.int(key: String): Int = intOrNull(key) ?: 0

    private fun JsonObject.intOrNull(key: String): Int? =
        (this[key] as? JsonPrimitive)?.content?.toDoubleOrNull()?.toInt()

    private fun JsonObject.epoch(key: String): Long =
        (this[key] as? JsonPrimitive)?.content?.toDoubleOrNull()?.toLong() ?: 0L

    private fun JsonObject.bool(key: String): Boolean =
        (this[key] as? JsonPrimitive)?.content == "true"

    private companion object {
        const val MAX_COMMENT_DEPTH = 32
        val IMAGE_EXTENSION = Regex("""\.(jpe?g|png|gif|webp)$""")
    }
}
