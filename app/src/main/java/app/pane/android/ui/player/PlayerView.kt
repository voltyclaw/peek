package app.pane.android.ui.player

import android.app.Activity
import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import app.pane.android.R
import app.pane.android.ui.components.PeekImage
import app.pane.android.ui.media.PHOTO_FALLBACK_ASPECT
import app.pane.android.ui.media.VIDEO_FALLBACK_ASPECT
import app.pane.android.ui.media.VideoPlaybackQuality
import app.pane.android.ui.media.VideoQuality
import app.pane.android.ui.media.VideoSurface
import app.pane.android.ui.media.contentAspectRatio
import app.pane.android.ui.media.displayVideoSize
import app.pane.android.ui.media.CONTROLS_AUTO_HIDE_MS
import app.pane.android.ui.media.ManualOrientationLock
import app.pane.android.ui.media.RotateControlLabel
import app.pane.android.ui.media.SurfaceGesture
import app.pane.android.ui.media.SurfaceGestureAction
import app.pane.android.ui.media.VideoSurfaceKind
import app.pane.android.ui.media.activityOrientation
import app.pane.android.ui.media.confirmedMediaTaps
import app.pane.android.ui.media.controlsAutoHide
import app.pane.android.ui.media.nextManualLock
import app.pane.android.ui.media.orientationRequest
import app.pane.android.ui.media.rememberPlaybackSession
import app.pane.android.ui.media.rotateControlLabel
import app.pane.android.ui.media.rememberTouchExplorationEnabled
import app.pane.android.ui.media.surfaceGestureAction
import app.pane.android.ui.viewer.failureCopyRes
import app.pane.android.ui.media.fittedContentPx
import kotlinx.coroutines.delay
import app.pane.android.ui.model.ViewerMediaItemUiModel
import app.pane.android.ui.model.ViewerPostUiModel
import app.pane.android.ui.model.ViewerUiState
import app.pane.android.ui.model.gallery
import app.pane.android.ui.model.mediaItemsOrPrimary

