package app.pane.android.data.bluesky

import app.pane.android.domain.model.Author
import app.pane.android.domain.model.BlueskyMetadata
import app.pane.android.domain.model.BlueskyPostException
import app.pane.android.domain.model.BskyQuoteStub
import app.pane.android.domain.model.Comment
import app.pane.android.domain.model.ExternalMediaItem
import app.pane.android.domain.model.ExternalThreadPost
import app.pane.android.domain.model.LinkContent
import app.pane.android.domain.model.LinkKind
import app.pane.android.domain.model.LinkSource
import app.pane.android.domain.model.Media
import app.pane.android.domain.model.MediaLocation
import app.pane.android.domain.repository.LinkContentRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class BskyHttp(val code: Int, val body: String)

fun interface BskyTransport {
    fun get(url: String): BskyHttp
}

private const val BSKY_USER_AGENT = "Pane/1.3.1 (public post viewer)"

internal object HttpBskyTransport : BskyTransport {
    override fun get(url: String): BskyHttp {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.instanceFollowRedirects = true
        connection.connectTimeout = app.pane.android.data.net.HttpTimeouts.CONNECT_MILLIS
        connection.readTimeout = app.pane.android.data.net.HttpTimeouts.READ_MILLIS
        connection.setRequestProperty("Accept", "application/json")
        connection.setRequestProperty("User-Agent", BSKY_USER_AGENT)
        return try {
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            BskyHttp(code, stream?.bufferedReader()?.use { it.readText() }.orEmpty())
        } finally {
            connection.disconnect()
        }
    }
}

