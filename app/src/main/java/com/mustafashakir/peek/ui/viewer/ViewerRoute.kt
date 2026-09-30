package com.mustafashakir.peek.ui.viewer

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mustafashakir.peek.domain.usecase.DownloadMediaUseCase
import com.mustafashakir.peek.domain.usecase.PrepareMediaForSharingUseCase
import com.mustafashakir.peek.ui.actions.rememberPostActionCallbacks
import com.mustafashakir.peek.ui.model.ViewerUiState

@Composable
fun ViewerRoute(
    viewModel: ViewerViewModel,
    prepareMediaForSharing: PrepareMediaForSharingUseCase,
    downloadMedia: DownloadMediaUseCase,
    onBack: () -> Unit,
    onOpenMedia: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewerUiState by viewModel.uiState.collectAsStateWithLifecycle()
    val callbacks = rememberPostActionCallbacks(prepareMediaForSharing, downloadMedia)
    var autoOpenedVideo by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(viewerUiState) {
        val post = (viewerUiState as? ViewerUiState.Content)?.post ?: return@LaunchedEffect
        if (!VideoAutoplay.shouldOpen(autoOpenedVideo, post.hasVisualMedia())) return@LaunchedEffect
        autoOpenedVideo = true
        onOpenMedia(post.initialMediaIndex.coerceAtLeast(0))
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
        modifier = modifier.fillMaxSize(),
    )
}