@Composable
fun PlayerView(
    uiState: ViewerUiState,
    initialMediaIndex: Int,
    ownerId: String = "",
    onBack: () -> Unit,
    onMore: () -> Unit,
    modifier: Modifier = Modifier,
    videoQuality: VideoQuality = VideoQuality.Auto,
    startMuted: () -> Boolean = { true },
    onMutedChange: (Boolean) -> Unit = {},
    onLoadMoreComments: () -> Unit = {},
    onCopyLink: suspend (String) -> Unit = {},
    onCopyMedia: suspend (ViewerMediaItemUiModel) -> Unit = {},
    onDownload: suspend (List<ViewerMediaItemUiModel>) -> Unit = {},
    onShare: suspend (List<ViewerMediaItemUiModel>) -> Unit = {},
    onSharePost: suspend (String, String?) -> Unit = { _, _ -> },
    onOpenInApp: suspend (String) -> Unit = {},
    onLeave: () -> Unit = onBack,
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
        val previousOrientation = activity?.requestedOrientation
        onDispose {
            if (previousLightStatusBars != null) {
                controller.isAppearanceLightStatusBars = previousLightStatusBars
            }
            if (previousLightNavigationBars != null) {
                controller.isAppearanceLightNavigationBars = previousLightNavigationBars
            }
            if (previousOrientation != null) {
                activity.requestedOrientation = previousOrientation
            }
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
            is ViewerUiState.Unavailable -> UnavailableMedia(onBack, url = uiState.url)
            is ViewerUiState.LoadFailed -> UnavailableMedia(onBack, url = uiState.url, reason = uiState.reason, failed = true)
            is ViewerUiState.Content -> MediaContent(
                post = uiState.post,
                ownerId = ownerId,
                initialMediaIndex = initialMediaIndex,
                videoQuality = videoQuality,
                startMuted = startMuted,
                onMutedChange = onMutedChange,
                onBack = onBack,
                onLeave = onLeave,
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
private fun UnavailableMedia(onBack: () -> Unit, url: String = "", reason: String = "", failed: Boolean = false) {
    val context = LocalContext.current
    val knownSource = remember(url) {
        app.pane.android.ui.actions.sourceAppNameRes(url)
    }
    val title = stringResource(if (failed) R.string.couldnt_open else R.string.pane_cant_show)
    val sourceName = knownSource?.let { stringResource(it) }
    val body = when {
        failed -> stringResource(failureCopyRes(reason))
        sourceName != null -> stringResource(R.string.link_isnt_public_post, sourceName)
        else -> stringResource(R.string.cant_show_link_here)
    }
    Box(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.align(Alignment.Center).padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = title,
                color = Color.White,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = body,
                color = Color.White.copy(alpha = 0.72f),
                textAlign = TextAlign.Center,
            )
        }
        LoadingBackButton(onBack)
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun MediaContent(
    post: ViewerPostUiModel,
    ownerId: String,
    initialMediaIndex: Int,
    videoQuality: VideoQuality,
    startMuted: () -> Boolean,
    onMutedChange: (Boolean) -> Unit,
    onBack: () -> Unit,
    onLeave: () -> Unit,
) {
    val items = post.gallery(ownerId)
    val initialPage = initialMediaIndex.coerceIn(0, items.lastIndex)
    val pagerState = rememberPagerState(
        initialPage = initialPage,
        pageCount = items::size,
    )
    val currentItem = items[pagerState.currentPage]
    var sessionChoice by rememberSaveable { mutableStateOf("") }
    val playbackUrl = when {
        sessionChoice == "auto" -> VideoPlaybackQuality.urlFor(
            currentItem.videoSources,
            VideoQuality.Auto,
            currentItem.videoUrl,
        )
        sessionChoice.isNotBlank() -> sessionChoice
        else -> VideoPlaybackQuality.urlFor(
            currentItem.videoSources,
            videoQuality,
            currentItem.videoUrl,
        )
    }
    val qualityOptions = VideoPlaybackQuality.renditions(currentItem.videoSources)
    val autoSelected = sessionChoice == "auto" || (sessionChoice.isEmpty() && videoQuality == VideoQuality.Auto)
    val session = rememberPlaybackSession()
    val exoPlayer = playbackUrl?.let { url ->
        session.acquire(post.sourceUrl, currentItem.id, url, freshMuted = startMuted()).player.exo
    }
    LaunchedEffect(sessionChoice) {
        if (sessionChoice.isEmpty()) return@LaunchedEffect
        val url = playbackUrl ?: return@LaunchedEffect
        session.switchUrl(post.sourceUrl, currentItem.id, url)
    }
    var resumeAfterPhoto by remember(post.sourceUrl) { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(playbackUrl) {
        val held = session.player ?: return@LaunchedEffect
        if (playbackUrl == null) {
            if (resumeAfterPhoto == null) resumeAfterPhoto = held.playWhenReady
            held.playWhenReady = false
        } else {
            val resume = resumeAfterPhoto ?: return@LaunchedEffect
            held.playWhenReady = resume
            resumeAfterPhoto = null
        }
    }
    if (exoPlayer != null) {
        SideEffect { exoPlayer.repeatMode = Player.REPEAT_MODE_OFF }
    }
    val initialMuted = exoPlayer?.let { it.volume <= 0.001f } ?: startMuted()
    val density = LocalDensity.current
    val touchExploration = rememberTouchExplorationEnabled()
    var chromeVisible by remember(currentItem.id) { mutableStateOf(playbackUrl != null || touchExploration) }
    var playbackSize by remember(playbackUrl) { mutableStateOf<Pair<Float, Float>?>(null) }
    var playing by remember(exoPlayer) { mutableStateOf(exoPlayer?.playWhenReady == true) }
    var muted by remember(exoPlayer) { mutableStateOf(initialMuted) }
    var notice by remember { mutableStateOf<String?>(null) }
    val mutedLabel = stringResource(R.string.playback_muted)
    val unmutedLabel = stringResource(R.string.playback_unmuted)
    val qualityNotice = stringResource(R.string.playback_quality_toast)
    var positionMs by remember(exoPlayer) {
        mutableLongStateOf(exoPlayer?.currentPosition?.coerceAtLeast(0L) ?: 0L)
    }
    var durationMs by remember(exoPlayer) { mutableLongStateOf(0L) }
    var scrubbing by remember(exoPlayer) { mutableStateOf(false) }
    var ended by remember(exoPlayer) {
        mutableStateOf(exoPlayer?.playbackState == Player.STATE_ENDED)
    }
    LaunchedEffect(notice) {
        if (notice != null) {
            delay(1_200)
            notice = null
        }
    }
    val hostView = LocalView.current
    val deviceLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    var manualLock by remember { mutableStateOf(ManualOrientationLock.None) }
    val orientation = orientationRequest(manualLock)
    LaunchedEffect(orientation) {
        val activity = hostView.context as? Activity ?: return@LaunchedEffect
        activity.requestedOrientation = activityOrientation(orientation)
    }
    val rotateLabel = stringResource(
        when (rotateControlLabel(manualLock, deviceLandscape)) {
            RotateControlLabel.ToLandscape -> R.string.rotate_to_landscape
            RotateControlLabel.ToPortrait -> R.string.rotate_to_portrait
        },
    )
    LaunchedEffect(touchExploration) {
        if (touchExploration) chromeVisible = true
    }
    LaunchedEffect(chromeVisible, playing, scrubbing, playbackUrl, ended, touchExploration) {
        if (
            chromeVisible &&
            playbackUrl != null &&
            !ended &&
            controlsAutoHide(playing, scrubbing, touchExploration)
        ) {
            delay(CONTROLS_AUTO_HIDE_MS)
            chromeVisible = false
        }
    }
    LaunchedEffect(exoPlayer, muted) {
        val player = exoPlayer ?: return@LaunchedEffect
        player.volume = if (muted) 0f else player.volume.takeIf { it > 0.001f } ?: 1f
    }
    LaunchedEffect(exoPlayer, scrubbing) {
        val player = exoPlayer ?: return@LaunchedEffect
        while (true) {
            if (!scrubbing) {
                val duration = player.duration
                durationMs = if (duration > 0L) duration else 0L
                positionMs = player.currentPosition.coerceAtLeast(0L)
            }
            delay(200)
        }
    }
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
                override fun onPlaybackStateChanged(playbackState: Int) {
                    val finished = playbackState == Player.STATE_ENDED
                    ended = finished
                    if (finished) chromeVisible = true
                }
            }
            player.addListener(listener)
            onDispose { player.removeListener(listener) }
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .confirmedMediaTaps(
                onSingleTapConfirmed = {
                    if (
                        surfaceGestureAction(VideoSurfaceKind.Fullscreen, SurfaceGesture.SingleTap)
                            == SurfaceGestureAction.ToggleControls
                    ) {
                        chromeVisible = !chromeVisible
                    }
                },
                onDoubleTap = {
                    when (surfaceGestureAction(VideoSurfaceKind.Fullscreen, SurfaceGesture.DoubleTap)) {
                        SurfaceGestureAction.ExitFullscreen -> onBack()
                        SurfaceGestureAction.EnterFullscreen -> Unit
                        SurfaceGestureAction.ToggleControls -> chromeVisible = !chromeVisible
                    }
                },
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
            val pageVideo = if (page == pagerState.currentPage) {
                playbackUrl
            } else {
                VideoPlaybackQuality.urlFor(item.videoSources, videoQuality, item.videoUrl)
            }
            val fallback = if (pageVideo != null) VIDEO_FALLBACK_ASPECT else PHOTO_FALLBACK_ASPECT
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
                    modifier = mediaModifier,
                    contentScale = ContentScale.Fit,
                )
                if (page == pagerState.currentPage && pageVideo != null && exoPlayer != null) {
                    VideoSurface(
                        exoPlayer = exoPlayer,
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT,
                        modifier = mediaModifier,
                    )
                }
            }
        }
        if (chromeVisible && playbackUrl != null) {
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(48.dp)
                    .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.55f), Color.Transparent))),
            )
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(48.dp)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.72f)))),
            )
        }
        notice?.let { message ->
            Text(
                text = message,
                modifier = Modifier
                    .align(Alignment.Center)
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
                    .background(Color.Black.copy(alpha = 0.72f))
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                color = Color.White,
                style = TextStyle(fontSize = 13.sp),
            )
        }
        if (chromeVisible) {
            FullscreenChrome(
                showTransport = playbackUrl != null && exoPlayer != null,
                playing = playing,
                muted = muted,
                qualityOptions = qualityOptions,
                selectedUrl = playbackUrl,
                autoSelected = autoSelected,
                onQuality = { option ->
                    sessionChoice = if (option.auto) "auto" else option.url
                    notice = qualityNotice.format(option.label.ifBlank { option.quality.name })
                },
                positionMs = positionMs,
                durationMs = durationMs,
                onTogglePlay = {
                    val player = exoPlayer ?: return@FullscreenChrome
                    val next = !player.playWhenReady
                    player.playWhenReady = next
                    playing = next
                },
                onToggleMute = {
                    val nextMuted = !muted
                    muted = nextMuted
                    onMutedChange(nextMuted)
                    notice = if (nextMuted) mutedLabel else unmutedLabel
                },
                onSeek = { fraction ->
                    val player = exoPlayer ?: return@FullscreenChrome
                    val duration = player.duration
                    if (duration <= 0L) return@FullscreenChrome
                    scrubbing = true
                    durationMs = duration
                    val target = seekPositionMs(duration, fraction)
                    positionMs = target
                    player.seekTo(target)
                },
                onSeekFinished = { scrubbing = false },
                onRotate = { manualLock = nextManualLock(manualLock, deviceLandscape) },
                rotateLabel = rotateLabel,
                onExit = onBack,
                modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 8.dp),
            )
        }
        if (ended) {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
                    .background(Color.Black.copy(alpha = 0.93f))
                    .padding(horizontal = 18.dp, vertical = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = stringResource(R.string.video_done),
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    style = TextStyle(fontSize = 14.sp),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(
                        text = stringResource(R.string.leave),
                        modifier = Modifier
                            .clickable(role = Role.Button, onClick = onLeave)
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        color = Color.White,
                        style = TextStyle(fontSize = 14.sp),
                    )
                    Text(
                        text = stringResource(R.string.replay),
                        modifier = Modifier
                            .clickable(role = Role.Button, onClick = {
                                val player = exoPlayer ?: return@clickable
                                ended = false
                                player.seekTo(0)
                                player.playWhenReady = true
                            })
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        color = Color.White,
                        style = TextStyle(fontSize = 14.sp),
                    )
                }
            }
        }
    }
}

