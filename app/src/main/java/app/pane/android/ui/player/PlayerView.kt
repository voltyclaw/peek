package app.pane.android.ui.player

import android.app.Activity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Forward5
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay5
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberStandardBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView as Media3PlayerView
import androidx.core.view.WindowCompat
import app.pane.android.R
import app.pane.android.ui.components.AuthorByline
import app.pane.android.ui.components.CaptionText
import app.pane.android.ui.components.CommentsSection
import app.pane.android.ui.components.PeekImage
import app.pane.android.ui.model.ViewerMediaItemUiModel
import app.pane.android.ui.model.ViewerPostUiModel
import app.pane.android.ui.model.ViewerUiState
import app.pane.android.ui.model.hasDownloadableMedia
import app.pane.android.ui.model.mediaItemsOrPrimary
import app.pane.android.ui.theme.GeistMono
import app.pane.android.ui.theme.PaneGround
import java.net.URI
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private const val REDDIT_MEDIA_USER_AGENT =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

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
        val window = (view.context as? Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        val previousLightStatusBars = controller?.isAppearanceLightStatusBars
        val previousLightNavigationBars = controller?.isAppearanceLightNavigationBars
        controller?.isAppearanceLightStatusBars = false
        controller?.isAppearanceLightNavigationBars = false
        onDispose {
            if (previousLightStatusBars != null) {
                controller.isAppearanceLightStatusBars = previousLightStatusBars
            }
            if (previousLightNavigationBars != null) {
                controller.isAppearanceLightNavigationBars = previousLightNavigationBars
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
            is ViewerUiState.Unavailable -> UnavailableMedia(onBack)
            is ViewerUiState.LoadFailed -> UnavailableMedia(onBack, uiState.reason)
            is ViewerUiState.Content -> MediaContent(
                post = uiState.post,
                isLoadingMoreComments = uiState.isLoadingMoreComments,
                initialMediaIndex = initialMediaIndex,
                onBack = onBack,
                onMore = onMore,
                onLoadMoreComments = onLoadMoreComments,
                onCopyLink = onCopyLink,
                onCopyMedia = onCopyMedia,
                onDownload = onDownload,
                onShare = onShare,
                onSharePost = onSharePost,
                onOpenInApp = onOpenInApp,
            )
        }
    }
}

