package app.pane.android.ui.mapper

import app.pane.android.R
import app.pane.android.domain.model.BundledImageKey
import app.pane.android.domain.model.Clock
import app.pane.android.domain.model.AuthorLines
import app.pane.android.domain.model.Comment
import app.pane.android.domain.model.ExternalPostMetadata
import app.pane.android.domain.model.InstagramMetadata
import app.pane.android.domain.model.LinkContent
import app.pane.android.domain.model.LinkKind
import app.pane.android.domain.model.LinkSource
import app.pane.android.domain.model.LoadStage
import app.pane.android.domain.model.MediaLocation
import app.pane.android.domain.model.RecentContent
import app.pane.android.domain.model.RedditMetadata
import app.pane.android.domain.model.SourceMetadata
import app.pane.android.domain.model.XReplyContinuation
import app.pane.android.ui.model.AuthorThreadPostUiModel
import app.pane.android.ui.model.CommentUiModel
import app.pane.android.ui.model.HomeUiState
import app.pane.android.ui.model.RecentLinkUiModel
import app.pane.android.ui.model.UiImage
import app.pane.android.ui.model.VideoSourceUiModel
import app.pane.android.ui.model.ViewerArticleUiModel
import app.pane.android.ui.model.ViewerLinkCardUiModel
import app.pane.android.ui.model.ViewerMediaItemUiModel
import app.pane.android.ui.model.ViewerPostUiModel
import app.pane.android.ui.model.ViewerQuoteUiModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.TextStyle
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.Locale
import kotlin.math.max

fun loadStageMessage(stage: LoadStage): String = when (stage) {
    LoadStage.Connecting -> "Connecting to the source"
    LoadStage.FetchingPage -> "Fetching the page"
    LoadStage.ExtractingContent -> "Finding the original source"
}

class UiImageMapper {
    fun map(location: MediaLocation): UiImage = when (location) {
        is MediaLocation.Remote -> UiImage.Url(location.url)
        is MediaLocation.Bundled -> UiImage.Resource(
            when (location.key) {
                BundledImageKey.Kyoto -> R.drawable.thumbnail_kyoto
                BundledImageKey.Material -> R.drawable.thumbnail_material
                BundledImageKey.FieldNotes -> R.drawable.thumbnail_field_notes
                BundledImageKey.Coast -> R.drawable.media_coast
            },
        )
    }
}

class HomeUiMapper(
    private val imageMapper: UiImageMapper,
    private val clock: Clock,
    private val zoneId: ZoneId = ZoneId.systemDefault(),
) {
    fun map(recents: List<RecentContent>): HomeUiState {
        if (recents.isEmpty()) return HomeUiState.Empty
        return HomeUiState.Content(
            recentLinks = recents.map { recent ->
                val content = recent.content
                if (content == null) {
                    RecentLinkUiModel(
                        url = recent.recentLink.url,
                        title = recent.recentLink.url,
                        sourceLabel = "",
                        ageLabel = ageLabel(recent.recentLink.openedAtEpochMillis),
                        thumbnail = null,
                        thumbnailDescription = recent.recentLink.url,
                        isCached = false,
                    )
                } else {
                    RecentLinkUiModel(
                        url = content.url,
                        title = content.title,
                        sourceLabel = sourceLabel(content),
                        ageLabel = ageLabel(recent.recentLink.openedAtEpochMillis),
                        thumbnail = imageMapper.map(content.thumbnail).takeUnless { image ->
                            image is UiImage.Url && image.value.isBlank()
                        },
                        thumbnailDescription = content.title,
                        isCached = true,
                    )
                }
            },
        )
    }

    private fun sourceLabel(content: LinkContent): String = when (content.source) {
        LinkSource.Instagram -> if (content.kind == LinkKind.Video) "INSTAGRAM · REEL" else "INSTAGRAM · POST"
        LinkSource.YouTube -> "YOUTUBE · VIDEO"
        LinkSource.TikTok -> "TIKTOK · CLIP"
        LinkSource.Reddit -> if (content.kind == LinkKind.Video) "REDDIT · VIDEO" else "REDDIT · POST"
        LinkSource.Facebook -> if (content.kind == LinkKind.Video) "FACEBOOK · VIDEO" else "FACEBOOK · POST"
        LinkSource.X -> if (content.kind == LinkKind.Video) "X · VIDEO" else "X · POST"
    }

    private fun ageLabel(openedAtEpochMillis: Long): String {
        val ageMillis = max(0L, clock.nowEpochMillis() - openedAtEpochMillis)
        val minutes = ageMillis / 60_000
        return when {
            minutes < 60 -> "${max(1L, minutes)}m"
            minutes < 1_440 -> "${minutes / 60}h"
            else -> Instant.ofEpochMilli(openedAtEpochMillis)
                .atZone(zoneId)
                .dayOfWeek
                .getDisplayName(TextStyle.SHORT, Locale.US)
                .uppercase(Locale.US)
        }
    }
}