@Composable
private fun FullscreenChrome(
    showTransport: Boolean,
    playing: Boolean,
    muted: Boolean,
    qualityOptions: List<app.pane.android.ui.media.VideoQualityOption>,
    selectedUrl: String?,
    autoSelected: Boolean,
    onQuality: (app.pane.android.ui.media.VideoQualityOption) -> Unit,
    positionMs: Long,
    durationMs: Long,
    onTogglePlay: () -> Unit,
    onToggleMute: () -> Unit,
    onSeek: (Float) -> Unit,
    onSeekFinished: () -> Unit,
    onRotate: () -> Unit,
    rotateLabel: String,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrubLabel = stringResource(R.string.playback_position)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {},
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (showTransport) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    text = formatPlaybackClock(positionMs),
                    color = Color.White,
                    style = TextStyle(fontSize = 12.sp),
                )
                Text(
                    text = formatPlaybackClock(durationMs),
                    color = Color.White.copy(alpha = 0.72f),
                    style = TextStyle(fontSize = 12.sp),
                )
            }
            Slider(
                value = playbackFraction(positionMs, durationMs),
                onValueChange = onSeek,
                onValueChangeFinished = onSeekFinished,
                enabled = durationMs > 0L,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = scrubLabel },
                colors = SliderDefaults.colors(
                    thumbColor = Color(0xFFF4EFEA),
                    activeTrackColor = Color(0xFF8A827A),
                    inactiveTrackColor = Color(0xFF262018),
                    disabledThumbColor = Color(0xFF8A827A),
                    disabledActiveTrackColor = Color(0xFF8A827A),
                    disabledInactiveTrackColor = Color(0xFF262018),
                ),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier
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
                    if (qualityOptions.isNotEmpty()) {
                        QualityButton(
                            options = qualityOptions,
                            selectedUrl = selectedUrl,
                            autoSelected = autoSelected,
                            onQuality = onQuality,
                        )
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            Row(
                modifier = Modifier
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(24.dp))
                    .background(Color.Black.copy(alpha = 0.55f))
                    .padding(horizontal = 4.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ChromeButton(
                    icon = Icons.Rounded.ScreenRotation,
                    description = rotateLabel,
                    onClick = onRotate,
                    tag = app.pane.android.ui.PaneTestTags.PLAYER_ROTATE,
                )
                ChromeButton(
                    icon = Icons.Rounded.FullscreenExit,
                    description = stringResource(R.string.exit_fullscreen),
                    onClick = onExit,
                    tag = app.pane.android.ui.PaneTestTags.PLAYER_EXIT_FULLSCREEN,
                )
            }
        }
    }
}