@Composable
private fun BoxScopeLoadingBackButton(onBack: () -> Unit) {
    ChromeButton(
        icon = Icons.AutoMirrored.Rounded.ArrowBack,
        contentDescription = stringResource(R.string.back),
        onClick = onBack,
        modifier = Modifier.statusBarsPadding().padding(20.dp),
    )
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
            Spacer(Modifier.height(10.dp))
            Text(
                text = "${(uiState.progress * 100).roundToInt()}%",
                color = Color.White,
                style = TextStyle(
                    fontFamily = GeistMono,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
            )
        }
        BoxScopeLoadingBackButton(onBack)
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
        BoxScopeLoadingBackButton(onBack)
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@kotlin.OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MediaContent(
    post: ViewerPostUiModel,
    isLoadingMoreComments: Boolean,
    initialMediaIndex: Int,
    onBack: () -> Unit,
    onMore: () -> Unit,
    onLoadMoreComments: () -> Unit,
    onCopyLink: suspend (String) -> Unit,
    onCopyMedia: suspend (ViewerMediaItemUiModel) -> Unit,
    onDownload: suspend (List<ViewerMediaItemUiModel>) -> Unit,
    onShare: suspend (List<ViewerMediaItemUiModel>) -> Unit,
    onSharePost: suspend (String, String?) -> Unit,
    onOpenInApp: suspend (String) -> Unit,
) {
    val items = post.mediaItemsOrPrimary()
    val initialPage = initialMediaIndex.coerceIn(0, items.lastIndex)
    val pagerState = rememberPagerState(
        initialPage = initialPage,
        pageCount = items::size,
    )
    val currentItem = items[pagerState.currentPage]
    val currentVideoUrl = currentItem.videoUrl
    val context = LocalContext.current
    val exoPlayer = remember(currentVideoUrl) {
        currentVideoUrl?.let { videoUrl ->
            exoPlayerFor(context, videoUrl).apply {
                setMediaItem(MediaItem.fromUri(videoUrl))
                repeatMode = Player.REPEAT_MODE_ONE
                playWhenReady = true
                prepare()
            }
        }
    }
    DisposableEffect(exoPlayer) {
        onDispose { exoPlayer?.release() }
    }

    var isPlaying by remember(currentVideoUrl) { mutableStateOf(currentVideoUrl != null) }
    var positionMs by remember(currentVideoUrl) { mutableLongStateOf(0L) }
    var durationMs by remember(currentVideoUrl) { mutableLongStateOf(0L) }
    var isMuted by remember(currentVideoUrl) { mutableStateOf(false) }
    var resizeMode by remember(currentVideoUrl) {
        mutableIntStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT)
    }
    var controlsVisible by remember {
        mutableStateOf(playerShowsControlsOnOpen())
    }
    var appliedInitialChrome by remember { mutableStateOf(false) }
    var playbackSpeed by remember(currentVideoUrl) { mutableFloatStateOf(1f) }
    var contentWidthPx by remember(pagerState.currentPage) { mutableFloatStateOf(0f) }
    var contentHeightPx by remember(pagerState.currentPage) { mutableFloatStateOf(0f) }

    DisposableEffect(exoPlayer) {
        val listener = exoPlayer?.let { player ->
            object : Player.Listener {
                override fun onIsPlayingChanged(playing: Boolean) {
                    isPlaying = playing
                }

                override fun onEvents(player: Player, events: Player.Events) {
                    durationMs = player.duration.coerceAtLeast(0L)
                }

                override fun onVideoSizeChanged(videoSize: VideoSize) {
                    if (videoSize.width > 0 && videoSize.height > 0) {
                        contentWidthPx = videoSize.width.toFloat()
                        contentHeightPx = videoSize.height.toFloat()
                    }
                }
            }.also { listener ->
                player.addListener(listener)
                val size = player.videoSize
                if (size.width > 0 && size.height > 0) {
                    contentWidthPx = size.width.toFloat()
                    contentHeightPx = size.height.toFloat()
                }
            }
        }
        onDispose {
            if (listener != null) exoPlayer.removeListener(listener)
        }
    }

    LaunchedEffect(exoPlayer) {
        while (exoPlayer != null) {
            positionMs = exoPlayer.currentPosition.coerceAtLeast(0L)
            durationMs = exoPlayer.duration.coerceAtLeast(0L)
            delay(250)
        }
    }

    LaunchedEffect(pagerState.currentPage) {
        if (!appliedInitialChrome) {
            appliedInitialChrome = true
        } else if (playerShowsControlsAfterPageChange(items[pagerState.currentPage].videoUrl)) {
            controlsVisible = true
        }
    }

    LaunchedEffect(controlsVisible, isPlaying, currentVideoUrl) {
        if (controlsVisible && isPlaying && currentVideoUrl != null) {
            delay(3_000)
            controlsVisible = false
        }
    }

    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val bottomSheetState = rememberStandardBottomSheetState(
        initialValue = SheetValue.PartiallyExpanded,
    )
    val scaffoldState = rememberBottomSheetScaffoldState(
        bottomSheetState = bottomSheetState,
    )
    val sheetExpanded = bottomSheetState.currentValue == SheetValue.Expanded ||
        bottomSheetState.targetValue == SheetValue.Expanded
    BoxWithConstraints(Modifier.fillMaxSize()) {
    val sparePx = spareBelowPeekPx(
        constraints.maxWidth.toFloat(),
        constraints.maxHeight.toFloat(),
        contentWidthPx,
        contentHeightPx,
    )
    val spareBelow = sparePx > 0f
    val mediaFraction = if (spareBelow && constraints.maxHeight > 0) {
        ((constraints.maxHeight - sparePx) / constraints.maxHeight).coerceIn(0.2f, 1f)
    } else {
        1f
    }
    val sheetPeekHeight by animateDpAsState(
        targetValue = when {
            spareBelow -> with(density) { sparePx.toDp() }
            controlsVisible || sheetExpanded -> 200.dp
            else -> 0.dp
        },
        label = "sheetPeekHeight",
    )

    BottomSheetScaffold(
        scaffoldState = scaffoldState,
        sheetPeekHeight = sheetPeekHeight,
        sheetShape = RectangleShape,
        sheetDragHandle = {},
        sheetContainerColor = PaneGround,
        sheetShadowElevation = 12.dp,
        containerColor = Color.Black,
        sheetContent = {
            PostDetailsSheet(
                post = post,
                currentItem = currentItem,
                isLoadingMoreComments = isLoadingMoreComments,
                onLoadMoreComments = onLoadMoreComments,
                onCopyLink = onCopyLink,
                onCopyMedia = onCopyMedia,
                onDownload = onDownload,
                onShare = onShare,
                onSharePost = onSharePost,
                onOpenInApp = onOpenInApp,
            )
        },
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .raiseCommentsOnSwipeUp {
                    when (
                        commentsRaiseForSwipe(
                            commentsLowered = !controlsVisible && !spareBelow,
                            sheetExpanded = bottomSheetState.currentValue == SheetValue.Expanded ||
                                bottomSheetState.targetValue == SheetValue.Expanded,
                        )
                    ) {
                        CommentsRaise.ShowPeek -> controlsVisible = true
                        CommentsRaise.Expand -> scope.launch { bottomSheetState.expand() }
                        CommentsRaise.None -> Unit
                    }
                }
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { controlsVisible = !controlsVisible },
                ),
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = if (spareBelow) {
                    Modifier.align(Alignment.TopCenter).fillMaxWidth().fillMaxHeight(mediaFraction)
                } else {
                    Modifier.fillMaxSize()
                },
                beyondViewportPageCount = 1,
                key = { items[it].id },
            ) { page ->
                val item = items[page]
                Box(Modifier.fillMaxSize()) {
                    PeekImage(
                        image = item.image,
                        contentDescription = item.contentDescription,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = if (spareBelow) ContentScale.Fit else ContentScale.Crop,
                        alignment = if (spareBelow) Alignment.TopCenter else Alignment.Center,
                        onIntrinsicSize = { width, height ->
                            if (page == pagerState.currentPage && contentWidthPx == 0f) {
                                contentWidthPx = width
                                contentHeightPx = height
                            }
                        },
                    )
                    if (page == pagerState.currentPage && item.videoUrl != null && exoPlayer != null) {
                        VideoSurface(exoPlayer, resizeMode, Modifier.fillMaxSize())
                    }
                }
            }

            AnimatedVisibility(
                visible = controlsVisible,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.TopCenter),
            ) {
                TopChrome(
                    authorName = post.authorName,
                    page = pagerState.currentPage + 1,
                    pageCount = items.size,
                    onBack = onBack,
                    onMore = onMore,
                )
            }

            AnimatedVisibility(
                visible = controlsVisible && exoPlayer != null,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.Center),
            ) {
                CenterPlaybackControls(
                    isPlaying = isPlaying,
                    positionMs = positionMs,
                    durationMs = durationMs,
                    onSeekBack = {
                        exoPlayer?.seekTo((exoPlayer.currentPosition - 5_000L).coerceAtLeast(0L))
                    },
                    onTogglePlay = {
                        exoPlayer?.let { if (isPlaying) it.pause() else it.play() }
                    },
                    onSeekForward = {
                        exoPlayer?.seekTo(
                            (exoPlayer.currentPosition + 5_000L).coerceAtMost(durationMs),
                        )
                    },
                )
            }

            AnimatedVisibility(
                visible = controlsVisible,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .then(if (spareBelow) Modifier else Modifier.navigationBarsPadding())
                    .padding(bottom = if (spareBelow) with(density) { sparePx.toDp() } + 12.dp else 210.dp),
            ) {
                if (exoPlayer != null) {
                    BottomPlaybackControls(
                        isPlaying = isPlaying,
                        isMuted = isMuted,
                        positionMs = positionMs,
                        durationMs = durationMs,
                        playbackSpeed = playbackSpeed,
                        onSeek = { fraction ->
                            exoPlayer.seekTo((fraction * durationMs).toLong())
                        },
                        onTogglePlay = { if (isPlaying) exoPlayer.pause() else exoPlayer.play() },
                        onToggleMute = {
                            isMuted = !isMuted
                            exoPlayer.volume = if (isMuted) 0f else 1f
                        },
                        onCyclePlaybackSpeed = {
                            playbackSpeed = when (playbackSpeed) {
                                1f -> 1.5f
                                1.5f -> 2f
                                else -> 1f
                            }
                            exoPlayer.setPlaybackSpeed(playbackSpeed)
                        },
                        onToggleFullscreen = {
                            resizeMode = if (resizeMode == AspectRatioFrameLayout.RESIZE_MODE_FIT) {
                                AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                            } else {
                                AspectRatioFrameLayout.RESIZE_MODE_FIT
                            }
                        },
                    )
                } else {
                    PhotoMediaBar(
                        page = pagerState.currentPage + 1,
                        pageCount = items.size,
                    )
                }
            }
        }
    }
    }
}

