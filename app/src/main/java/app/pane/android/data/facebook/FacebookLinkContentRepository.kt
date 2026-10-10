package app.pane.android.data.facebook

import app.pane.android.data.cache.LinkContentCacheStore
import app.pane.android.data.resolver.PageLoadProgressElement
import app.pane.android.data.resolver.PrioritizedUrlResolver
import app.pane.android.domain.model.Author
import app.pane.android.domain.model.AuthorLines
import app.pane.android.domain.model.StoryUnavailableException
import app.pane.android.domain.model.ExternalMediaItem
import app.pane.android.domain.model.ExternalPostMetadata
import app.pane.android.domain.model.LinkContent
import app.pane.android.domain.model.LinkKind
import app.pane.android.domain.model.LinkSource
import app.pane.android.domain.model.Media
import app.pane.android.domain.model.MediaLocation
import app.pane.android.domain.model.PlayableVideo
import app.pane.android.domain.repository.LinkContentRepository
import app.pane.android.domain.repository.LoadProgressListener
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

    override suspend fun resolve(url: String, onProgress: LoadProgressListener): Result<LinkContent> =
        resolve(url, onProgress) {}

    override suspend fun resolve(
        url: String,
        onProgress: LoadProgressListener,
        onPreview: (LinkContent) -> Unit,
    ): Result<LinkContent> {
        val post = FacebookUrls.parse(url) ?: return Result.failure(
            app.pane.android.domain.model.SourceFailure.Unsupported(url),
        )
        cached(post.id, url)?.let { return Result.success(it) }
        return load(url, post, onProgress, onPreview)
    }

    override suspend fun peekCached(url: String): LinkContent? {
        val post = FacebookUrls.parse(url) ?: return null
        return cached(post.id, url)
    }

    override suspend fun loadMoreComments(url: String): Result<LinkContent> {
        val post = FacebookUrls.parse(url) ?: return Result.failure(
            app.pane.android.domain.model.SourceFailure.Unsupported(url),
        )
        val current = cached(post.id, url)
            ?: return Result.failure(IllegalStateException("Load the Facebook post before loading more comments"))
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
        val post = FacebookUrls.parse(url) ?: return Result.failure(
            app.pane.android.domain.model.SourceFailure.Unsupported(url),
        )
        successfulCache.remove(post.id)
        cacheStore.remove(cacheKey(post.id))
        return load(url, post, onProgress, onPreview)
    }

    private suspend fun cached(id: String, url: String): LinkContent? {
        successfulCache[id]?.let { return it.withUrl(url) }
        return cacheStore.get(cacheKey(id))?.content?.withUrl(url)?.also { successfulCache[id] = it }
    }

    private suspend fun load(
        url: String,
        post: FacebookUrls.Post,
        onProgress: LoadProgressListener,
        onPreview: (LinkContent) -> Unit,
    ): Result<LinkContent> = try {
        loadMutex.withLock {
            successfulCache[post.id]?.let { return@withLock Result.success(it.withUrl(url)) }
            val resolved = withContext(
                PageLoadProgressElement(onProgress) + FacebookPreviewElement { preview ->
                    runCatching { onPreview(map(url, preview)) }
                },
            ) {
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
        if (post.kind == FacebookUrls.Kind.Story) {
            Result.failure(StoryUnavailableException())
        } else {
            Result.failure(error)
        }
    }

    private fun map(requestedUrl: String, post: ParsedFacebookPost): LinkContent {
        val images = post.imageUrls
        val videos = post.videos
            .map { source ->
                PlayableVideo(
                    url = source.url,
                    width = source.width,
                    height = source.height,
                    bitrate = source.bitrate,
                )
            }
            .ifEmpty { listOfNotNull(post.videoUrl?.let { PlayableVideo(it) }) }
        val video = videos.maxWithOrNull(
            compareBy<PlayableVideo> { it.height ?: 0 }.thenBy { it.bitrate ?: 0 },
        )?.url ?: post.videoUrl
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
            author = facebookAuthor(requestedUrl, post),
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
                        videos = videos,
                    ),
                ).filter { it.imageUrl.isNotBlank() || !it.videoUrl.isNullOrBlank() },
            ),
        )
    }

    private fun facebookAuthor(url: String, post: ParsedFacebookPost): Author {
        val presented = AuthorLines.present(
            name = post.author,
            metadata = FacebookUrls.pageHandle(url).orEmpty(),
        )
        return Author(
            name = presented.name,
            metadata = presented.metadata,
            avatarUrl = post.authorAvatarUrl,
        )
    }

    private fun LinkContent.withUrl(url: String): LinkContent =
        if (this.url == url) this else copy(url = url)

    private fun cacheKey(id: String): String = "facebook:$id"
}
