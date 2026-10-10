package app.pane.android.data.youtube

import app.pane.android.domain.model.Author
import app.pane.android.domain.model.Comment
import app.pane.android.domain.model.LinkContent
import app.pane.android.domain.model.LinkKind
import app.pane.android.domain.model.LinkSource
import app.pane.android.domain.model.Media
import app.pane.android.domain.model.MediaLocation
import app.pane.android.domain.model.YouTubeCommentsState
import app.pane.android.domain.model.YouTubeMetadata
import app.pane.android.domain.repository.LinkContentRepository
import app.pane.android.domain.repository.LoadProgressListener
import app.pane.android.domain.youtube.MapYouTubeConsentStore
import app.pane.android.domain.youtube.YouTubeComment
import app.pane.android.domain.youtube.YouTubeConsentPolicy
import app.pane.android.domain.youtube.YouTubeConsentStore
import app.pane.android.domain.youtube.YouTubeDataApi
import app.pane.android.domain.youtube.YouTubeLinkKind
import app.pane.android.domain.youtube.YouTubeVideo

/**
 * Resolves a YouTube URL without a network call until consent is stored.
 * After the tap, a key loads the Data API. No key falls back to oEmbed and leaves comments unavailable.
 * Playback stays on the IFrame. This repository never returns a direct stream.
 */