@Composable
private fun CenterPlaybackControls(
    isPlaying: Boolean,
    positionMs: Long,
    durationMs: Long,
    onSeekBack: () -> Unit,
    onTogglePlay: () -> Unit,
    onSeekForward: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(28.dp),
    ) {
        Icon(
            imageVector = Icons.Rounded.Replay5,
            contentDescription = stringResource(R.string.seek_back_5),
            tint = Color.White,
            modifier = Modifier.size(34.dp).clickable(
                role = Role.Button,
                onClick = onSeekBack,
            ),
        )
        Box(
            modifier = Modifier
                .size(76.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.45f))
                .clickable(role = Role.Button, onClick = onTogglePlay),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = stringResource(R.string.play_video),
                tint = Color.White,
                modifier = Modifier.size(30.dp),
            )
        }
        Icon(
            imageVector = Icons.Rounded.Forward5,
            contentDescription = stringResource(R.string.seek_forward_5),
            tint = Color.White,
            modifier = Modifier.size(34.dp).clickable(
                enabled = durationMs > 0L && positionMs < durationMs,
                role = Role.Button,
                onClick = onSeekForward,
            ),
        )
    }
}

@Composable
private fun PhotoMediaBar(page: Int, pageCount: Int) {
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.55f))
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.photo_label),
            color = Color.White,
            style = TextStyle(
                fontFamily = GeistMono,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
            ),
        )
        Text(
            text = stringResource(R.string.carousel_position, page, pageCount),
            color = Color.White.copy(alpha = 0.78f),
            style = TextStyle(fontFamily = GeistMono, fontSize = 10.sp),
        )
    }
}

