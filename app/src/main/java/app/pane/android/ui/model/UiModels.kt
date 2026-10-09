package app.pane.android.ui.model

import androidx.annotation.DrawableRes
import androidx.compose.runtime.Immutable

@Immutable
sealed interface UiImage {
    @Immutable
    data class Resource(@param:DrawableRes val id: Int) : UiImage

    @Immutable
    data class Url(val value: String) : UiImage
}

@Immutable
sealed interface HomeUiState {
    data object Loading : HomeUiState

    @Immutable
    data class Content(
        val recentLinks: List<RecentLinkUiModel>,
    ) : HomeUiState

    data object Empty : HomeUiState
}

@Immutable
data class RecentLinkUiModel(
    val url: String,
    val title: String,
    val sourceLabel: String,
    val ageLabel: String,
    val thumbnail: UiImage?,
    val thumbnailDescription: String,
    val isCached: Boolean,
    val identity: String = "",
    val pfpUrl: String? = null,
    val sourceMark: Int? = null,
    val thumbUrl: String? = null,
    val video: Boolean = false,
    val starred: Boolean = false,
    val markAsAvatar: Boolean = false,
)

@Immutable
data class LedgerRowUi(
    val url: String,
    val title: String,
    val identity: String,
    val timeLabel: String,
    val pfp: UiImage?,
    val sourceMark: Int?,
    val thumb: UiImage?,
    val video: Boolean,
    val starred: Boolean,
    val tiktokId: String = "",
    val tiktokThumbUrl: String? = null,
    val globe: Boolean = false,
    val markAsAvatar: Boolean = false,
)

fun ViewerPostUiModel.gallery(ownerId: String): List<ViewerMediaItemUiModel> {
    if (ownerId.isBlank()) return mediaItemsOrPrimary()
    authorThread.firstOrNull { it.id == ownerId }?.media?.takeIf { it.isNotEmpty() }?.let { return it }
    fun walk(comments: List<CommentUiModel>): List<ViewerMediaItemUiModel>? {
        comments.forEach { comment ->
            if (comment.id == ownerId && comment.media.isNotEmpty()) return comment.media
            walk(comment.replies)?.let { return it }
        }
        return null
    }
    return walk(comments) ?: mediaItemsOrPrimary()
}

fun RecentLinkUiModel.asLedgerRow(): LedgerRowUi = LedgerRowUi(
    url = url,
    title = title,
    identity = if (markAsAvatar) identity else identity.ifBlank { sourceLabel },
    timeLabel = ageLabel,
    pfp = pfpUrl?.let(UiImage::Url),
    sourceMark = sourceMark,
    thumb = thumbUrl?.let(UiImage::Url) ?: thumbnail,
    video = video,
    starred = starred,
    markAsAvatar = markAsAvatar,
)

@Immutable
sealed interface ViewerUiState {
    @Immutable
    data class Loading(val progress: Float = 0f, val message: String = "") : ViewerUiState

    data class Unavailable(val url: String) : ViewerUiState

    /** The URL is supported, but the source did not return a post. */
    data class LoadFailed(val url: String, val reason: String) : ViewerUiState

    @Immutable
    data class Content(
        val post: ViewerPostUiModel,
        val isLoadingMoreComments: Boolean = false,
        val starred: Boolean = false,
    ) : ViewerUiState
}

