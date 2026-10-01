package app.pane.android.ui.player

import android.app.Activity
import android.content.pm.ActivityInfo
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import app.pane.android.R
import app.pane.android.ui.components.PeekImage
import app.pane.android.ui.media.PHOTO_FALLBACK_ASPECT
import app.pane.android.ui.media.VIDEO_FALLBACK_ASPECT
import app.pane.android.ui.media.VideoSurface
import app.pane.android.ui.media.contentAspectRatio
import app.pane.android.ui.media.displayVideoSize
import app.pane.android.ui.media.exoPlayerFor
import app.pane.android.ui.media.fittedContentPx
import app.pane.android.ui.model.ViewerMediaItemUiModel
import app.pane.android.ui.model.ViewerPostUiModel
import app.pane.android.ui.model.ViewerUiState
import app.pane.android.ui.model.mediaItemsOrPrimary

@Composable
fun PlayerView(
    uiState: ViewerUiState,
    initialMediaIndex: Int,
    onBack: () -> Unit,
    onMore: () -> Unit,
    modifier: Modifier = Modifier,
    onLoadMoreComments: () -> Unit = {},
    onCopyLink: suspend (String) -> Unit = {},
    onCopyMedia: suspend (ViewerMediaItemUiModel) -> Unit = {},
    onDownload: suspend (List<ViewerMediaItemUiModel>) -> Unit = {},
    onShare: suspend (List<ViewerMediaItemUiModel>) -> Unit = {},
    onSharePost: suspend (String, String?) -> Unit = { _, _ -> },
    onOpenInApp: suspend (String) -> Unit = {},
) {
    val view = LocalView.current
    DisposableEffect(view) {
        val activity = view.context as? Activity
        val window = activity?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        val previousLightStatusBars = controller?.isAppearanceLightStatusBars
        val previousLightNavigationBars = controller?.isAppearanceLightNavigationBars
        controller?.isAppearanceLightStatusBars = false
        controller?.isAppearanceLightNavigationBars = false
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
        onDispose {
            if (previousLightStatusBars != null) {
                controller.isAppearanceLightStatusBars = previousLightStatusBars
            }
            if (previousLightNavigationBars != null) {
                controller.isAppearanceLightNavigationBars = previousLightNavigationBars
            }
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
    }
    SideEffect {
        val window = (view.context as? Activity)?.window ?: return@SideEffect
        val controller = WindowCompat.getInsetsController(window, view)
        controller.isAppearanceLightStatusBars = false
        controller.isAppearanceLightNavigationBars = false
    }
    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
        when (uiState) {
            is ViewerUiState.Loading -> LoadingMedia(uiState, onBack)
            is ViewerUiState.Unavailable -> UnavailableMedia(onBack)
            is ViewerUiState.LoadFailed -> UnavailableMedia(onBack, uiState.reason)
            is ViewerUiState.Content -> MediaContent(
                post = uiState.post,
                initialMediaIndex = initialMediaIndex,
                onBack = onBack,
            )
        }
    }
}

@Composable
private fun LoadingBackButton(onBack: () -> Unit) {
    Box(
        modifier = Modifier
            .statusBarsPadding()
            .padding(20.dp)
            .size(42.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.4f))
            .clickable(role = Role.Button, onClick = onBack),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.AutoMirrored.Rounded.ArrowBack,
            contentDescription = stringResource(R.string.back),
            tint = Color.White,
            modifier = Modifier.size(22.dp),
        )
    }
}

@Composable
private fun LoadingMedia(uiState: ViewerUiState.Loading, onBack: () -> Unit) {
    Box(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CircularProgressIndicator(
                progress = { uiState.progress.coerceIn(0f, 1f) },
                color = Color.White,
                trackColor = Color.White.copy(alpha = 0.24f),
                modifier = Modifier.size(36.dp),
            )
        }
        LoadingBackButton(onBack)
    }
}

