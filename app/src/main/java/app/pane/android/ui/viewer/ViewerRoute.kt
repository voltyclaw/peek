package app.pane.android.ui.viewer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pane.android.domain.usecase.DownloadMediaUseCase
import app.pane.android.domain.usecase.PrepareMediaForSharingUseCase
import app.pane.android.ui.actions.rememberPostActionCallbacks
import app.pane.android.ui.media.VideoQuality
import app.pane.android.ui.model.ViewerUiState
import app.pane.android.ui.model.mediaItemsOrPrimary

@Composable
fun ViewerRoute(
    viewModel: ViewerViewModel,
    prepareMediaForSharing: PrepareMediaForSharingUseCase,
    downloadMedia: DownloadMediaUseCase,
    onBack: () -> Unit,
    onOpenMedia: (Int) -> Unit,
    modifier: Modifier = Modifier,
    videoQuality: VideoQuality = VideoQuality.Auto,
) {
    val viewerUiState by viewModel.uiState.collectAsStateWithLifecycle()
    val callbacks = rememberPostActionCallbacks(prepareMediaForSharing, downloadMedia)
    var handedToImmersive by rememberSaveable { mutableStateOf(false) }
    var measuredWidth by remember { mutableFloatStateOf(0f) }
    var measuredHeight by remember { mutableFloatStateOf(0f) }
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val viewportWidth = configuration.screenWidthDp * density.density
    val viewportHeight = configuration.screenHeightDp * density.density
    val content = viewerUiState as? ViewerUiState.Content
    val items = content?.post?.mediaItemsOrPrimary().orEmpty()
    val mediaIndex = content?.post?.initialMediaIndex?.coerceIn(0, items.lastIndex.coerceAtLeast(0)) ?: 0
    val item = items.getOrNull(mediaIndex)
    val contentWidth = item?.width?.takeIf { it > 1 }?.toFloat() ?: measuredWidth
    val contentHeight = item?.height?.takeIf { it > 1 }?.toFloat() ?: measuredHeight
    val openImmersive = VideoAutoplay.shouldOpen(
        alreadyOpened = handedToImmersive,
        contentWidthPx = contentWidth,
        contentHeightPx = contentHeight,
        viewportWidthPx = viewportWidth,
        viewportHeightPx = viewportHeight,
    )
    LaunchedEffect(openImmersive, mediaIndex) {
        if (openImmersive) {
            onOpenMedia(mediaIndex)
            handedToImmersive = true
        }
    }

    if (openImmersive) {
        Box(modifier.fillMaxSize().background(Color.Black))
        return
    }

    ViewerView(
        uiState = viewerUiState,
        onBack = onBack,
        onRefresh = viewModel::onRefresh,
        onLoadMoreComments = viewModel::onLoadMoreComments,
        onOpenMedia = onOpenMedia,
        onCopyLink = callbacks.onCopyLink,
        onCopyMedia = callbacks.onCopyMedia,
        onDownload = callbacks.onDownload,
        onShare = callbacks.onShare,
        onSharePost = callbacks.onSharePost,
        onOpenInApp = callbacks.onOpenInApp,
        videoQuality = videoQuality,
        onMediaMeasured = { width, height ->
            if (width > 1f && height > 1f) {
                measuredWidth = width
                measuredHeight = height
            }
        },
        modifier = modifier.fillMaxSize(),
    )
}
