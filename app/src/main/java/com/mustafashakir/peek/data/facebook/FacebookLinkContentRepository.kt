package com.mustafashakir.peek.data.facebook

import com.mustafashakir.peek.data.cache.LinkContentCacheStore
import com.mustafashakir.peek.data.resolver.PageLoadProgressElement
import com.mustafashakir.peek.data.resolver.PrioritizedUrlResolver
import com.mustafashakir.peek.domain.model.Author
import com.mustafashakir.peek.domain.model.ExternalMediaItem
import com.mustafashakir.peek.domain.model.ExternalPostMetadata
import com.mustafashakir.peek.domain.model.LinkContent
import com.mustafashakir.peek.domain.model.LinkKind
import com.mustafashakir.peek.domain.model.LinkSource
import com.mustafashakir.peek.domain.model.Media
import com.mustafashakir.peek.domain.model.MediaLocation
import com.mustafashakir.peek.domain.repository.LinkContentRepository
import com.mustafashakir.peek.domain.repository.LoadProgressListener
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class FacebookLinkContentRepository(
    pageLoaders: List<FacebookPageLoader>,
    private val cacheStore: LinkContentCacheStore,
) : LinkContentRepository {
    private val resolverChain = PrioritizedUrlResolver(pageLoaders)
    private val successfulCache = ConcurrentHashMap<String, LinkContent>()
    private val loadMutex = Mutex()

    fun supports(url: String): Boolean = FacebookUrls.supports(url)

    override suspend fun resolve(url: String): Result<LinkContent> =
        resolve(url, LoadProgressListener {})

    override suspend fun resolve(url: String, onProgress: LoadProgressListener): Result<LinkContent> {
        val post = FacebookUrls.parse(url) ?: return Result.failure(
            IllegalArgumentException("Unsupported Facebook post URL: $url"),
        )
        cached(post.id, url)?.let { return Result.success(it) }
        return load(url, post, onProgress)
    }

    override suspend fun peekCached(url: String): LinkContent? {
        val post = FacebookUrls.parse(url) ?: return null
        return cached(post.id, url)
    }

    override suspend fun loadMoreComments(url: String): Result<LinkContent> {
        val post = FacebookUrls.parse(url) ?: return Result.failure(
            IllegalArgumentException("Unsupported Facebook post URL: $url"),
        )
        val current = cached(post.id, url)
            ?: return Result.failure(IllegalStateException("Load the Facebook post before loading more comments"))
        return Result.success(current)
    }

    override suspend fun refresh(url: String): Result<LinkContent> =
        refresh(url, LoadProgressListener {})

    override suspend fun refresh(url: String, onProgress: LoadProgressListener): Result<LinkContent> {
        val post = FacebookUrls.parse(url) ?: return Result.failure(
            IllegalArgumentException("Unsupported Facebook post URL: $url"),
        )
        successfulCache.remove(post.id)
        cacheStore.remove(cacheKey(post.id))
        return load(url, post, onProgress)
    }

    private suspend fun cached(id: String, url: String): LinkContent? {
        successfulCache[id]?.let { return it.withUrl(url) }
        return cacheStore.get(cacheKey(id))?.content?.withUrl(url)?.also { successfulCache[id] = it }
    }

    private suspend fun load(
        url: String,
        post: FacebookUrls.Post,
        onProgress: LoadProgressListener,
    ): Result<LinkContent> = try {
        loadMutex.withLock {
            successfulCache[post.id]?.let { return@withLock Result.success(it.withUrl(url)) }
            val resolved = withContext(PageLoadProgressElement(onProgress)) {
                resolverChain.resolveWithSource(post.canonicalUrl)
            }
            val content = map(url, resolved.value)
            successfulCache[post.id] = content
            cacheStore.put(cacheKey(post.id), content, resolved.resolverId)
            Result.success(content)
        }
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (error: Exception) {
        Result.failure(error)
    }

    private fun map(requestedUrl: String, post: ParsedFacebookPost): LinkContent {
        val images = post.imageUrls
        val video = post.videoUrl
        val primaryImage = images.firstOrNull().orEmpty()
        return LinkContent(
            url = requestedUrl,
            title = post.text,
            source = LinkSource.Facebook,
            kind = if (video != null) LinkKind.Video else LinkKind.Post,
            thumbnail = MediaLocation.Remote(primaryImage),
            media = Media(
                location = MediaLocation.Remote(primaryImage),
                contentDescription = post.text.take(200),
                badge = when {
                    video != null -> "VIDEO"
                    images.isNotEmpty() -> "PHOTO"
                    else -> "TEXT"
                },
            ),
            author = Author(name = post.author, metadata = "FACEBOOK"),
            commentCount = 0,
            comments = emptyList(),
            sourceMetadata = ExternalPostMetadata(
                postId = post.id,
                mediaItems = listOf(
                    ExternalMediaItem(
                        id = post.id,
                        imageUrl = primaryImage,
                        contentDescription = post.text.take(200),
                        videoUrl = video,
                    ),
                ).filter { it.imageUrl.isNotBlank() || !it.videoUrl.isNullOrBlank() },
            ),
        )
    }

    private fun LinkContent.withUrl(url: String): LinkContent =
        if (this.url == url) this else copy(url = url)

    private fun cacheKey(id: String): String = "facebook:$id"
}