@Composable
private fun QualityButton(
    options: List<app.pane.android.ui.media.VideoQualityOption>,
    selectedUrl: String?,
    autoSelected: Boolean,
    onQuality: (app.pane.android.ui.media.VideoQualityOption) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        ChromeButton(
            icon = Icons.Rounded.Settings,
            description = stringResource(R.string.playback_quality),
            onClick = { open = true },
        )
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            containerColor = Color(0xFF191412),
        ) {
            options.forEach { option ->
                val checked = if (option.auto) {
                    autoSelected
                } else {
                    !autoSelected && option.url == selectedUrl
                }
                DropdownMenuItem(
                    text = {
                        Text(
                            text = option.label.ifBlank { option.quality.name },
                            color = Color.White,
                            style = TextStyle(fontSize = 14.sp),
                        )
                    },
                    onClick = {
                        open = false
                        onQuality(option)
                    },
                    trailingIcon = if (checked) {
                        { Icon(Icons.Rounded.Check, contentDescription = null, tint = Color.White) }
                    } else {
                        null
                    },
                )
            }
        }
    }
}

@Composable
private fun ChromeButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    tag: String? = null,
) {
    Box(
        modifier = Modifier
            .then(if (tag != null) Modifier.testTag(tag) else Modifier)
            .size(42.dp)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, tint = Color.White, modifier = Modifier.size(22.dp))
    }
}