@Composable
private fun UnavailableMedia(onBack: () -> Unit, reason: String = "") {
    Box(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.align(Alignment.Center).padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.content_unavailable),
                color = Color.White,
            )
            if (reason.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = reason,
                    color = Color.White.copy(alpha = 0.72f),
                    textAlign = TextAlign.Center,
                )
            }
        }
        LoadingBackButton(onBack)
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun MediaContent(
    post: ViewerPostUiModel,
    initialMediaIndex: Int,
    onBack: () -> Unit,
) {
    val items = post.mediaItemsOrPrimary()
    val initialPage = initialMediaIndex.coerceIn(0, items.lastIndex)
    val pagerState = rememberPagerState(
        initialPage = initialPage,
        pageCount = items::size,
    )
    val currentVideoUrl = items[pagerState.currentPage].videoUrl
    val context = LocalContext.current
    val exoPlayer = remember(currentVideoUrl) {
        currentVideoUrl?.let { videoUrl ->
            exoPlayerFor(context, videoUrl).apply {
                setMediaItem(MediaItem.fromUri(videoUrl))
                repeatMode = Player.REPEAT_MODE_ONE
                volume = 1f
                playWhenReady = true
                prepare()
            }
        }
    }
    val density = LocalDensity.current
    val revealThreshold = with(density) { 56.dp.toPx() }
    var chromeVisible by remember { mutableStateOf(false) }
    var playbackSize by remember(currentVideoUrl) { mutableStateOf<Pair<Float, Float>?>(null) }
    var playing by remember(exoPlayer) { mutableStateOf(exoPlayer?.playWhenReady == true) }
    var muted by remember(exoPlayer) { mutableStateOf(false) }
    DisposableEffect(exoPlayer) {
        val player = exoPlayer
        if (player == null) {
            onDispose { }
        } else {
            val listener = object : Player.Listener {
                override fun onVideoSizeChanged(videoSize: VideoSize) {
                    playbackSize = displayVideoSize(
                        videoSize.width,
                        videoSize.height,
                        videoSize.pixelWidthHeightRatio,
                    )
                }
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    playing = isPlaying
                }
            }
            player.addListener(listener)
            onDispose {
                player.removeListener(listener)
                player.release()
            }
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .pointerInput(onBack, revealThreshold) {
                val slop = viewConfiguration.touchSlop
                awaitEachGesture {
                    val down = awaitFirstDown(pass = PointerEventPass.Initial, requireUnconsumed = false)
                    var totalDx = 0f
                    var totalDy = 0f
                    var decided = false
                    var vertical = false
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) {
                            if (vertical && swipeExitsFullscreen(totalDx, totalDy, revealThreshold)) onBack()
                            break
                        }
                        val delta = change.position - change.previousPosition
                        totalDx += delta.x
                        totalDy += delta.y
                        if (!decided && (kotlin.math.abs(totalDx) > slop || kotlin.math.abs(totalDy) > slop)) {
                            decided = true
                            vertical = kotlin.math.abs(totalDy) > kotlin.math.abs(totalDx)
                        }
                        if (vertical) change.consume()
                    }
                }
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { chromeVisible = !chromeVisible },
            ),
    ) {
        val boxW = constraints.maxWidth.toFloat()
        val boxH = constraints.maxHeight.toFloat()
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            beyondViewportPageCount = 1,
            key = { items[it].id },
        ) { page ->
            val item = items[page]
            val reported = if (page == pagerState.currentPage) playbackSize else null
            val contentW = reported?.first ?: item.width?.toFloat() ?: 0f
            val contentH = reported?.second ?: item.height?.toFloat() ?: 0f
            val fallback = if (item.videoUrl != null) VIDEO_FALLBACK_ASPECT else PHOTO_FALLBACK_ASPECT
            val aspect = contentAspectRatio(contentW, contentH, fallback)
            val fitted = fittedContentPx(
                boxW,
                boxH,
                if (contentW > 1f) contentW else aspect,
                if (contentH > 1f) contentH else 1f,
            )
            val mediaModifier = if (fitted.widthPx > 1f && fitted.heightPx > 1f) {
                Modifier.size(
                    with(density) { fitted.widthPx.toDp() },
                    with(density) { fitted.heightPx.toDp() },
                )
            } else {
                Modifier.fillMaxSize()
            }
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                PeekImage(
                    image = item.image,
                    contentDescription = item.contentDescription,
                    modifier = mediaModifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { chromeVisible = !chromeVisible },
                    ),
                    contentScale = ContentScale.Fit,
                )
                if (page == pagerState.currentPage && item.videoUrl != null && exoPlayer != null) {
                    VideoSurface(
                        exoPlayer = exoPlayer,
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT,
                        modifier = mediaModifier,
                    )
                }
            }
        }
        if (chromeVisible) {
            FullscreenChrome(
                showTransport = currentVideoUrl != null && exoPlayer != null,
                playing = playing,
                muted = muted,
                onTogglePlay = {
                    val player = exoPlayer ?: return@FullscreenChrome
                    val next = !player.playWhenReady
                    player.playWhenReady = next
                    playing = next
                },
                onToggleMute = {
                    val player = exoPlayer ?: return@FullscreenChrome
                    val nextMuted = player.volume > 0f
                    player.volume = if (nextMuted) 0f else 1f
                    muted = nextMuted
                },
                onExit = onBack,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 28.dp),
            )
        }
    }
}

@Composable
private fun FullscreenChrome(
    showTransport: Boolean,
    playing: Boolean,
    muted: Boolean,
    onTogglePlay: () -> Unit,
    onToggleMute: () -> Unit,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(24.dp))
            .background(Color.Black.copy(alpha = 0.55f))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showTransport) {
            ChromeButton(
                icon = if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                description = stringResource(if (playing) R.string.pause else R.string.play_video),
                onClick = onTogglePlay,
            )
            ChromeButton(
                icon = if (muted) Icons.AutoMirrored.Rounded.VolumeOff else Icons.AutoMirrored.Rounded.VolumeUp,
                description = stringResource(if (muted) R.string.unmute else R.string.mute),
                onClick = onToggleMute,
            )
        }
        ChromeButton(
            icon = Icons.Rounded.FullscreenExit,
            description = stringResource(R.string.exit_fullscreen),
            onClick = onExit,
        )
    }
}

@Composable
private fun ChromeButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(42.dp)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, tint = Color.White, modifier = Modifier.size(22.dp))
    }
}
