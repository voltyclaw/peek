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
    val sourceChip: String = "",
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
)

@Immutable
data class AuthorThreadPostUiModel(
    val id: String,
    val author: String,
    val text: String,
    val opened: Boolean,
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
)
