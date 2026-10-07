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
    pageLoaders: List<XPageLoader>,
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
        return Result.success(current)
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
        return LinkContent(
            url = requestedUrl,
            title = post.text,
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
            comments = post.replies.map(::mapReply),
            sourceMetadata = ExternalPostMetadata(
                postId = post.id,
                mediaItems = items,
                authorThread = post.authorThread.map { item ->
                    ExternalThreadPost(id = item.id, author = item.author, text = item.text)
                },
                authorThreadPartial = post.authorThreadPartial,
            ),
        )
    }

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