class BlueskyLinkContentRepository(
    private val transport: BskyTransport = HttpBskyTransport,
    private val sleep: (Long) -> Unit = { millis -> Thread.sleep(millis) },
) : LinkContentRepository {
    fun supports(url: String): Boolean = BskyUrls.parsePost(url) != null

    override suspend fun resolve(url: String): Result<LinkContent> = withContext(Dispatchers.IO) {
        runCatching { load(url) }
    }

    override suspend fun peekCached(url: String): LinkContent? = null

    override suspend fun refresh(url: String): Result<LinkContent> = resolve(url)

    private fun load(url: String): LinkContent {
        val ref = BskyUrls.parsePost(url) ?: throw IllegalArgumentException("Unsupported link")
        val root = fetch(ref.atUri)
        val moderation = BskyLabels.decide(root.post.postLabels, root.post.authorLabels, root.post.did)
        when (moderation.post) {
            BskyPostGate.Hide -> throw BlueskyPostException(BlueskyPostException.Kind.Hidden)
            BskyPostGate.SignedOut -> throw BlueskyPostException(BlueskyPostException.Kind.SignedIn)
            BskyPostGate.Show, BskyPostGate.Warn -> Unit
        }
        val self = selfThread(root)
        val selfUris = self.posts.map { it.uri }.toSet()
        val replies = root.replies.mapNotNull { reply ->
            if (reply.uri in root.hiddenReplies || reply.uri in selfUris) return@mapNotNull null
            val gate = BskyLabels.decide(reply.postLabels, reply.authorLabels, reply.did)
            if (gate.blocksRoot) return@mapNotNull null
            reply
        }
        return page(root.post, root.parent, replies, self.posts.drop(1), self.partial, moderation)
    }

    private data class Chain(val posts: List<BskyParsedPost>, val partial: Boolean)

    private fun selfThread(root: BskyParsedThread): Chain {
        val posts = mutableListOf(root.post)
        var partial = false
        var thread = root
        while (posts.size < SELF_THREAD_CAP) {
            val next = continuation(thread, posts.last().did) ?: break
            if (posts.any { it.uri == next.uri }) break
            val fetched = runCatching { fetch(next.uri) }.getOrElse {
                posts += next
                partial = true
                break
            }
            if (fetched.post.uri == next.uri) {
                posts += fetched.post
                thread = fetched
                if (posts.size == SELF_THREAD_CAP && continuation(fetched, fetched.post.did) != null) partial = true
            } else {
                posts += next
                break
            }
        }
        return Chain(posts, partial)
    }

    private fun continuation(thread: BskyParsedThread, authorDid: String): BskyParsedPost? =
        thread.replies
            .filter { it.did == authorDid && it.parentUri == thread.post.uri && it.uri !in thread.hiddenReplies }
            .filter { BskyLabels.decide(it.postLabels, it.authorLabels, it.did).post == BskyPostGate.Show ||
                BskyLabels.decide(it.postLabels, it.authorLabels, it.did).post == BskyPostGate.Warn }
            .minByOrNull { it.createdAt.orEmpty() }

    private fun fetch(atUri: String): BskyParsedThread {
        val encoded = URLEncoder.encode(atUri, Charsets.UTF_8.name())
        val endpoint = "$API?uri=$encoded&depth=1&parentHeight=1"
        var wait = 1_000L
        repeat(4) { attempt ->
            val http = transport.get(endpoint)
            if (http.code == 429 && attempt < 3) {
                sleep(wait)
                wait *= 2
                return@repeat
            }
            if (http.code == 400 && http.body.contains("NotFound")) {
                throw BlueskyPostException(BlueskyPostException.Kind.Gone)
            }
            if (http.code !in 200..299) throw IOException("bluesky http")
            return BskyThread.parse(http.body) ?: throw BlueskyPostException(BlueskyPostException.Kind.Gone)
        }
        throw IOException("bluesky http")
    }

    private fun page(
        post: BskyParsedPost,
        parent: BskyParsedPost?,
        replies: List<BskyParsedPost>,
        continuation: List<BskyParsedPost>,
        partial: Boolean,
        moderation: BskyModeration,
    ): LinkContent {
        val didUrl = "https://bsky.app/profile/${post.did}/post/${BskyThread.rkey(post.uri)}"
        val share = "https://bsky.app/profile/${post.handle}/post/${BskyThread.rkey(post.uri)}"
        val profile = "https://bsky.app/profile/${post.did}"
        val media = covered(post.media, moderation.media)
        val name = post.displayName.trim()
        val handle = post.handle.trim()
        val invalid = handle == "handle.invalid" || handle.isBlank()
        val authorName = name.ifBlank { if (invalid) "" else "@$handle" }
        val authorMeta = if (name.isBlank() || invalid) "" else "@$handle"
        val quote = quoteOf(post.quote)
        val thumb = media.firstOrNull()?.imageUrl?.takeIf { moderation.media == BskyMediaGate.Show && it.isNotBlank() }.orEmpty()
        val video = media.any { !it.videoUrl.isNullOrBlank() && it.cover == null }
        return LinkContent(
            url = didUrl,
            title = post.text,
            source = LinkSource.Bluesky,
            kind = if (video) LinkKind.Video else LinkKind.Post,
            thumbnail = MediaLocation.Remote(thumb),
            media = Media(
                location = MediaLocation.Remote(thumb),
                contentDescription = media.firstOrNull()?.contentDescription.orEmpty(),
            ),
            author = Author(
                name = authorName,
                metadata = authorMeta,
                avatarUrl = post.avatar?.takeIf { !moderation.avatarHidden && it.startsWith("http") },
            ),
            commentCount = post.replyCount,
            comments = replies.map { reply -> comment(reply) },
            sourceMetadata = BlueskyMetadata(
                postId = BskyThread.rkey(post.uri),
                did = post.did,
                handle = handle,
                shareUrl = share,
                profileUrl = profile,
                replyingToHandle = parent?.handle?.takeIf { post.parentUri != null && it.isNotBlank() && it != "handle.invalid" },
                replyingToUrl = parent?.did?.takeIf { post.parentUri != null }?.let { "https://bsky.app/profile/$it/post/${BskyThread.rkey(parent.uri)}" },
                spans = post.spans,
                mediaItems = if (continuation.isEmpty()) media else emptyList(),
                authorThread = if (continuation.isEmpty()) {
                    emptyList()
                } else {
                    listOf(threadPost(post, media, moderation.post == BskyPostGate.Warn)) + continuation.map { item ->
                        val gate = BskyLabels.decide(item.postLabels, item.authorLabels, item.did)
                        threadPost(item, covered(item.media, gate.media), gate.post == BskyPostGate.Warn)
                    }
                },
                authorThreadPartial = partial,
                contentWarning = moderation.post == BskyPostGate.Warn,
                avatarHidden = moderation.avatarHidden,
                createdAtEpochMillis = BskyThread.createdMillis(post.createdAt),
                quoteAuthor = quote?.author,
                quoteHandle = quote?.handle,
                quoteText = quote?.text,
                quoteUrl = quote?.url,
                quoteMedia = quote?.media.orEmpty(),
                quoteStub = quote?.stub,
                linkCards = listOfNotNull(post.linkCard),
            ),
        )
    }

    private data class QuoteBits(
        val author: String?,
        val handle: String?,
        val text: String?,
        val url: String?,
        val media: List<ExternalMediaItem>,
        val stub: BskyQuoteStub?,
    )

    private fun quoteOf(quote: BskyParsedQuote?): QuoteBits? {
        if (quote == null) return null
        val url = BskyThread.httpsPost(quote.uri)
        if (quote.stub != null) {
            return QuoteBits(null, null, null, url, emptyList(), quote.stub)
        }
        val gate = BskyLabels.decide(quote.labels, quote.authorLabels, quote.did)
        if (gate.post == BskyPostGate.SignedOut) {
            return QuoteBits(null, null, null, url, emptyList(), BskyQuoteStub.SignedIn)
        }
        if (gate.post == BskyPostGate.Hide) {
            return QuoteBits(null, null, null, url, emptyList(), BskyQuoteStub.Gone)
        }
        val handle = quote.handle.takeIf { it.isNotBlank() && it != "handle.invalid" }
        return QuoteBits(
            author = quote.displayName.ifBlank { handle?.let { "@$it" } },
            handle = handle,
            text = quote.text,
            url = url ?: "https://bsky.app/profile/${quote.did}",
            media = covered(quote.media, gate.media).take(1),
            stub = null,
        )
    }

    private fun threadPost(post: BskyParsedPost, media: List<ExternalMediaItem>, warning: Boolean): ExternalThreadPost =
        ExternalThreadPost(
            id = post.uri,
            author = post.displayName.ifBlank { post.handle },
            text = post.text,
            media = media,
            spans = post.spans,
            warning = warning,
        )

    private fun comment(post: BskyParsedPost): Comment {
        val gate = BskyLabels.decide(post.postLabels, post.authorLabels, post.did)
        val name = post.displayName.ifBlank { "@${post.handle}" }
        return Comment(
            id = post.uri,
            author = name,
            initial = name.removePrefix("@").firstOrNull()?.uppercaseChar()?.toString().orEmpty(),
            age = "",
            body = post.text,
            avatarUrl = post.avatar?.takeIf { !gate.avatarHidden },
            handle = post.handle,
            media = covered(post.media, gate.media),
            profileUrl = "https://bsky.app/profile/${post.did}",
            spans = post.spans,
        )
    }

    private fun covered(items: List<ExternalMediaItem>, gate: BskyMediaGate): List<ExternalMediaItem> {
        if (items.isEmpty() || gate == BskyMediaGate.Show) return items
        val cover = when (gate) {
            BskyMediaGate.HideGraphic -> "graphic"
            BskyMediaGate.HideAdult -> "adult"
            BskyMediaGate.Blur -> "nudity"
            BskyMediaGate.Show -> return items
        }
        val hide = gate == BskyMediaGate.HideAdult || gate == BskyMediaGate.HideGraphic
        return items.map { item ->
            if (hide) {
                item.copy(imageUrl = "", videoUrl = null, videos = emptyList(), cover = cover)
            } else {
                item.copy(cover = cover)
            }
        }
    }

    private companion object {
        const val API = "https://public.api.bsky.app/xrpc/app.bsky.feed.getPostThread"
        const val SELF_THREAD_CAP = 25
    }
}
