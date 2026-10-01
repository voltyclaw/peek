package app.pane.android.ui.mapper

import app.pane.android.R
import app.pane.android.domain.model.BundledImageKey
import app.pane.android.domain.model.Clock
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
import app.pane.android.ui.model.AuthorThreadPostUiModel
import app.pane.android.ui.model.CommentUiModel
import app.pane.android.ui.model.HomeUiState
import app.pane.android.ui.model.RecentLinkUiModel
import app.pane.android.ui.model.UiImage
import app.pane.android.ui.model.VideoSourceUiModel
import app.pane.android.ui.model.ViewerMediaItemUiModel
import app.pane.android.ui.model.ViewerPostUiModel
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
        return ViewerPostUiModel(
            title = content.title,
            isVideo = content.kind == LinkKind.Video,
            media = mediaItems.first().image,
            mediaDescription = mediaItems.first().contentDescription,
            mediaBadge = content.media.badge,
            duration = content.media.duration,
            videoUrl = mediaItems.first().videoUrl,
            authorName = content.author.name,
            authorMetadata = content.author.metadata,
            commentCount = content.commentCount,
            comments = content.comments.map(::mapComment),
            canLoadMoreComments = when (sourceMetadata) {
                is InstagramMetadata -> sourceMetadata.commentsEndCursor != null
                is RedditMetadata -> sourceMetadata.moreCommentIds.isNotEmpty()
                else -> false
            },
            commentsTruncated = content.source == LinkSource.X &&
                content.comments.isNotEmpty() &&
                content.commentCount > content.comments.size,
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

    private fun mapComment(comment: Comment): CommentUiModel = CommentUiModel(
        id = comment.id,
        author = comment.author,
        initial = comment.initial,
        age = comment.age,
        body = comment.body,
        isCreator = comment.isCreator,
        replies = comment.replies.map(::mapComment),
    )
}
