package app.pane.android.data.bluesky

import app.pane.android.domain.model.BskyQuoteStub
import app.pane.android.domain.model.BskyTextSpan
import app.pane.android.domain.model.ExternalLinkCard
import app.pane.android.domain.model.ExternalMediaItem
import app.pane.android.domain.model.PlayableVideo
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URI
import java.time.Instant

internal data class BskyParsedPost(
    val uri: String,
    val did: String,
    val handle: String,
    val displayName: String,
    val avatar: String?,
    val text: String,
    val createdAt: String?,
    val spans: List<BskyTextSpan>,
    val replyCount: Int,
    val postLabels: List<BskyLabel>,
    val authorLabels: List<BskyLabel>,
    val media: List<ExternalMediaItem>,
    val linkCard: ExternalLinkCard?,
    val quote: BskyParsedQuote?,
    val parentUri: String?,
)

internal data class BskyParsedQuote(
    val stub: BskyQuoteStub?,
    val uri: String,
    val did: String,
    val handle: String,
    val displayName: String,
    val text: String,
    val media: List<ExternalMediaItem>,
    val labels: List<BskyLabel>,
    val authorLabels: List<BskyLabel>,
)

internal data class BskyParsedThread(
    val post: BskyParsedPost,
    val parent: BskyParsedPost?,
    val replies: List<BskyParsedPost>,
    val hiddenReplies: Set<String>,
)

internal object BskyThread {
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(body: String): BskyParsedThread? {
        val root = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull() ?: return null
        val thread = root.obj("thread") ?: return null
        val type = thread.str("\$type").orEmpty()
        if (type.endsWith("#notFoundPost") || type.endsWith("#blockedPost")) return null
        val post = postView(thread.obj("post")) ?: return null
        val parent = thread.obj("parent")?.let { node ->
            if (node.str("\$type").orEmpty().endsWith("Post")) null else postView(node.obj("post"))
        }
        val hidden = hiddenReplies(thread)
        val replies = thread.arr("replies").orEmpty().mapNotNull { node ->
            val obj = node as? JsonObject ?: return@mapNotNull null
            val replyType = obj.str("\$type").orEmpty()
            if (replyType.endsWith("#notFoundPost") || replyType.endsWith("#blockedPost")) return@mapNotNull null
            postView(obj.obj("post"))
        }
        return BskyParsedThread(post, parent, replies, hidden)
    }

    fun httpsPost(uri: String): String? {
        val body = uri.removePrefix("at://").trim('/')
        val parts = body.split('/')
        if (parts.size < 3 || parts[1] != "app.bsky.feed.post") return null
        return "https://bsky.app/profile/${parts[0]}/post/${parts[2]}"
    }

    fun rkey(uri: String): String = uri.substringAfterLast('/')