@Composable
private fun PostDetailsSheet(
    post: ViewerPostUiModel,
    currentItem: ViewerMediaItemUiModel,
    isLoadingMoreComments: Boolean,
    onLoadMoreComments: () -> Unit,
    onCopyLink: suspend (String) -> Unit,
    onCopyMedia: suspend (ViewerMediaItemUiModel) -> Unit,
    onDownload: suspend (List<ViewerMediaItemUiModel>) -> Unit,
    onShare: suspend (List<ViewerMediaItemUiModel>) -> Unit,
    onSharePost: suspend (String, String?) -> Unit,
    onOpenInApp: suspend (String) -> Unit,
) {
    val scrollState = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .verticalScroll(scrollState)
            .padding(horizontal = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        AuthorByline(
            post = post,
            onCopyLink = { onCopyLink(post.sourceUrl) },
            onCopyMedia = { onCopyMedia(currentItem) },
            onDownload = { onDownload(listOf(currentItem).filter(ViewerMediaItemUiModel::hasDownloadableMedia)) },
            onShare = { onShare(listOf(currentItem).filter(ViewerMediaItemUiModel::hasDownloadableMedia)) },
            onSharePost = { onSharePost(post.sourceUrl, post.title) },
            onOpenInApp = { onOpenInApp(post.sourceUrl) },
            canCopyMedia = currentItem.videoUrl == null && currentItem.hasDownloadableMedia(),
            canDownload = currentItem.hasDownloadableMedia(),
        )
        CaptionText(post)
        CommentsSection(
            post = post,
            isLoadingMore = isLoadingMoreComments,
            scrollOffset = scrollState.value,
            onLoadMore = onLoadMoreComments,
        )
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
private fun exoPlayerFor(context: android.content.Context, videoUrl: String): ExoPlayer {
    val builder = ExoPlayer.Builder(context)
    if (isRedditMediaUrl(videoUrl)) {
        val dataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(REDDIT_MEDIA_USER_AGENT)
            .setDefaultRequestProperties(mapOf("Referer" to "https://www.reddit.com/"))
            .setAllowCrossProtocolRedirects(true)
        builder.setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
    }
    return builder.build()
}

private fun isRedditMediaUrl(url: String): Boolean {
    val host = runCatching { URI(url).host }.getOrNull()?.lowercase(Locale.US) ?: return false
    return host == "v.redd.it" || host.endsWith(".redd.it") || host.endsWith(".redditmedia.com")
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun VideoSurface(
    exoPlayer: ExoPlayer,
    resizeMode: Int,
    modifier: Modifier = Modifier,
) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            Media3PlayerView(context).apply {
                player = exoPlayer
                useController = false
                setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                this.resizeMode = resizeMode
            }
        },
        update = { view ->
            view.player = exoPlayer
            view.resizeMode = resizeMode
        },
    )
}

