package app.pane.android.ui.player

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pane.android.domain.usecase.DownloadMediaUseCase
import app.pane.android.domain.usecase.PrepareMediaForSharingUseCase
import app.pane.android.ui.actions.rememberPostActionCallbacks
import app.pane.android.ui.media.VideoQuality

@Composable
fun PlayerRoute(
    viewModel: PlayerViewModel,
    prepareMediaForSharing: PrepareMediaForSharingUseCase,
    downloadMedia: DownloadMediaUseCase,
    initialMediaIndex: Int,
    onBack: () -> Unit,
    onLeave: () -> Unit = onBack,
    videoQuality: VideoQuality = VideoQuality.Auto,
    startMuted: () -> Boolean = { true },
    onMutedChange: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val callbacks = rememberPostActionCallbacks(prepareMediaForSharing, downloadMedia)

    PlayerView(
        uiState = uiState,
        initialMediaIndex = initialMediaIndex,
        videoQuality = videoQuality,
        startMuted = startMuted,
        onMutedChange = onMutedChange,
        onBack = onBack,
        onLeave = onLeave,
        onMore = {},
        onLoadMoreComments = viewModel::onLoadMoreComments,
        onCopyLink = callbacks.onCopyLink,
        onCopyMedia = callbacks.onCopyMedia,
        onDownload = callbacks.onDownload,
        onShare = callbacks.onShare,
        onSharePost = callbacks.onSharePost,
        onOpenInApp = callbacks.onOpenInApp,
        modifier = modifier.fillMaxSize(),
    )
}
