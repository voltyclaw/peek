package app.pane.android.data.x

import app.pane.android.data.cache.LinkContentCacheStore
import app.pane.android.data.resolver.PageLoadProgressElement
import app.pane.android.data.resolver.PrioritizedUrlResolver
import app.pane.android.domain.model.Author
import app.pane.android.domain.model.ExternalMediaItem
import app.pane.android.domain.model.ExternalPostMetadata
import app.pane.android.domain.model.LinkContent
import app.pane.android.domain.model.LinkKind
import app.pane.android.domain.model.LinkSource
import app.pane.android.domain.model.Media
import app.pane.android.domain.model.MediaLocation
import app.pane.android.domain.repository.LinkContentRepository
import app.pane.android.domain.repository.LoadProgressListener
import java.util.concurrent.ConcurrentHashMap
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

    override suspend fun resolve(url: String, onProgress: LoadProgressListener): Result<LinkContent> {
        val status = XUrls.parse(url) ?: return Result.failure(
            IllegalArgumentException("Unsupported X status URL: $url"),
        )
        cached(status.id, url)?.let { return Result.success(it) }
        return load(url, status, onProgress)
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

    override suspend fun refresh(url: String, onProgress: LoadProgressListener): Result<LinkContent> {
        val status = XUrls.parse(url) ?: return Result.failure(
            IllegalArgumentException("Unsupported X status URL: $url"),
        )
        successfulCache.remove(status.id)
        cacheStore.remove(cacheKey(status.id))
        return load(url, status, onProgress)
    }

    private suspend fun cached(id: String, url: String): LinkContent? {
        successfulCache[id]?.let { return it.withUrl(url) }
        return cacheStore.get(cacheKey(id))?.content?.withUrl(url)?.also { successfulCache[id] = it }
    }

    private suspend fun load(
        url: String,
        status: XUrls.Status,
        onProgress: LoadProgressListener,
    ): Result<LinkContent> = try {
        loadMutex.withLock {
            successfulCache[status.id]?.let { return@withLock Result.success(it.withUrl(url)) }
            val resolved = withContext(PageLoadProgressElement(onProgress)) {
                resolverChain.resolveWithSource(status.canonicalUrl)
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
        val items = if (images.isEmpty() && video != null) {
            listOf(
                ExternalMediaItem(
                    id = post.id,
                    imageUrl = "",
                    contentDescription = post.text.take(200),
                    videoUrl = video,
                ),
            )
        } else {
            images.mapIndexed { index, image ->
                ExternalMediaItem(
                    id = "${post.id}-$index",
                    imageUrl = image,
                    contentDescription = post.text.take(200),
                    videoUrl = if (index == 0) video else null,
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
            author = Author(name = post.author, metadata = "X"),
            commentCount = post.commentCount,
            comments = emptyList(),
            sourceMetadata = ExternalPostMetadata(postId = post.id, mediaItems = items),
        )
    }

    private fun LinkContent.withUrl(url: String): LinkContent =
        if (this.url == url) this else copy(url = url)

    private fun cacheKey(id: String): String = "x:$id"
}