    fun createdMillis(createdAt: String?): Long? =
        createdAt?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }

    private fun postView(post: JsonObject?): BskyParsedPost? {
        if (post == null) return null
        val uri = post.str("uri") ?: return null
        val author = post.obj("author") ?: return null
        val did = author.str("did").orEmpty()
        val handle = author.str("handle").orEmpty()
        val record = post.obj("record")
        val text = record?.str("text").orEmpty()
        val embed = readEmbed(post.obj("embed"), adult = false)
        return BskyParsedPost(
            uri = uri,
            did = did,
            handle = handle,
            displayName = author.str("displayName").orEmpty(),
            avatar = author.str("avatar"),
            text = text,
            createdAt = record?.str("createdAt"),
            spans = facets(text, record?.arr("facets")),
            replyCount = post.int("replyCount") ?: 0,
            postLabels = labels(post["labels"]),
            authorLabels = labels(author["labels"]),
            media = embed.media,
            linkCard = embed.card,
            quote = embed.quote,
            parentUri = record?.obj("reply")?.obj("parent")?.str("uri"),
        )
    }

    private fun facets(text: String, array: JsonArray?): List<BskyTextSpan> {
        if (array == null) return emptyList()
        val marks = array.mapNotNull { element ->
            val facet = element as? JsonObject ?: return@mapNotNull null
            val index = facet.obj("index") ?: return@mapNotNull null
            val start = index.int("byteStart") ?: return@mapNotNull null
            val end = index.int("byteEnd") ?: return@mapNotNull null
            val feature = facet.arr("features").orEmpty().firstNotNullOfOrNull { it as? JsonObject }
            val type = feature?.str("\$type").orEmpty()
            val url = when {
                type.endsWith("#mention") -> feature?.str("did")?.let { "https://bsky.app/profile/$it" }
                type.endsWith("#link") -> feature?.str("uri")
                else -> null
            }
            BskyFacetMark(start, end, url)
        }
        return BskyFacets.spans(text, marks)
    }

    private data class EmbedRead(
        val media: List<ExternalMediaItem> = emptyList(),
        val card: ExternalLinkCard? = null,
        val quote: BskyParsedQuote? = null,
    )

    private fun readEmbed(embed: JsonObject?, adult: Boolean): EmbedRead {
        if (embed == null) return EmbedRead()
        val type = embed.str("\$type").orEmpty()
        return when {
            type.endsWith("recordWithMedia#view") -> {
                val media = readEmbed(embed.obj("media"), adult)
                val quote = readRecord(embed.obj("record"))
                media.copy(quote = quote)
            }
            type.endsWith("record#view") -> EmbedRead(quote = readRecord(embed))
            type.endsWith("images#view") -> EmbedRead(media = images(embed.arr("images"), "image"))
            type.endsWith("gallery#view") -> EmbedRead(media = gallery(embed))
            type.endsWith("video#view") -> EmbedRead(media = listOfNotNull(video(embed)))
            type.endsWith("external#view") -> external(embed.obj("external"))
            else -> EmbedRead()
        }
    }

    private fun gallery(embed: JsonObject): List<ExternalMediaItem> {
        val items = embed.arr("items") ?: embed.arr("images") ?: return emptyList()
        return items.mapIndexedNotNull { index, element ->
            val obj = element as? JsonObject ?: return@mapIndexedNotNull null
            val image = obj.obj("image") ?: obj
            imageItem(image, "gallery-$index")
        }
    }

    private fun images(array: JsonArray?, prefix: String): List<ExternalMediaItem> =
        array.orEmpty().mapIndexedNotNull { index, element ->
            val obj = element as? JsonObject ?: return@mapIndexedNotNull null
            imageItem(obj, "$prefix-$index")
        }

    private fun imageItem(obj: JsonObject, id: String): ExternalMediaItem? {
        val full = obj.str("fullsize") ?: obj.str("thumb") ?: return null
        val ratio = obj.obj("aspectRatio")
        return ExternalMediaItem(
            id = id,
            imageUrl = full,
            contentDescription = obj.str("alt").orEmpty(),
            width = ratio?.int("width"),
            height = ratio?.int("height"),
        )
    }

    private fun video(obj: JsonObject): ExternalMediaItem? {
        val playlist = obj.str("playlist") ?: return null
        val ratio = obj.obj("aspectRatio")
        val gif = obj.str("presentation") == "gif"
        return ExternalMediaItem(
            id = obj.str("cid") ?: playlist,
            imageUrl = obj.str("thumbnail").orEmpty(),
            contentDescription = obj.str("alt").orEmpty(),
            videoUrl = playlist,
            videos = listOf(PlayableVideo(url = playlist, adaptive = playlist.contains(".m3u8"))),
            width = ratio?.int("width"),
            height = ratio?.int("height"),
            gif = gif,
        )
    }

    private fun external(external: JsonObject?): EmbedRead {
        if (external == null) return EmbedRead()
        val uri = external.str("uri").orEmpty()
        val host = runCatching { URI(uri).host }.getOrNull()?.removePrefix("www.").orEmpty()
        if (host == "media.tenor.com") {
            return EmbedRead(
                media = listOf(
                    ExternalMediaItem(
                        id = uri.ifBlank { "tenor" },
                        imageUrl = external.str("thumb").orEmpty(),
                        contentDescription = external.str("title").orEmpty(),
                        videoUrl = uri.takeIf { it.isNotBlank() },
                        gif = true,
                    ),
                ),
            )
        }
        if (uri.isBlank()) return EmbedRead()
        return EmbedRead(
            card = ExternalLinkCard(
                url = uri,
                label = host.ifBlank { uri },
                title = external.str("title").orEmpty(),
                thumbUrl = external.str("thumb"),
            ),
        )
    }

    private fun readRecord(recordView: JsonObject?): BskyParsedQuote? {
        val record = recordView?.obj("record") ?: recordView ?: return null
        val type = record.str("\$type").orEmpty()
        val uri = record.str("uri").orEmpty()
        when {
            type.endsWith("#viewNotFound") || type.endsWith("#viewBlocked") ->
                return BskyParsedQuote(BskyQuoteStub.Gone, uri, "", "", "", "", emptyList(), emptyList(), emptyList())
            type.endsWith("#viewDetached") ->
                return BskyParsedQuote(BskyQuoteStub.Detached, uri, "", "", "", "", emptyList(), emptyList(), emptyList())
            type.contains("generator") || type.contains("listView") || type.contains("starterPack") || type.contains("labeler") ->
                return BskyParsedQuote(BskyQuoteStub.Nested, uri, "", "", "", "", emptyList(), emptyList(), emptyList())
        }
        val author = record.obj("author")
        val value = record.obj("value")
        val embeds = record.arr("embeds").orEmpty()
        val nested = embeds.any { (it as? JsonObject)?.str("\$type").orEmpty().contains("record") }
        val media = if (nested) {
            emptyList()
        } else {
            embeds.flatMap { element ->
                val obj = element as? JsonObject ?: return@flatMap emptyList()
                readEmbed(obj, adult = false).media
            }
        }
        val did = author?.str("did").orEmpty()
        return BskyParsedQuote(
            stub = if (nested) BskyQuoteStub.Nested else null,
            uri = uri,
            did = did,
            handle = author?.str("handle").orEmpty(),
            displayName = author?.str("displayName").orEmpty(),
            text = if (nested) "" else value?.str("text").orEmpty(),
            media = if (nested) emptyList() else media,
            labels = labels(record["labels"]),
            authorLabels = labels(author?.get("labels")),
        )
    }

    private fun hiddenReplies(thread: JsonObject): Set<String> {
        val gate = thread.obj("threadgate")?.obj("record") ?: thread.obj("post")?.obj("threadgate")?.obj("record")
        return gate?.arr("hiddenReplies").orEmpty().mapNotNull { element ->
            when (element) {
                is JsonObject -> element.str("uri")
                else -> element.jsonPrimitive.contentOrNull
            }
        }.toSet()
    }

    private fun labels(node: JsonElement?): List<BskyLabel> {
        val array = when (node) {
            is JsonArray -> node
            is JsonObject -> node["values"] as? JsonArray
            else -> null
        } ?: return emptyList()
        return array.mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            val value = obj.str("val") ?: obj.str("value") ?: return@mapNotNull null
            BskyLabel(
                value = value,
                src = obj.str("src").orEmpty(),
                neg = obj["neg"]?.jsonPrimitive?.booleanOrNull == true,
            )
        }
    }

    private fun JsonObject.str(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull
    private fun JsonObject.int(key: String): Int? = this[key]?.jsonPrimitive?.intOrNull
    private fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject
    private fun JsonObject.arr(key: String): JsonArray? = this[key] as? JsonArray
}
