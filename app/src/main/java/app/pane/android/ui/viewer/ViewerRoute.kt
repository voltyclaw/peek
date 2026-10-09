package app.pane.android.ui.viewer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.pane.android.ui.components.PaneSnackbarHost
import app.pane.android.ui.components.showForFiveSeconds
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import app.pane.android.R
import app.pane.android.ui.actions.openExternally
import app.pane.android.ui.actions.shouldFinishAfterExternalOpen
import app.pane.android.domain.usecase.DownloadMediaUseCase
import app.pane.android.domain.usecase.PrepareMediaForSharingUseCase
import app.pane.android.ui.actions.rememberPostActionCallbacks
import app.pane.android.ui.media.VideoQuality
import app.pane.android.ui.model.ViewerUiState
import app.pane.android.ui.model.mediaItemsOrPrimary
import app.pane.android.data.youtube.YouTubeUrls
import app.pane.android.domain.youtube.YouTubePlayer
import app.pane.android.ui.youtube.YouTubeFrame

@Composable
fun ViewerRoute(
    viewModel: ViewerViewModel,
    prepareMediaForSharing: PrepareMediaForSharingUseCase,
    downloadMedia: DownloadMediaUseCase,
    onBack: () -> Unit,
    onLeave: () -> Unit = onBack,
    onOpenMedia: (Int) -> Unit,
    modifier: Modifier = Modifier,
    videoQuality: VideoQuality = VideoQuality.Auto,
    startMuted: () -> Boolean = { true },
    onMutedChange: (Boolean) -> Unit = {},
    onOpenLinked: ((String) -> Unit)? = null,
    youTubeConsented: Boolean = false,
    onAcceptYouTube: (String) -> Unit = {},
    onYouTubePlayerShown: (String) -> Unit = {},
) {
    val viewerUiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val starredMessage = stringResource(R.string.starred_snack)
    val unstarredMessage = stringResource(R.string.unstarred_snack)
    val undoLabel = stringResource(R.string.undo)
    LaunchedEffect(viewModel) {
        viewModel.notice.collect { notice ->
            when (notice) {
                ViewerNotice.Starred -> snackbar.showForFiveSeconds(starredMessage)
                is ViewerNotice.Unstarred -> snackbar.showForFiveSeconds(unstarredMessage, undoLabel) {
                    viewModel.undoStar(notice.undo)
                }
            }
        }
    }
    val callbacks = rememberPostActionCallbacks(prepareMediaForSharing, downloadMedia)
    val context = LocalContext.current
    val noteUrl = (viewerUiState as? ViewerUiState.Content)?.post?.sourceUrl.orEmpty()
    var note by remember(noteUrl) { mutableStateOf(if (noteUrl.isBlank()) "" else PostNotes.read(context, noteUrl)) }
    var handedToImmersive by rememberSaveable { mutableStateOf(false) }
    var measuredWidth by remember { mutableFloatStateOf(0f) }
    var measuredHeight by remember { mutableFloatStateOf(0f) }
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val viewportWidth = configuration.screenWidthDp * density.density
    val viewportHeight = configuration.screenHeightDp * density.density
    val content = viewerUiState as? ViewerUiState.Content
    val sourceUrl = content?.post?.sourceUrl.orEmpty()
    val youtubeId = remember(sourceUrl) { YouTubeUrls.videoId(sourceUrl) }
    var acceptedYouTube by remember(sourceUrl) { mutableStateOf(false) }
    val playingYouTube = youtubeId != null && (youTubeConsented || acceptedYouTube)
    LaunchedEffect(youtubeId, youTubeConsented, acceptedYouTube) {
        if (youtubeId != null && youTubeConsented && !acceptedYouTube) onYouTubePlayerShown(youtubeId)
    }
    val youtubeFrame = youtubeId?.let { id ->
        YouTubeFrame(
            embedUrl = if (playingYouTube) YouTubePlayer.embedUrl(id) else null,
            onPlay = {
                acceptedYouTube = true
                onAcceptYouTube(id)
            },
            onOpenLink = { link ->
                val started = openExternally(context, link, finishAfter = false)
                if (!started) {
                    Toast.makeText(context, context.getString(R.string.action_failed), Toast.LENGTH_SHORT).show()
                }
            },
        )
    }
    val items = content?.post?.mediaItemsOrPrimary().orEmpty()
    val mediaIndex = content?.post?.initialMediaIndex?.coerceIn(0, items.lastIndex.coerceAtLeast(0)) ?: 0
    val item = items.getOrNull(mediaIndex)
    val contentWidth = item?.width?.takeIf { it > 1 }?.toFloat() ?: measuredWidth
    val contentHeight = item?.height?.takeIf { it > 1 }?.toFloat() ?: measuredHeight
    val openImmersive = youtubeId == null && VideoAutoplay.shouldOpen(
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

    Box(modifier.fillMaxSize()) {
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
        onOpenInApp = { url ->
            val started = openExternally(context, url, finishAfter = true)
            if (shouldFinishAfterExternalOpen(started, finishAfter = true)) onBack()
            else if (!started) Toast.makeText(context, context.getString(R.string.action_failed), Toast.LENGTH_SHORT).show()
        },
        videoQuality = videoQuality,
        startMuted = startMuted,
        onMutedChange = onMutedChange,
        onLeave = onLeave,
        note = note,
        onSaveNote = { saved ->
            note = saved
            if (noteUrl.isNotBlank()) PostNotes.write(context, noteUrl, saved)
        },
        onStar = viewModel::onToggleStar,
        onOpenOutbound = { target ->
            val openInPane = onOpenLinked
            if (openInPane != null) {
                openInPane(target)
            } else {
                val started = openExternally(context, target, finishAfter = false)
                if (!started) {
                    Toast.makeText(context, context.getString(R.string.action_failed), Toast.LENGTH_SHORT).show()
                }
            }
        },
        onMediaMeasured = { width, height ->
            if (width > 1f && height > 1f) {
                measuredWidth = width
                measuredHeight = height
            }
        },
        youtube = youtubeFrame,
        modifier = Modifier.fillMaxSize(),
    )
        PaneSnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = 88.dp))
    }
}