class ViewerUiMapper(private val imageMapper: UiImageMapper) {
    fun map(content: LinkContent): ViewerPostUiModel {
        val sourceMetadata = content.sourceMetadata
        val mediaItems = if (sourceMetadata is InstagramMetadata && sourceMetadata.mediaItems.isNotEmpty()) {
            sourceMetadata.mediaItems.map { item ->
                ViewerMediaItemUiModel(
                    id = item.id,
                    image = imageMapper.map(MediaLocation.Remote(item.imageUrl)),
                    contentDescription = item.contentDescription,
                    videoUrl = bestVideoUrl(item.videoVariants),
                    width = item.width?.takeIf { it > 0 } ?: item.videoVariants.mapNotNull { it.width }.maxOrNull(),
                    height = item.height?.takeIf { it > 0 } ?: item.videoVariants.mapNotNull { it.height }.maxOrNull(),
                    videoSources = item.videoVariants.map { variant ->
                        VideoSourceUiModel(
                            url = variant.url,
                            width = variant.width,
                            height = variant.height,
                        )
                    },
                )
            }
        } else if (sourceMetadata is RedditMetadata && sourceMetadata.mediaItems.isNotEmpty()) {
            sourceMetadata.mediaItems.map { item ->
                ViewerMediaItemUiModel(
                    id = item.id,
                    image = imageMapper.map(MediaLocation.Remote(item.imageUrl)),
                    contentDescription = item.contentDescription,
                    videoUrl = item.videoUrl,
                    width = item.width?.takeIf { it > 0 },
                    height = item.height?.takeIf { it > 0 },
                    videoSources = item.videos.map { source ->
                        VideoSourceUiModel(
                            url = source.url,
                            width = source.width,
                            height = source.height,
                            bitrate = source.bitrate,
                            adaptive = source.adaptive,
                        )
                    },
                )
            }
        } else if (sourceMetadata is ExternalPostMetadata && sourceMetadata.mediaItems.isNotEmpty()) {
            sourceMetadata.mediaItems.map { item ->
                ViewerMediaItemUiModel(
                    id = item.id,
                    image = imageMapper.map(MediaLocation.Remote(item.imageUrl)),
                    contentDescription = item.contentDescription,
                    videoUrl = item.videoUrl,
                    videoSources = item.videos.map { source ->
                        VideoSourceUiModel(
                            url = source.url,
                            width = source.width,
                            height = source.height,
                            bitrate = source.bitrate,
                            adaptive = source.adaptive,
                        )
                    },
                )
            }
        } else {
            listOf(
                ViewerMediaItemUiModel(
                    id = content.url,
                    image = imageMapper.map(content.media.location),
                    contentDescription = content.media.contentDescription,
                    videoUrl = bestVideoUrl(sourceMetadata),
                ),
            )
        }
        val external = sourceMetadata as? ExternalPostMetadata
        val author = AuthorLines.present(content.author.name, content.author.metadata)
        val primaryId = when (sourceMetadata) {
            is ExternalPostMetadata -> sourceMetadata.postId
            is RedditMetadata -> sourceMetadata.postId
            is InstagramMetadata -> sourceMetadata.postId
            null -> null
        }
        val comments = commentsWithoutPrimary(primaryId, content.comments)
        val xContinuation = if (content.source == LinkSource.X) external?.replyContinuation else null
        return ViewerPostUiModel(
            title = content.title,
            isVideo = content.kind == LinkKind.Video,
            media = mediaItems.first().image,
            mediaDescription = mediaItems.first().contentDescription,
            mediaBadge = content.media.badge,
            duration = content.media.duration,
            videoUrl = mediaItems.first().videoUrl,
            authorName = author.name,
            authorMetadata = author.metadata,
            commentCount = content.commentCount,
            comments = comments.map { mapComment(it, content.source) },
            canLoadMoreComments = when {
                sourceMetadata is InstagramMetadata -> sourceMetadata.commentsEndCursor != null
                sourceMetadata is RedditMetadata -> sourceMetadata.moreCommentIds.isNotEmpty()
                xContinuation == XReplyContinuation.More -> !external?.repliesCursor.isNullOrBlank()
                else -> false
            },
            commentsTruncated = when (xContinuation) {
                XReplyContinuation.Blocked -> true
                XReplyContinuation.Exhausted, XReplyContinuation.More -> false
                null -> content.source == LinkSource.X && content.commentCount > comments.size
            },
            authorThread = external?.authorThread.orEmpty().map { item ->
                AuthorThreadPostUiModel(
                    id = item.id,
                    author = item.author,
                    text = item.text,
                    opened = item.id == external?.postId,
                )
            },
            authorThreadPartial = external?.authorThreadPartial == true,
            mediaItems = mediaItems,
            initialMediaIndex = requestedMediaIndex(content.url, mediaItems.size),
            sourceUrl = content.url,
            authorAvatar = content.author.avatarUrl
                ?.takeIf { it.startsWith("http://") || it.startsWith("https://") }
                ?.let { UiImage.Url(it) },
            authorProfileUrl = authorProfileUrl(content),
            article = external?.articleTitle?.takeIf { it.isNotBlank() }?.let { title ->
                external.articleUrl?.takeIf { it.isNotBlank() }?.let { url ->
                    ViewerArticleUiModel(
                        title = title,
                        preview = external.articlePreview.orEmpty(),
                        body = external.articleBody.orEmpty(),
                        coverUrl = external.articleCoverUrl,
                        url = url,
                    )
                }
            },
            quote = external?.quoteUrl?.takeIf { it.isNotBlank() }?.let { url ->
                ViewerQuoteUiModel(
                    authorName = external.quoteAuthor.orEmpty(),
                    handle = external.quoteHandle.orEmpty(),
                    text = external.quoteText.orEmpty(),
                    url = url,
                )
            },
            linkCards = external?.linkCards.orEmpty().map { card ->
                ViewerLinkCardUiModel(url = card.url, label = card.label, title = card.title)
            },
        )
    }

