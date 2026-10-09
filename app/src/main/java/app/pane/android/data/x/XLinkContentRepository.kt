package app.pane.android.data.x

import app.pane.android.data.cache.LinkContentCacheStore
import app.pane.android.data.resolver.PageLoadProgressElement
import app.pane.android.data.resolver.PrioritizedUrlResolver
import app.pane.android.domain.model.Author
import app.pane.android.domain.model.AuthorLines
import app.pane.android.domain.model.Comment
import app.pane.android.domain.model.ExternalMediaItem
import app.pane.android.domain.model.ExternalPostMetadata
import app.pane.android.domain.model.ExternalThreadPost
import app.pane.android.domain.model.LinkContent
import app.pane.android.domain.model.LinkKind
import app.pane.android.domain.model.LinkSource
import app.pane.android.domain.model.XReplyContinuation
import app.pane.android.domain.model.Media
import app.pane.android.domain.model.PlayableVideo
import app.pane.android.domain.model.MediaLocation
import app.pane.android.domain.repository.LinkContentRepository
import app.pane.android.domain.repository.LoadProgressListener
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class XLinkContentRepository(
    private val pageLoaders: List<XPageLoader>,
    private val cacheStore: LinkContentCacheStore,
) : LinkContentRepository {
    private val resolverChain = PrioritizedUrlResolver(pageLoaders)
    private val successfulCache = ConcurrentHashMap<String, LinkContent>()
    private val loadMutex = Mutex()

    fun supports(url: String): Boolean = XUrls.supports(url)

    override suspend fun resolve(url: String): Result<LinkContent> =
        resolve(url, LoadProgressListener {})

    override suspend fun resolve(url: String, onProgress: LoadProgressListener): Result<LinkContent> =
        resolve(url, onProgress) {}

    override suspend fun resolve(
        url: String,
        onProgress: LoadProgressListener,
        onPreview: (LinkContent) -> Unit,
    ): Result<LinkContent> {
        val status = XUrls.parse(url) ?: return Result.failure(
            IllegalArgumentException("Unsupported X status URL: $url"),
        )
        cached(status.id, url)?.let { return Result.success(it) }
        return load(url, status, onProgress, onPreview)
    }

    override suspend fun peekCached(url: String): LinkContent? {
        val status = XUrls.parse(url) ?: return null
        return cached(status.id, url)
    }

    override suspend fun loadMoreComments(url: String): Result<LinkContent> {
        val status = XUrls.parse(url) ?: return Result.failure(
            IllegalArgumentException("Unsupported X status URL: $url"),
        )
        val current = cached(status.id, url)
            ?: return Result.failure(IllegalStateException("Load the X post before loading more comments"))
        val metadata = current.sourceMetadata as? ExternalPostMetadata ?: return Result.success(current)
        val cursor = metadata.repliesCursor
        if (metadata.replyContinuation != XReplyContinuation.More || cursor.isNullOrBlank()) {
            return Result.success(current)
        }
        val pager = pageLoaders.filterIsInstance<XConversationPager>().firstOrNull()
            ?: return Result.success(store(status.id, blocked(current, metadata)))
        return try {
            val page = pager.loadReplies(status.id, cursor)
            val known = current.comments.map { it.id }.toSet()
            val merged = mergeXReplyPage(metadata.postId, known, cursor, page)
            val updated = current.copy(
                comments = current.comments + merged.fresh.map(::mapReply),
                sourceMetadata = metadata.copy(
                    repliesCursor = merged.cursor,
                    replyContinuation = merged.continuation,
                ),
            )
            Result.success(store(status.id, updated))
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            Result.success(store(status.id, blocked(current, metadata)))
        }
    }

    override suspend fun refresh(url: String): Result<LinkContent> =
        refresh(url, LoadProgressListener {})

    override suspend fun refresh(url: String, onProgress: LoadProgressListener): Result<LinkContent> =
        refresh(url, onProgress) {}

    override suspend fun refresh(
        url: String,
        onProgress: LoadProgressListener,
        onPreview: (LinkContent) -> Unit,
    ): Result<LinkContent> {
        val status = XUrls.parse(url) ?: return Result.failure(
            IllegalArgumentException("Unsupported X status URL: $url"),
        )
        successfulCache.remove(status.id)
        cacheStore.remove(cacheKey(status.id))
        return load(url, status, onProgress, onPreview)
    }

    private suspend fun cached(id: String, url: String): LinkContent? {
        successfulCache[id]?.let { return it.withUrl(url) }
        return cacheStore.get(cacheKey(id))?.content?.withUrl(url)?.also { successfulCache[id] = it }
    }

    private suspend fun load(
        url: String,
        status: XUrls.Status,
        onProgress: LoadProgressListener,
        onPreview: (LinkContent) -> Unit,
    ): Result<LinkContent> = try {
        loadMutex.withLock {
            successfulCache[status.id]?.let { return@withLock Result.success(it.withUrl(url)) }
            val resolved = withContext(
                PageLoadProgressElement(onProgress) + XPreviewElement { post ->
                    runCatching { onPreview(map(url, post)) }
                },
            ) {
                // Keep the caller's host and handle so the fast status page can start immediately.
                resolverChain.resolveWithSource(url)
            }
            val content = map(url, resolved.value)
            successfulCache[status.id] = content
            cacheStore.put(cacheKey(status.id), content, resolved.resolverId)
            Result.success(content)
        }
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (error: Exception) {
        Result.failure(error)
    }

    private fun map(requestedUrl: String, post: ParsedXPost): LinkContent {
        val images = post.imageUrls
        val video = post.videoUrl
        val videos = post.videos.map { PlayableVideo(url = it.url, bitrate = it.bitrate) }
        val items = if (images.isEmpty() && video != null) {
            listOf(
                ExternalMediaItem(
                    id = post.id,
                    imageUrl = "",
                    contentDescription = post.text.take(200),
                    videoUrl = video,
                    videos = videos,
                ),
            )
        } else {
            images.mapIndexed { index, image ->
                ExternalMediaItem(
                    id = "${post.id}-$index",
                    imageUrl = image,
                    contentDescription = post.text.take(200),
                    videoUrl = if (index == 0) video else null,
                    videos = if (index == 0) videos else emptyList(),
                )
            }
        }
        val primaryImage = images.firstOrNull().orEmpty()
        val isVideo = !video.isNullOrBlank()
        val replies = post.replies.filter { it.id != post.id }
        val expanded = XRichText.displayText(
            text = post.text,
            entities = post.urlEntities,
            articleUrl = post.article?.url,
            quoteUrl = post.quote?.url,
            cardUrls = post.linkCards.map { it.url },
            stripMedia = images.isNotEmpty() || isVideo,
        )
        val visible = XRichText.visible(expanded).trim()
        val articleTitle = post.article?.title?.takeIf { it.isNotBlank() }
        val cardTitle = post.linkCards.firstOrNull { it.title.isNotBlank() }?.title
        val cardLabel = post.linkCards.firstOrNull { it.label.isNotBlank() }?.label
        val quoteText = post.quote?.text?.takeIf { it.isNotBlank() && !app.pane.android.domain.model.looksLikeUrl(it) }
        val caption = when {
            visible.isNotBlank() && !app.pane.android.domain.model.looksLikeUrl(visible) -> expanded
            articleTitle != null -> articleTitle
            cardTitle != null -> cardTitle
            cardLabel != null -> cardLabel
            quoteText != null -> quoteText
            else -> app.pane.android.domain.text.LinkLabels.domain(requestedUrl) ?: "X"
        }
        return LinkContent(
            url = requestedUrl,
            title = caption,
            source = LinkSource.X,
            kind = if (isVideo) LinkKind.Video else LinkKind.Post,
            thumbnail = MediaLocation.Remote(primaryImage),
            media = Media(
                location = MediaLocation.Remote(primaryImage),
                contentDescription = post.text.take(200),
                badge = when {
                    isVideo -> "VIDEO"
                    images.size > 1 -> "GALLERY"
                    images.isNotEmpty() -> "PHOTO"
                    else -> "TEXT"
                },
            ),
            author = xAuthor(post.author, post.screenName, post.avatarUrl),
            commentCount = post.commentCount,
            comments = replies.map(::mapReply),
            sourceMetadata = ExternalPostMetadata(
                postId = post.id,
                mediaItems = items,
                authorThread = post.authorThread.map { item ->
                    val parsed = item.media.mapIndexed { index, media -> media.toExternal(item.id, index, item.text) }
                    val media = if (parsed.isEmpty() && item.id == post.id) items else parsed
                    ExternalThreadPost(id = item.id, author = item.author, text = item.text, media = media)
                },
                authorThreadPartial = post.authorThreadPartial,
                repliesCursor = post.repliesCursor,
                replyContinuation = initialXReplyContinuation(
                    commentCount = post.commentCount,
                    loaded = replies.size,
                    cursor = post.repliesCursor,
                ),
                articleTitle = post.article?.title,
                articlePreview = post.article?.preview,
                articleBody = post.article?.body,
                articleCoverUrl = post.article?.coverUrl,
                articleUrl = post.article?.url,
                quoteAuthor = post.quote?.authorName,
                quoteHandle = post.quote?.handle,
                quoteText = post.quote?.text,
                quoteUrl = post.quote?.url,
                linkCards = post.linkCards.map { card ->
                    app.pane.android.domain.model.ExternalLinkCard(card.url, card.label, card.title)
                },
            ),
        )
    }

    private suspend fun store(id: String, content: LinkContent): LinkContent {
        successfulCache[id] = content
        cacheStore.put(cacheKey(id), content, "x-syndication")
        return content
    }

    private fun blocked(current: LinkContent, metadata: ExternalPostMetadata): LinkContent =
        current.copy(
            sourceMetadata = metadata.copy(
                repliesCursor = null,
                replyContinuation = XReplyContinuation.Blocked,
            ),
        )

    private fun xAuthor(name: String, screenName: String?, avatarUrl: String?): Author {
        val handle = screenName?.trim()?.removePrefix("@")
            ?.takeIf { it.isNotEmpty() && !AuthorLines.isSourceLabel(it) }
        val display = name.takeUnless { it.isBlank() || AuthorLines.isSourceLabel(it) }.orEmpty()
            .ifBlank { handle.orEmpty() }
        val meta = handle?.takeIf { !it.equals(display, ignoreCase = true) }?.let { "@$it" }.orEmpty()
        val presented = AuthorLines.present(display, meta)
        return Author(
            name = presented.name,
            metadata = presented.metadata,
            avatarUrl = avatarUrl?.takeIf { it.startsWith("http") },
        )
    }

    private fun mapReply(reply: ParsedXReply): Comment = Comment(
            id = reply.id,
            author = reply.author,
            initial = reply.author.firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()?.toString() ?: "?",
            age = formatAge(reply.createdAtEpochMillis),
            body = reply.text,
            avatarUrl = reply.avatarUrl?.takeIf { it.startsWith("http") },
            handle = reply.screenName?.removePrefix("@")?.takeIf { it.isNotEmpty() },
            cardTitle = reply.cardTitle,
            cardBody = reply.cardBody,
            cardUrl = reply.cardUrl,
            media = reply.media.mapIndexed { index, media -> media.toExternal(reply.id, index, reply.text) },
        )

    private fun ParsedXMedia.toExternal(ownerId: String, index: Int, text: String) = ExternalMediaItem(
        id = "$ownerId-$index",
        imageUrl = imageUrl,
        contentDescription = text.take(200),
        videoUrl = videoUrl,
        videos = videos.map { PlayableVideo(url = it.url, bitrate = it.bitrate) },
        width = width,
        height = height,
        gif = gif,
    )

    private fun formatAge(createdAtEpochMillis: Long): String {
        if (createdAtEpochMillis <= 0L) return ""
        val ageSeconds = max(0L, (System.currentTimeMillis() - createdAtEpochMillis) / 1_000L)
        return when {
            ageSeconds < 60 -> "now"
            ageSeconds < 3_600 -> "${ageSeconds / 60}m"
            ageSeconds < 86_400 -> "${ageSeconds / 3_600}h"
            else -> "${ageSeconds / 86_400}d"
        }
    }

    private fun LinkContent.withUrl(url: String): LinkContent =
        if (this.url == url) this else copy(url = url)

    private fun cacheKey(id: String): String = "x:$id"
}

internal data class XReplyMerge(
    val fresh: List<ParsedXReply>,
    val cursor: String?,
    val continuation: XReplyContinuation,
)

/** Drops the opened status, then keeps paging, stops cleanly, or marks the guest wall. */
internal fun mergeXReplyPage(
    primaryId: String,
    knownIds: Set<String>,
    requestedCursor: String,
    page: XReplyPage,
): XReplyMerge {
    if (page.blocked || page.nextCursor == requestedCursor) {
        return XReplyMerge(emptyList(), cursor = null, continuation = XReplyContinuation.Blocked)
    }
    val fresh = page.replies.filter { it.id != primaryId && it.id !in knownIds }
    val next = page.nextCursor?.takeIf { it.isNotBlank() }
    return when {
        fresh.isEmpty() && next == null -> XReplyMerge(emptyList(), null, XReplyContinuation.Blocked)
        next == null -> XReplyMerge(fresh, null, XReplyContinuation.Exhausted)
        else -> XReplyMerge(fresh, next, XReplyContinuation.More)
    }
}

internal fun initialXReplyContinuation(commentCount: Int, loaded: Int, cursor: String?): XReplyContinuation =
    when {
        !cursor.isNullOrBlank() -> XReplyContinuation.More
        commentCount > loaded -> XReplyContinuation.Blocked
        else -> XReplyContinuation.Exhausted
    }
