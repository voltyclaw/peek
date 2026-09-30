package app.pane.android.data.reddit

import app.pane.android.data.cache.LinkContentCacheStore
import app.pane.android.data.resolver.PageLoadProgressElement
import app.pane.android.data.resolver.PrioritizedUrlResolver
import app.pane.android.domain.model.Author
import app.pane.android.domain.model.Comment
import app.pane.android.domain.model.LinkContent
import app.pane.android.domain.model.LinkKind
import app.pane.android.domain.model.LinkSource
import app.pane.android.domain.model.Media
import app.pane.android.domain.model.MediaLocation
import app.pane.android.domain.model.RedditMediaItem
import app.pane.android.domain.model.RedditMetadata
import app.pane.android.domain.repository.LinkContentRepository
import app.pane.android.domain.repository.LoadProgressListener
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class RedditLinkContentRepository(
    pageLoaders: List<RedditPageLoader>,
    private val cacheStore: LinkContentCacheStore,
) : LinkContentRepository {
    private val resolverChain = PrioritizedUrlResolver(pageLoaders)
    private val successfulCache = ConcurrentHashMap<String, LinkContent>()
    private val shareToPostId = ConcurrentHashMap<String, String>()
    private val loadMutex = Mutex()

    fun supports(url: String): Boolean = RedditUrls.supports(url)

    override suspend fun resolve(url: String): Result<LinkContent> =
        resolve(url, LoadProgressListener {})

    override suspend fun resolve(url: String, onProgress: LoadProgressListener): Result<LinkContent> {
        if (!supports(url)) {
            return Result.failure(IllegalArgumentException("Unsupported Reddit post URL: $url"))
        }
        return load(url, onProgress, forceNetwork = false)
    }

    override suspend fun peekCached(url: String): LinkContent? {
        val id = cachedPostId(url) ?: return null
        successfulCache[id]?.let { return it.withUrl(url) }
        return cacheStore.get(cacheKey(id))?.content?.withUrl(url)?.also { successfulCache[id] = it }
    }

    override suspend fun refresh(url: String): Result<LinkContent> =
        refresh(url, LoadProgressListener {})

    override suspend fun refresh(url: String, onProgress: LoadProgressListener): Result<LinkContent> {
        if (!supports(url)) {
            return Result.failure(IllegalArgumentException("Unsupported Reddit post URL: $url"))
        }
        val id = RedditUrls.direct(url)?.id ?: shareToPostId.remove(url)
        if (id != null) {
            successfulCache.remove(id)
            cacheStore.remove(cacheKey(id))
        }
        return load(url, onProgress, forceNetwork = true)
    }

    private suspend fun load(
        url: String,
        onProgress: LoadProgressListener,
        forceNetwork: Boolean,
    ): Result<LinkContent> = try {
        loadMutex.withLock {
            val knownId = cachedPostId(url)
            if (!forceNetwork && knownId != null) {
                cachedContent(knownId, url)?.let { return@withLock Result.success(it) }
            }
            val resolved = withContext(PageLoadProgressElement(onProgress)) {
                resolverChain.resolveWithSource(url)
            }
            val content = mapToLinkContent(url, resolved.value)
            successfulCache[content.postId()] = content
            if (RedditUrls.isShareLink(url)) shareToPostId[url] = content.postId()
            cacheStore.put(cacheKey(content.postId()), content, resolved.resolverId)
            Result.success(content)
        }
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (error: Exception) {
        Result.failure(error)
    }

    private suspend fun cachedContent(id: String, url: String): LinkContent? {
        successfulCache[id]?.let { return it.withUrl(url) }
        return cacheStore.get(cacheKey(id))?.content?.withUrl(url)?.also { successfulCache[id] = it }
    }

    private fun cachedPostId(url: String): String? =
        RedditUrls.direct(url)?.id ?: shareToPostId[url]

    private fun mapToLinkContent(requestedUrl: String, post: ParsedRedditPost): LinkContent {
        val mediaItems = post.media.mapIndexed { index, item ->
            RedditMediaItem(
                id = item.id.ifBlank { "${post.id}-$index" },
                imageUrl = item.imageUrl,
                contentDescription = item.contentDescription.ifBlank { post.title },
                videoUrl = item.videoUrl,
                width = item.width,
                height = item.height,
                durationSeconds = item.durationSeconds,
            )
        }
        val primary = mediaItems.firstOrNull()
        val isSingleVideo = mediaItems.size == 1 && primary?.videoUrl != null
        val imageUrl = primary?.imageUrl?.takeIf { it.isNotBlank() }.orEmpty()
        val body = post.selfText.takeIf { it.isNotBlank() && !it.equals(post.title, ignoreCase = true) }
        return LinkContent(
            url = requestedUrl,
            title = if (body == null) post.title else "${post.title}\n\n$body",
            source = LinkSource.Reddit,
            kind = if (isSingleVideo) LinkKind.Video else LinkKind.Post,
            thumbnail = MediaLocation.Remote(imageUrl),
            media = Media(
                location = MediaLocation.Remote(imageUrl),
                contentDescription = primary?.contentDescription ?: post.title,
                badge = when {
                    mediaItems.size > 1 -> "GALLERY"
                    isSingleVideo -> "VIDEO"
                    mediaItems.size == 1 -> "PHOTO"
                    else -> "TEXT"
                },
                duration = primary?.durationSeconds?.takeIf { it > 0 }?.let(::formatDuration),
            ),
            author = Author(
                name = post.author,
                metadata = buildString {
                    append("REDDIT · r/${post.subreddit}")
                    if (post.over18) append(" · NSFW")
                    if (post.spoiler) append(" · SPOILER")
                    append(" · ${formatCount(post.score)} POINTS")
                },
            ),
            commentCount = post.commentCount,
            comments = post.comments.map(::mapComment),
            sourceMetadata = RedditMetadata(
                postId = post.id,
                subreddit = post.subreddit,
                permalink = post.permalink,
                score = post.score,
                commentCount = post.commentCount,
                createdUtcEpochSeconds = post.createdUtcEpochSeconds,
                author = post.author,
                over18 = post.over18,
                spoiler = post.spoiler,
                mediaItems = mediaItems,
            ),
        )
    }

    private fun mapComment(comment: ParsedRedditComment): Comment = Comment(
        id = comment.id,
        author = comment.author,
        initial = comment.author.firstOrNull { it.isLetter() }?.uppercaseChar()?.toString() ?: "?",
        age = formatAge(comment.createdUtcEpochSeconds),
        body = comment.body,
        isCreator = comment.isSubmitter,
        replies = comment.replies.map(::mapComment),
    )

    private fun formatAge(createdAtEpochSeconds: Long): String {
        if (createdAtEpochSeconds <= 0L) return ""
        val ageSeconds = max(0L, System.currentTimeMillis() / 1_000L - createdAtEpochSeconds)
        return when {
            ageSeconds < 60 -> "now"
            ageSeconds < 3_600 -> "${ageSeconds / 60}m"
            ageSeconds < 86_400 -> "${ageSeconds / 3_600}h"
            else -> "${ageSeconds / 86_400}d"
        }
    }

    private fun formatDuration(seconds: Int): String {
        val hours = seconds / 3_600
        val minutes = (seconds % 3_600) / 60
        val remainder = seconds % 60
        return if (hours > 0) {
            "%d:%02d:%02d".format(Locale.US, hours, minutes, remainder)
        } else {
            "%02d:%02d".format(Locale.US, minutes, remainder)
        }
    }

    private fun formatCount(count: Int): String = when {
        count >= 1_000_000 -> "${count / 1_000_000}M"
        count >= 10_000 -> "${count / 1_000}K"
        count >= 1_000 -> String.format(Locale.US, "%.1fK", count / 1_000.0)
        else -> count.toString()
    }

    private fun LinkContent.postId(): String =
        (sourceMetadata as RedditMetadata).postId

    private fun LinkContent.withUrl(url: String): LinkContent =
        if (this.url == url) this else copy(url = url)

    private fun cacheKey(id: String): String = "reddit:$id"
}