@Immutable
data class ViewerPostUiModel(
    val title: String,
    val isVideo: Boolean,
    val media: UiImage,
    val mediaDescription: String,
    val mediaBadge: String?,
    val duration: String?,
    val videoUrl: String?,
    val authorName: String,
    val authorMetadata: String,
    val commentCount: Int,
    val comments: List<CommentUiModel>,
    val canLoadMoreComments: Boolean = false,
    val mediaItems: List<ViewerMediaItemUiModel> = emptyList(),
    val initialMediaIndex: Int = 0,
    val sourceUrl: String = "",
    val authorAvatar: UiImage? = null,
    /** Public page returned some replies, and no further page is available. */
    val commentsTruncated: Boolean = false,
    /** Author's own chain, root first. Empty unless this status is part of that chain. */
    val authorThread: List<AuthorThreadPostUiModel> = emptyList(),
    /** The public page is missing earlier posts by the same account. */
    val authorThreadPartial: Boolean = false,
    val authorProfileUrl: String? = null,
    val article: ViewerArticleUiModel? = null,
    val quote: ViewerQuoteUiModel? = null,
    val linkCards: List<ViewerLinkCardUiModel> = emptyList(),
    val description: String = "",
    val metaLine: String = "",
    val commentsNotice: ViewerCommentsNotice = ViewerCommentsNotice.None,
    val youtubeEmbedOff: Boolean = false,
    val youtubeAgeRestricted: Boolean = false,
    val tiktok: Boolean = false,
    val tiktokEmbedOff: Boolean = false,
    val tiktokRemoved: Boolean = false,
    val tiktokLive: Boolean = false,
    val tiktokDetailsFailed: Boolean = false,
    val tiktokVideoId: String = "",
    val tiktokHandle: String = "",
    val tiktokPostedAtEpochSeconds: Long? = null,
    val tiktokShortLink: Boolean = false,
    val bluesky: Boolean = false,
    val blueskyAvatarHidden: Boolean = false,
    val blueskyWarning: Boolean = false,
    val blueskySpans: List<TextSpanUi> = emptyList(),
    val replyingTo: String? = null,
    val replyingToUrl: String? = null,
)

enum class ViewerCommentsNotice { None, Unavailable, Off, Failed }

@Immutable
data class ViewerArticleUiModel(
    val title: String,
    val preview: String,
    val body: String,
    val coverUrl: String?,
    val url: String,
)

@Immutable
data class ViewerQuoteUiModel(
    val authorName: String,
    val handle: String,
    val text: String,
    val url: String,
    val media: List<ViewerMediaItemUiModel> = emptyList(),
    val stub: String? = null,
)

@Immutable
data class ViewerLinkCardUiModel(
    val url: String,
    val label: String,
    val title: String = "",
    val thumbUrl: String? = null,
)

@Immutable
data class AuthorThreadPostUiModel(
    val id: String,
    val author: String,
    val text: String,
    val opened: Boolean,
    val media: List<ViewerMediaItemUiModel> = emptyList(),
    val spans: List<TextSpanUi> = emptyList(),
    val warning: Boolean = false,
)

@Immutable
data class TextSpanUi(
    val start: Int,
    val end: Int,
    val url: String? = null,
)

@Immutable
data class VideoSourceUiModel(
    val url: String,
    val width: Int? = null,
    val height: Int? = null,
    val bitrate: Int? = null,
    val adaptive: Boolean = false,
)

@Immutable
data class ViewerMediaItemUiModel(
    val id: String,
    val image: UiImage,
    val contentDescription: String,
    val videoUrl: String?,
    val width: Int? = null,
    val height: Int? = null,
    val videoSources: List<VideoSourceUiModel> = emptyList(),
    val gif: Boolean = false,
    val cover: String? = null,
)

fun ViewerMediaItemUiModel.hasDownloadableMedia(): Boolean {
    if (!videoUrl.isNullOrBlank()) return true
    return (image as? UiImage.Url)?.value?.isNotBlank() == true
}

fun ViewerPostUiModel.mediaItemsOrPrimary(): List<ViewerMediaItemUiModel> =
    mediaItems.ifEmpty {
        listOf(
            ViewerMediaItemUiModel(
                id = "primary",
                image = media,
                contentDescription = mediaDescription,
                videoUrl = videoUrl,
            ),
        )
    }

@Immutable
data class CommentUiModel(
    val id: String,
    val author: String,
    val initial: String,
    val age: String,
    val body: String,
    val isCreator: Boolean,
    val replies: List<CommentUiModel>,
    val avatarUrl: String? = null,
    val handle: String? = null,
    val profileUrl: String? = null,
    val cardTitle: String? = null,
    val cardBody: String? = null,
    val cardUrl: String? = null,
    val media: List<ViewerMediaItemUiModel> = emptyList(),
)