    private fun requestedMediaIndex(url: String, itemCount: Int): Int {
        if (itemCount <= 1) return 0
        val rawQuery = runCatching { URI(url).rawQuery }.getOrNull() ?: return 0
        val oneBasedIndex = rawQuery
            .split("&")
            .firstOrNull { it.substringBefore("=") == "img_index" }
            ?.substringAfter("=", "")
            ?.let { URLDecoder.decode(it, StandardCharsets.UTF_8.name()) }
            ?.toIntOrNull()
            ?: return 0
        return (oneBasedIndex - 1).coerceIn(0, itemCount - 1)
    }

    private fun bestVideoUrl(sourceMetadata: SourceMetadata?): String? =
        when (sourceMetadata) {
            is InstagramMetadata -> bestVideoUrl(sourceMetadata.videoVariants)
            is RedditMetadata -> sourceMetadata.mediaItems.firstOrNull()?.videoUrl
            is ExternalPostMetadata -> sourceMetadata.mediaItems.firstOrNull()?.videoUrl
            null -> null
        }

    private fun bestVideoUrl(
        variants: List<app.pane.android.domain.model.InstagramVideoVariant>,
    ): String? = variants.maxByOrNull { (it.width ?: 0) * (it.height ?: 0) }?.url

    private fun mapComment(comment: Comment, source: LinkSource): CommentUiModel {
        val handle = comment.handle?.removePrefix("@")?.takeIf { it.isNotEmpty() }
        return CommentUiModel(
            id = comment.id,
            author = comment.author,
            initial = comment.initial,
            age = comment.age,
            body = comment.body,
            isCreator = comment.isCreator,
            replies = comment.replies.map { mapComment(it, source) },
            avatarUrl = comment.avatarUrl?.takeIf { it.startsWith("http") },
            handle = handle,
            profileUrl = commentProfileUrl(source, comment.author, handle),
            cardTitle = comment.cardTitle,
            cardBody = comment.cardBody,
            cardUrl = comment.cardUrl,
        )
    }

    private fun authorProfileUrl(content: LinkContent): String? = when (content.source) {
        LinkSource.X -> xHandle(content.author.name, content.author.metadata)?.let { "https://x.com/$it" }
        LinkSource.Instagram -> {
            val user = (content.sourceMetadata as? InstagramMetadata)?.authorUsername ?: content.author.name
            igUser(user)?.let { "https://www.instagram.com/$it/" }
        }
        LinkSource.Reddit -> {
            val name = (content.sourceMetadata as? RedditMetadata)?.author ?: content.author.name
            redditUser(name)?.let { "https://www.reddit.com/user/$it" }
        }
        else -> null
    }

    private fun commentProfileUrl(source: LinkSource, author: String, handle: String?): String? = when (source) {
        LinkSource.X -> handle?.let { "https://x.com/$it" }
        LinkSource.Instagram -> igUser(author)?.let { "https://www.instagram.com/$it/" }
        LinkSource.Reddit -> redditUser(author)?.let { "https://www.reddit.com/user/$it" }
        else -> null
    }

    private fun xHandle(name: String, metadata: String): String? {
        val fromMeta = metadata.trim().removePrefix("@").substringBefore(' ')
        if (metadata.trim().startsWith("@") && X_HANDLE.matches(fromMeta)) return fromMeta
        val token = name.trim()
        return token.takeIf { X_HANDLE.matches(it) }
    }

    private fun igUser(value: String): String? =
        value.trim().takeIf { IG_USER.matches(it) }

    private fun redditUser(value: String): String? {
        val name = value.trim()
        if (name.equals("[deleted]", ignoreCase = true) || name.equals("[removed]", ignoreCase = true)) return null
        return name.takeIf { REDDIT_USER.matches(it) }
    }

    private val X_HANDLE = Regex("[A-Za-z0-9_]{1,15}")
    private val IG_USER = Regex("[A-Za-z0-9._]{1,30}")
    private val REDDIT_USER = Regex("[A-Za-z0-9_-]{2,21}")
}

/** THREAD replies are children. The opened status stays in the primary viewer only. */
internal fun commentsWithoutPrimary(primaryId: String?, comments: List<Comment>): List<Comment> {
    if (primaryId.isNullOrBlank()) return comments
    return comments.mapNotNull { comment ->
        if (comment.id == primaryId) {
            null
        } else {
            comment.copy(replies = commentsWithoutPrimary(primaryId, comment.replies))
        }
    }
}