class YouTubeLinkContentRepository(
    private val consent: YouTubeConsentStore = MapYouTubeConsentStore(),
    private val api: YouTubeDataApi = InertYouTubeDataApi,
) : LinkContentRepository {
    private val pages = mutableMapOf<String, LinkContent>()

    fun supports(url: String): Boolean = YouTubeUrls.supports(url)

    override suspend fun resolve(url: String): Result<LinkContent> {
        val link = YouTubeUrls.parse(url) ?: return Result.failure(app.pane.android.domain.model.SourceFailure.Unsupported(url))
        val videoId = link.videoId ?: return Result.failure(app.pane.android.domain.model.SourceFailure.Unsupported(url))
        val canonical = link.canonicalUrl ?: url
        if (!hasConsent()) return Result.success(stub(url, canonical, videoId, blockedShort = false))
        val video = api.fetch(videoId)
        val comments = if (video.commentsUnavailable) {
            null
        } else {
            api.commentPage(videoId, null, 0)
        }
        val content = page(url, canonical, link.kind, link.startSeconds, video, comments)
        pages[canonical] = content
        return Result.success(content)
    }

    override suspend fun resolve(url: String, onProgress: LoadProgressListener): Result<LinkContent> = resolve(url)

    override suspend fun peekCached(url: String): LinkContent? = null

    override suspend fun loadMoreComments(url: String): Result<LinkContent> {
        if (!hasConsent()) return Result.failure(IllegalStateException("YouTube consent is required"))
        val canonical = YouTubeUrls.canonical(url) ?: url
        val current = pages[canonical] ?: return Result.failure(IllegalStateException("Open the video first"))
        val meta = current.sourceMetadata as? YouTubeMetadata ?: return Result.success(current)
        if (meta.commentsState == YouTubeCommentsState.Unavailable || meta.commentsState == YouTubeCommentsState.Off) {
            return Result.success(current)
        }
        val page = api.commentPage(meta.videoId, meta.commentPageToken, meta.commentPagesLoaded)
        if (page.hardWall) {
            val blocked = current.copy(sourceMetadata = meta.copy(commentsHardWall = true, commentPageToken = null))
            pages[canonical] = blocked
            return Result.success(blocked)
        }
        if (page.comments.isEmpty() && page.nextPageToken == null) return Result.success(current)
        val merged = current.copy(
            comments = current.comments + page.comments.map { it.toComment() },
            sourceMetadata = meta.copy(
                commentPageToken = page.nextPageToken,
                commentPagesLoaded = page.pagesLoaded,
                commentsState = page.state,
                commentsHardWall = page.hardWall,
            ),
        )
        pages[canonical] = merged
        return Result.success(merged)
    }

    override suspend fun refresh(url: String): Result<LinkContent> {
        YouTubeUrls.videoId(url)?.let(api::invalidate)
        YouTubeUrls.canonical(url)?.let(pages::remove)
        return resolve(url)
    }

    private fun hasConsent(): Boolean {
        val saved = consent.read() ?: return false
        return saved.policyVersion == YouTubeConsentPolicy.VERSION && saved.acceptedAtEpochMillis > 0L
    }

    private fun stub(url: String, canonical: String, videoId: String, blockedShort: Boolean): LinkContent =
        content(
            requestedUrl = url,
            canonical = canonical,
            video = YouTubeVideo(videoId),
            metadata = YouTubeMetadata(videoId = videoId, isShort = blockedShort, commentsState = YouTubeCommentsState.Ready),
            comments = emptyList(),
        )

    private fun page(
        requestedUrl: String,
        canonical: String,
        kind: YouTubeLinkKind,
        startSeconds: Int,
        video: YouTubeVideo,
        comments: app.pane.android.domain.youtube.YouTubeCommentPage?,
    ): LinkContent {
        val state = when {
            video.commentsUnavailable -> YouTubeCommentsState.Unavailable
            comments == null -> YouTubeCommentsState.Unavailable
            else -> comments.state
        }
        val meta = YouTubeMetadata(
            videoId = video.videoId,
            description = video.description,
            metaLine = metaLine(video, kind),
            channelUrl = video.channelUrl,
            handle = video.handle,
            commentPageToken = comments?.nextPageToken,
            commentPagesLoaded = comments?.pagesLoaded ?: 0,
            commentsHardWall = comments?.hardWall == true,
            commentsState = state,
            embeddable = video.embeddable,
            ageRestricted = video.ageRestricted,
            isShort = kind == YouTubeLinkKind.Short,
        )
        return content(
            requestedUrl = requestedUrl,
            canonical = canonical,
            video = video,
            metadata = meta.copy(description = video.description),
            comments = comments?.comments?.map { it.toComment() }.orEmpty(),
        ).let { page ->
            if (startSeconds > 0) page else page
        }
    }

    private fun content(
        requestedUrl: String,
        canonical: String,
        video: YouTubeVideo,
        metadata: YouTubeMetadata,
        comments: List<Comment>,
    ): LinkContent = LinkContent(
        url = if (hasConsent()) canonical else requestedUrl,
        title = video.title,
        source = LinkSource.YouTube,
        kind = LinkKind.Video,
        thumbnail = MediaLocation.Remote(video.thumbnailUrl),
        media = Media(
            location = MediaLocation.Remote(video.thumbnailUrl),
            contentDescription = video.title,
        ),
        author = Author(
            name = video.channelName,
            metadata = video.handle.takeIf { it.isNotBlank() }?.let { "@$it" }.orEmpty(),
            avatarUrl = video.avatarUrl.takeIf { it.isNotBlank() },
        ),
        commentCount = comments.size,
        comments = comments,
        sourceMetadata = metadata,
    )

    private fun metaLine(video: YouTubeVideo, kind: YouTubeLinkKind): String {
        val views = compact(video.viewCount)
        val parts = listOfNotNull(
            video.publishedLabel.takeIf { it.isNotBlank() },
            views?.let { "$it views" },
            "Short".takeIf { kind == YouTubeLinkKind.Short },
        )
        return parts.joinToString(" · ")
    }

    private fun compact(raw: String): String? {
        val value = raw.toLongOrNull() ?: return raw.takeIf { it.isNotBlank() }
        return when {
            value >= 1_000_000_000L -> "${value / 1_000_000_000L}B"
            value >= 1_000_000L -> "${value / 1_000_000L}M"
            value >= 1_000L -> "${value / 1_000L}K"
            else -> value.toString()
        }
    }

    private fun YouTubeComment.toComment(): Comment = Comment(
        id = id,
        author = author,
        initial = author.removePrefix("@").firstOrNull()?.uppercaseChar()?.toString() ?: "?",
        age = publishedAt.substringBefore('T'),
        body = body,
        avatarUrl = avatarUrl.takeIf { it.isNotBlank() },
    )
}

private object InertYouTubeDataApi : YouTubeDataApi {
    override fun fetch(videoId: String): YouTubeVideo = YouTubeVideo(videoId)
}