@Composable
private fun TopChrome(
    authorName: String,
    page: Int,
    pageCount: Int,
    onBack: () -> Unit,
    onMore: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ChromeButton(
            Icons.AutoMirrored.Rounded.ArrowBack,
            stringResource(R.string.back),
            onBack,
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = authorName,
                color = Color.White,
                style = TextStyle(
                    fontFamily = GeistMono,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
            )
            if (pageCount > 1) {
                Text(
                    text = stringResource(R.string.carousel_position, page, pageCount),
                    color = Color.White.copy(alpha = 0.72f),
                    style = TextStyle(fontFamily = GeistMono, fontSize = 9.sp),
                )
            }
        }
        ChromeButton(
            Icons.Rounded.MoreHoriz,
            stringResource(R.string.more_options),
            onMore,
        )
    }
}

@Composable
private fun ChromeButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(42.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.4f))
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = Color.White,
            modifier = Modifier.size(22.dp),
        )
    }
}

@Composable
private fun BottomPlaybackControls(
    isPlaying: Boolean,
    isMuted: Boolean,
    positionMs: Long,
    durationMs: Long,
    playbackSpeed: Float,
    onSeek: (Float) -> Unit,
    onTogglePlay: () -> Unit,
    onToggleMute: () -> Unit,
    onCyclePlaybackSpeed: () -> Unit,
    onToggleFullscreen: () -> Unit,
) {
    val progressFraction = if (durationMs > 0) {
        (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Slider(
            value = progressFraction,
            onValueChange = onSeek,
            modifier = Modifier.fillMaxWidth().height(20.dp),
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = Color(0xFFD9E8D4),
                inactiveTrackColor = Color.White.copy(alpha = 0.3f),
            ),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.45f))
                        .clickable(role = Role.Button, onClick = onTogglePlay),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = if (isPlaying) {
                            Icons.Rounded.Pause
                        } else {
                            Icons.Rounded.PlayArrow
                        },
                        contentDescription = stringResource(R.string.play_video),
                        tint = Color.White,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Text(
                    text = "${formatMillis(positionMs)} / ${formatMillis(durationMs)}",
                    color = Color.White,
                    style = TextStyle(
                        fontFamily = GeistMono,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                    ),
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    text = "${formatSpeed(playbackSpeed)}x",
                    color = Color.White,
                    style = TextStyle(
                        fontFamily = GeistMono,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.45f))
                        .clickable(
                            role = Role.Button,
                            onClickLabel = stringResource(
                                R.string.playback_speed,
                                "${formatSpeed(playbackSpeed)}x",
                            ),
                            onClick = onCyclePlaybackSpeed,
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
                Icon(
                    imageVector = if (isMuted) {
                        Icons.AutoMirrored.Rounded.VolumeOff
                    } else {
                        Icons.AutoMirrored.Rounded.VolumeUp
                    },
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier
                        .size(20.dp)
                        .clickable(role = Role.Button, onClick = onToggleMute),
                )
                Icon(
                    imageVector = Icons.Rounded.Fullscreen,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier
                        .size(19.dp)
                        .clickable(role = Role.Button, onClick = onToggleFullscreen),
                )
            }
        }
    }
}

/**
 * Upward swipe raises comments the same way a tap does. Sideways motion is left for the gallery pager.
 */
@Composable
private fun Modifier.raiseCommentsOnSwipeUp(onRaise: () -> Unit): Modifier {
    val currentOnRaise by rememberUpdatedState(onRaise)
    val touchSlop = LocalViewConfiguration.current.touchSlop
    val thresholdPx = with(LocalDensity.current) { 56.dp.toPx() }
    return pointerInput(touchSlop, thresholdPx) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            var totalDx = 0f
            var totalDy = 0f
            var trackingUp = false
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed) {
                    if (trackingUp) change.consume()
                    if (swipeRaisesComments(totalDx, totalDy, thresholdPx)) currentOnRaise()
                    break
                }
                val delta = change.position - change.previousPosition
                totalDx += delta.x
                totalDy += delta.y
                if (
                    !trackingUp &&
                    kotlin.math.abs(totalDy) > touchSlop &&
                    kotlin.math.abs(totalDx) <= kotlin.math.abs(totalDy) &&
                    totalDy < 0f
                ) {
                    trackingUp = true
                }
                if (trackingUp) change.consume()
            }
        }
    }
}

private fun formatMillis(milliseconds: Long): String {
    val totalSeconds = (milliseconds / 1_000).coerceAtLeast(0L)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.US, "%d:%02d", minutes, seconds)
}

private fun formatSpeed(speed: Float): String =
    if (speed == speed.toInt().toFloat()) speed.toInt().toString() else speed.toString()
