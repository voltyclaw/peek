package app.pane.android.ui.viewer

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.pane.android.R
import app.pane.android.ui.actions.openInAppLabelRes
import app.pane.android.ui.actions.sourceMarkRes
import app.pane.android.ui.components.AuthorByline
import app.pane.android.ui.components.AuthorThreadSection
import app.pane.android.ui.components.CaptionText
import app.pane.android.ui.components.CommentsSection
import app.pane.android.ui.components.PeekImage
import app.pane.android.ui.media.MAX_FRAMED_MEDIA_HEIGHT
import app.pane.android.ui.media.MutedInlineVideo
import app.pane.android.ui.media.VideoPlaybackQuality
import app.pane.android.ui.media.VideoQuality
import app.pane.android.ui.media.PHOTO_FALLBACK_ASPECT
import app.pane.android.ui.media.VIDEO_FALLBACK_ASPECT
import app.pane.android.ui.media.contentAspectRatio
import app.pane.android.ui.media.fittedContentPx
import app.pane.android.ui.components.PaneLockup
import app.pane.android.ui.model.UiImage
import app.pane.android.ui.model.ViewerPostUiModel
import app.pane.android.ui.model.ViewerUiState
import app.pane.android.ui.model.ViewerMediaItemUiModel
import app.pane.android.ui.model.hasDownloadableMedia
import app.pane.android.ui.model.mediaItemsOrPrimary
import app.pane.android.ui.theme.Geist
import app.pane.android.ui.theme.GeistMono
import app.pane.android.ui.theme.Inter
import app.pane.android.ui.theme.PaneAccent
import app.pane.android.ui.theme.PaneBorder
import app.pane.android.ui.theme.PaneChip
import app.pane.android.ui.theme.PaneFill
import app.pane.android.ui.theme.PaneGround
import app.pane.android.ui.theme.PaneOnFill
import app.pane.android.ui.theme.PaneInk
import app.pane.android.ui.theme.PaneMuted
import app.pane.android.ui.theme.PaneSecondary
import app.pane.android.ui.theme.PaneTile
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

@Composable
fun ViewerView(
    uiState: ViewerUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onOpenMedia: (Int) -> Unit,
    modifier: Modifier = Modifier,
    onLoadMoreComments: () -> Unit = {},
    onCopyLink: suspend (String) -> Unit = {},
    onCopyMedia: suspend (ViewerMediaItemUiModel) -> Unit = {},
    onDownload: suspend (List<ViewerMediaItemUiModel>) -> Unit = {},
    onShare: suspend (List<ViewerMediaItemUiModel>) -> Unit = {},
    onSharePost: suspend (String, String?) -> Unit = { _, _ -> },
    onOpenInApp: suspend (String) -> Unit = {},
    onMediaMeasured: (Float, Float) -> Unit = { _, _ -> },
    videoQuality: VideoQuality = VideoQuality.Auto,
    startMuted: () -> Boolean = { true },
    onMutedChange: (Boolean) -> Unit = {},
    onLeave: () -> Unit = {},
    note: String = "",
    onSaveNote: (String) -> Unit = {},
    onOpenOutbound: (String) -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    Box(modifier = modifier.fillMaxSize().background(PaneGround), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier.widthIn(max = 390.dp).fillMaxWidth().fillMaxHeight(),
        ) {
            when (uiState) {
                is ViewerUiState.Loading -> LoadingViewer(uiState.progress, uiState.message, onBack, onRefresh)
                is ViewerUiState.Unavailable -> UnavailableViewer(
                    url = uiState.url,
                    onBack = onBack,
                    onOpen = { scope.launch { onOpenInApp(uiState.url) } },
                    onShare = { scope.launch { onSharePost(uiState.url, null) } },
                    onCopy = { scope.launch { onCopyLink(uiState.url) } },
                )
                is ViewerUiState.LoadFailed -> LoadFailedViewer(
                    url = uiState.url,
                    reason = uiState.reason,
                    onBack = onBack,
                    onRetry = onRefresh,
                    onOpen = { scope.launch { onOpenInApp(uiState.url) } },
                    onShare = { scope.launch { onSharePost(uiState.url, null) } },
                    onCopy = { scope.launch { onCopyLink(uiState.url) } },
                )
                is ViewerUiState.Content -> ViewerContent(
                    post = uiState.post,
                    isLoadingMoreComments = uiState.isLoadingMoreComments,
                    onBack = onBack,
                    onRefresh = onRefresh,
                    onLoadMoreComments = onLoadMoreComments,
                    onOpenMedia = onOpenMedia,
                    onCopyLink = onCopyLink,
                    onCopyMedia = onCopyMedia,
                    onDownload = onDownload,
                    onShare = onShare,
                    onSharePost = onSharePost,
                    onOpenInApp = onOpenInApp,
                    onMediaMeasured = onMediaMeasured,
                    videoQuality = videoQuality,
                    startMuted = startMuted,
                    onMutedChange = onMutedChange,
                    onLeave = onLeave,
                    note = note,
                    onSaveNote = onSaveNote,
                    onOpenOutbound = onOpenOutbound,
                )
            }
        }
    }
}

@Composable
private fun ColumnScope.ViewerContent(
    post: ViewerPostUiModel,
    isLoadingMoreComments: Boolean,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onLoadMoreComments: () -> Unit,
    onOpenMedia: (Int) -> Unit,
    onCopyLink: suspend (String) -> Unit,
    onCopyMedia: suspend (ViewerMediaItemUiModel) -> Unit,
    onDownload: suspend (List<ViewerMediaItemUiModel>) -> Unit,
    onShare: suspend (List<ViewerMediaItemUiModel>) -> Unit,
    onSharePost: suspend (String, String?) -> Unit,
    onOpenInApp: suspend (String) -> Unit,
    onMediaMeasured: (Float, Float) -> Unit,
    videoQuality: VideoQuality,
    startMuted: () -> Boolean,
    onMutedChange: (Boolean) -> Unit,
    onLeave: () -> Unit,
    note: String,
    onSaveNote: (String) -> Unit,
    onOpenOutbound: (String) -> Unit,
) {
    val host = displayHost(post.sourceUrl)
    val openLabel = stringResource(openInAppLabelRes(post.sourceUrl))
    val sourceMark = sourceMarkRes(post.sourceUrl)
    val scope = rememberCoroutineScope()
    var overflow by remember { mutableStateOf(false) }
    var editingNote by remember { mutableStateOf(false) }
    var videoPaused by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()
    val items = post.mediaItemsOrPrimary()
    val pagerState = rememberPagerState(
        initialPage = post.initialMediaIndex.coerceIn(0, items.lastIndex.coerceAtLeast(0)),
        pageCount = items::size,
    )
    val currentItem = items.getOrNull(pagerState.currentPage)
    val hasMedia = items.any(::hasVisualMedia)
    val outbound = outboundLink(post.title, post.sourceUrl)
    val linkCard = outbound != null && hasMedia && currentItem?.videoUrl == null && post.authorThread.size < 2
    val reddit = host.contains("reddit.com")
    val textOnly = !hasMedia && post.authorThread.size < 2 && !reddit
    val overMedia = hasMedia && !linkCard && post.authorThread.size < 2
    Box(Modifier.fillMaxWidth().weight(1f)) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(scrollState).padding(bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (overMedia) {
                Box(Modifier.fillMaxWidth()) {
                    MediaCanvas(
                        post,
                        items,
                        pagerState,
                        onOpenMedia,
                        onMediaMeasured,
                        videoQuality,
                        startMuted,
                        onMutedChange,
                        videoPaused,
                        { videoPaused = !videoPaused },
                    )
                    Box(
                        Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(72.dp).background(
                            Brush.verticalGradient(listOf(Color.Transparent, PaneGround)),
                        ),
                    )
                    ViewerTopBar(onBack, onRefresh, overMedia = true)
                }
            } else {
                ViewerTopBar(onBack, onRefresh, overMedia = false)
            }
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                val showAuthor = post.authorName.isNotBlank() || post.authorMetadata.isNotBlank()
                if (reddit) {
                    RedditBody(post)
                    if (outbound != null && linkCard) {
                        LinkPreviewCard(outbound, currentItem, onOpenOutbound)
                    }
                } else {
                    if (showAuthor) AuthorCaption(post)
                    if (post.authorThread.size >= 2) {
                        AuthorThreadSection(post, if (hasMedia) currentItem?.image else null)
                    } else if (outbound != null && linkCard) {
                        CaptionText(post)
                        LinkPreviewCard(outbound, currentItem, onOpenOutbound)
                    } else if (textOnly) {
                        Box(Modifier.padding(vertical = 8.dp).width(48.dp).height(1.dp).background(PaneBorder))
                        CaptionText(post, large = true)
                    } else {
                        CaptionText(post)
                    }
                }
                if (overMedia && post.authorThread.size < 2 && !reddit) {
                    // caption already placed above for the image/video path
                }
                CommentsSection(
                    post = post,
                    isLoadingMore = isLoadingMoreComments,
                    scrollOffset = scrollState.value,
                    onLoadMore = onLoadMoreComments,
                    host = host,
                )
            }
        }
    }
    val canDownload = items.any(ViewerMediaItemUiModel::hasDownloadableMedia)
    ViewerBottomBar(
        openLabel = openLabel,
        onShare = { scope.launch { onSharePost(post.sourceUrl, post.title) } },
        onOverflow = { overflow = true },
        onOpen = { scope.launch { onOpenInApp(post.sourceUrl) } },
        sourceMark = sourceMark,
    )
    if (overflow) {
        OverflowSheet(
            contextLine = listOf(post.authorName, host).filter { it.isNotBlank() }.joinToString(" · "),
            canDownload = canDownload,
            onDismiss = { overflow = false },
            onShare = {
                overflow = false
                scope.launch { onSharePost(post.sourceUrl, post.title) }
            },
            onCopyLink = {
                overflow = false
                scope.launch { onCopyLink(post.sourceUrl) }
            },
            onDownload = {
                overflow = false
                scope.launch { onDownload(items.filter(ViewerMediaItemUiModel::hasDownloadableMedia)) }
            },
            onAddNote = {
                overflow = false
                editingNote = true
            },
        )
    }
    if (editingNote) {
        NoteEditorDialog(
            initial = note,
            onDismiss = { editingNote = false },
            onSave = {
                onSaveNote(it)
                editingNote = false
            },
        )
    }
}

@Composable
private fun ViewerHeader(isVideo: Boolean, onBack: () -> Unit, onRefresh: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().height(50.dp).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Box(
            modifier = Modifier.size(48.dp).offset(x = (-8).dp).clip(CircleShape).clickable(role = Role.Button, onClick = onBack),
            contentAlignment = Alignment.CenterStart,
        ) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.back), tint = PaneInk, modifier = Modifier.size(22.dp))
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            Text(
                text = if (isVideo) "▶" else "+",
                color = PaneMuted,
                style = TextStyle(fontFamily = Inter, fontSize = if (isVideo) 10.sp else 15.sp, fontWeight = FontWeight.Bold),
            )
            Text(
                text = stringResource(if (isVideo) R.string.video_preview else R.string.viewing_post),
                color = PaneInk,
                style = TextStyle(fontFamily = GeistMono, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.2.sp),
            )
        }
        Box(
            modifier = Modifier.size(48.dp).offset(x = 8.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onRefresh),
            contentAlignment = Alignment.CenterEnd,
        ) {
            Icon(Icons.Rounded.Refresh, contentDescription = stringResource(R.string.refresh), tint = PaneInk, modifier = Modifier.size(21.dp))
        }
    }
}

private fun hasVisualMedia(item: ViewerMediaItemUiModel): Boolean {
    if (item.videoUrl != null) return true
    return when (val image = item.image) {
        is UiImage.Resource -> true
        is UiImage.Url -> image.value.isNotBlank()
    }
}

@Composable
private fun MediaCanvas(
    post: ViewerPostUiModel,
    items: List<ViewerMediaItemUiModel>,
    pagerState: PagerState,
    onOpenMedia: (Int) -> Unit,
    onMediaMeasured: (Float, Float) -> Unit,
    videoQuality: VideoQuality,
    startMuted: () -> Boolean,
    onMutedChange: (Boolean) -> Unit,
    videoPaused: Boolean = false,
    onToggleVideo: () -> Unit = {},
) {
    val coroutineScope = rememberCoroutineScope()
    val current = items[pagerState.currentPage]
    var measured by remember { mutableStateOf<Map<String, Pair<Float, Float>>>(emptyMap()) }
    val known = measured[current.id]
    val knownW = known?.first ?: current.width?.toFloat() ?: 0f
    val knownH = known?.second ?: current.height?.toFloat() ?: 0f
    val frameAspect = contentAspectRatio(
        knownW,
        knownH,
        if (current.videoUrl != null) VIDEO_FALLBACK_ASPECT else PHOTO_FALLBACK_ASPECT,
    )
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val density = LocalDensity.current
        val boxW = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val boxH = (boxW / frameAspect)
            .coerceAtMost(with(density) { MAX_FRAMED_MEDIA_HEIGHT.toPx() })
            .coerceAtLeast(1f)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(372.dp)
                .background(Color.Black),
        ) {
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            val item = items[page]
            val videoUrl = VideoPlaybackQuality.urlFor(item.videoSources, videoQuality, item.videoUrl)
            var inlineMuted by remember(videoUrl) { mutableStateOf(startMuted()) }
            val size = measured[item.id]
            val pageW = size?.first ?: item.width?.toFloat() ?: 0f
            val pageH = size?.second ?: item.height?.toFloat() ?: 0f
            val pageAspect = contentAspectRatio(
                pageW,
                pageH,
                if (videoUrl != null) VIDEO_FALLBACK_ASPECT else PHOTO_FALLBACK_ASPECT,
            )
            val fitted = fittedContentPx(
                boxW,
                boxH,
                if (pageW > 1f) pageW else pageAspect,
                if (pageH > 1f) pageH else 1f,
            )
            val mediaModifier = if (fitted.widthPx > 1f && fitted.heightPx > 1f) {
                Modifier.size(
                    with(density) { fitted.widthPx.toDp() },
                    with(density) { fitted.heightPx.toDp() },
                )
            } else {
                Modifier.fillMaxSize()
            }
            val reportSize: (Float, Float) -> Unit = { width, height ->
                measured = measured + (item.id to (width to height))
                if (page == pagerState.currentPage) onMediaMeasured(width, height)
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(role = Role.Button, onClick = { onOpenMedia(page) }),
                contentAlignment = Alignment.Center,
            ) {
                PeekImage(
                    image = item.image,
                    contentDescription = if (videoUrl == null) item.contentDescription else null,
                    modifier = mediaModifier.then(
                        if (videoUrl == null) {
                            Modifier.clickable(role = Role.Button, onClick = { onOpenMedia(page) })
                        } else {
                            Modifier
                        },
                    ),
                    contentScale = ContentScale.Crop,
                    onIntrinsicSize = if (videoUrl == null) reportSize else null,
                )
                if (videoUrl != null && page == pagerState.currentPage) {
                    // Poster and caption paint first. The player attaches on the next frame.
                    var attachPlayer by remember(videoUrl) { mutableStateOf(false) }
                    LaunchedEffect(videoUrl) { attachPlayer = true }
                    if (attachPlayer) {
                        MutedInlineVideo(
                            videoUrl = videoUrl,
                            modifier = mediaModifier.fillMaxSize(),
                            muted = inlineMuted,
                            paused = videoPaused,
                            onVideoSize = reportSize,
                        )
                    }
                }
                if (videoUrl != null) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(PaneGround.copy(alpha = 0.55f))
                                .clickable(role = Role.Button, onClick = onToggleVideo),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                if (videoPaused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                                contentDescription = stringResource(if (videoPaused) R.string.play_video else R.string.pause),
                                tint = PaneMuted,
                                modifier = Modifier.size(28.dp),
                            )
                        }
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(12.dp)
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(PaneGround.copy(alpha = 0.55f))
                                .clickable(role = Role.Button) {
                                    inlineMuted = !inlineMuted
                                    onMutedChange(inlineMuted)
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                if (inlineMuted) Icons.AutoMirrored.Rounded.VolumeOff else Icons.AutoMirrored.Rounded.VolumeUp,
                                contentDescription = stringResource(if (inlineMuted) R.string.unmute else R.string.mute),
                                tint = PaneMuted,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }
            }
        }
        post.mediaBadge?.let { badge ->
            if (items.size == 1 || badge == "CAROUSEL" || badge == "GALLERY") {
                Row(
                    modifier = Modifier.align(Alignment.TopStart).padding(12.dp).height(28.dp).clip(CircleShape).background(PaneGround.copy(alpha = 0.91f)).padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (items[pagerState.currentPage].videoUrl != null) {
                        Icon(Icons.Rounded.Videocam, contentDescription = null, tint = PaneInk, modifier = Modifier.size(13.dp))
                    }
                    Text(badge, color = PaneInk, style = TextStyle(fontFamily = GeistMono, fontSize = 8.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.7.sp))
                }
            }
        }
        if (items.size > 1) {
            CarouselButton(
                icon = Icons.Rounded.ChevronLeft,
                contentDescription = stringResource(R.string.previous_media),
                enabled = pagerState.currentPage > 0,
                onClick = {
                    coroutineScope.launch {
                        pagerState.animateScrollToPage((pagerState.currentPage - 1).coerceAtLeast(0))
                    }
                },
                modifier = Modifier.align(Alignment.CenterStart).padding(8.dp),
            )
            CarouselButton(
                icon = Icons.Rounded.ChevronRight,
                contentDescription = stringResource(R.string.next_media),
                enabled = pagerState.currentPage < items.lastIndex,
                onClick = {
                    coroutineScope.launch {
                        pagerState.animateScrollToPage((pagerState.currentPage + 1).coerceAtMost(items.lastIndex))
                    }
                },
                modifier = Modifier.align(Alignment.CenterEnd).padding(8.dp),
            )
        } else {
            post.duration?.let { duration ->
                Box(
                    modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp).height(28.dp).clip(CircleShape).background(PaneGround.copy(alpha = 0.6f)).padding(horizontal = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(duration, color = PaneMuted, style = TextStyle(fontFamily = Inter, fontSize = 12.sp))
                }
            }
        }
        }
    }
}

@Composable
private fun CarouselButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(36.dp)
            .shadow(4.dp, CircleShape)
            .clip(CircleShape)
            .background(PaneGround.copy(alpha = if (enabled) 0.92f else 0.55f))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = if (enabled) PaneInk else PaneMuted,
            modifier = Modifier.size(24.dp),
        )
    }
}

@Composable
private fun LoadingViewer(progress: Float, message: String, onBack: () -> Unit, onRefresh: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        LoadingHeader(onBack, onRefresh)
        Column(
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CircularProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier.size(36.dp),
                color = PaneMuted,
                trackColor = PaneBorder,
            )
            Spacer(Modifier.height(18.dp))
            Text(
                text = stringResource(R.string.resolving_title),
                color = PaneInk,
                style = TextStyle(fontFamily = app.pane.android.ui.theme.PaneDisplay, fontSize = 26.sp, fontWeight = FontWeight.Medium, letterSpacing = (-0.025).em),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.resolving_subtitle),
                color = PaneSecondary,
                style = TextStyle(fontFamily = Inter, fontSize = 14.sp),
                modifier = Modifier.padding(horizontal = 8.dp),
            )
            Spacer(Modifier.height(24.dp))
            LoadingProgressCard(progress, message)
        }
        LoadingFooter(onBack)
    }
}

@Composable
private fun LoadingHeader(onBack: () -> Unit, onRefresh: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        LoadingHeaderPill(onClick = onBack) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.back), tint = PaneInk, modifier = Modifier.size(18.dp))
        }
        PaneLockup()
        LoadingHeaderPill(onClick = onRefresh) {
            Icon(Icons.Rounded.Refresh, contentDescription = stringResource(R.string.refresh), tint = PaneInk, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun LoadingHeaderPill(onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(PaneChip)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

@Composable
private fun LoadingProgressCard(progress: Float, message: String) {
    val animatedProgress by animateFloatAsState(targetValue = progress.coerceIn(0f, 1f), animationSpec = tween(300), label = "loadingProgress")
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(PaneChip)
            .padding(start = 16.dp, top = 16.dp, end = 14.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                text = stringResource(R.string.fetching_post_label),
                color = PaneSecondary,
                style = TextStyle(fontFamily = GeistMono, fontSize = 9.sp, fontWeight = FontWeight.Medium, letterSpacing = 1.sp),
            )
            Text(
                text = "${(animatedProgress * 100).roundToInt()}%",
                color = PaneMuted,
                style = TextStyle(fontFamily = Inter, fontSize = 12.sp),
            )
        }
        Box(modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape).background(PaneBorder)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(animatedProgress)
                    .fillMaxHeight()
                    .clip(CircleShape)
                    .background(PaneInk),
            )
        }
        Text(text = friendlyLoadingCopy(message), color = PaneSecondary, style = TextStyle(fontFamily = Inter, fontSize = 12.sp))
    }
}

@Composable
private fun LoadingFooter(onBack: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.loading_hint),
            color = PaneMuted,
            style = TextStyle(fontFamily = Inter, fontSize = 12.sp),
        )
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .border(1.dp, PaneBorder, RoundedCornerShape(50))
                .clickable(role = Role.Button, onClick = onBack)
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(Icons.Rounded.Close, contentDescription = null, tint = PaneInk, modifier = Modifier.size(14.dp))
            Text(
                text = stringResource(R.string.cancel),
                color = PaneInk,
                style = TextStyle(fontFamily = Inter, fontSize = 14.sp, fontWeight = FontWeight.Bold),
            )
        }
    }
}

@Composable
private fun friendlyLoadingCopy(message: String): String {
    val raw = message.contains('<') || message.contains("Exception", ignoreCase = true)
    return if (message.isBlank() || raw) stringResource(R.string.opening_post) else message
}

@Composable
private fun LoadFailedViewer(
    url: String,
    reason: String,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onCopy: () -> Unit,
) {
    ErrorShell(url, onBack, onOpen, onShare, onCopy) {
        UnloadableBody(
            host = displayHost(url),
            author = "",
            url = url,
            hint = stringResource(failureCopyRes(reason)),
            onRetry = onRetry,
        )
    }
}

@Composable
private fun UnavailableViewer(url: String, onBack: () -> Unit, onOpen: () -> Unit, onShare: () -> Unit, onCopy: () -> Unit) {
    ErrorShell(url, onBack, onOpen, onShare, onCopy) {
        UnloadableBody(
            host = displayHost(url),
            author = "",
            url = url,
            hint = stringResource(R.string.unsupported_link_description),
            onRetry = null,
            title = stringResource(R.string.not_a_single_post),
        )
    }
}

@Composable
private fun ErrorShell(
    url: String,
    onBack: () -> Unit,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onCopy: () -> Unit,
    body: @Composable () -> Unit,
) {
    val host = displayHost(url)
    val openLabel = stringResource(openInAppLabelRes(url))
    val sourceMark = sourceMarkRes(url)
    var overflow by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DonePill(onBack, overMedia = false)
        }
        Box(Modifier.weight(1f).verticalScroll(rememberScrollState())) { body() }
        ViewerBottomBar(
            openLabel,
            onShare = onShare,
            onOverflow = { overflow = true },
            onOpen = onOpen,
            sourceMark = sourceMark,
        )
    }
    if (overflow) {
        OverflowSheet(
            contextLine = host,
            canDownload = false,
            onDismiss = { overflow = false },
            onShare = { overflow = false; onShare() },
            onCopyLink = { overflow = false; onCopy() },
            onDownload = {},
            onAddNote = { overflow = false },
        )
    }
}

@Composable
private fun AuthorCaption(post: ViewerPostUiModel) {
    val name = post.authorName.ifBlank { stringResource(R.string.author_unknown) }
    val detail = post.authorMetadata.takeIf { post.authorName.isNotBlank() && it.isNotBlank() }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        val initial = name.firstOrNull()?.uppercase() ?: ""
        Box(Modifier.size(40.dp).clip(CircleShape).background(PaneTile).border(1.dp, PaneBorder, CircleShape), contentAlignment = Alignment.Center) {
            Text(initial, color = PaneMuted, style = TextStyle(fontFamily = Inter, fontSize = 14.sp, fontWeight = FontWeight.SemiBold))
        }
        Column {
            Text(name, color = PaneInk, style = TextStyle(fontFamily = Inter, fontSize = 15.sp, fontWeight = FontWeight.SemiBold))
            if (detail != null) {
                Text(detail, color = PaneMuted, style = TextStyle(fontFamily = Inter, fontSize = 13.sp))
            }
        }
    }
}

@Composable
private fun RedditBody(post: ViewerPostUiModel) {
    Text(post.authorMetadata.ifBlank { post.authorName }, color = PaneMuted, style = TextStyle(fontFamily = Inter, fontSize = 13.sp))
    Text(
        text = post.title,
        color = PaneInk,
        style = TextStyle(fontFamily = app.pane.android.ui.theme.PaneDisplay, fontSize = 23.sp, fontWeight = FontWeight.Medium, letterSpacing = (-0.02).em, lineHeight = 28.sp),
    )
}

@Composable
private fun LinkPreviewCard(
    url: String,
    item: ViewerMediaItemUiModel?,
    onOpen: (String) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(PaneTile).border(1.dp, PaneBorder, RoundedCornerShape(20.dp)).clickable(role = Role.Button) { onOpen(url) }.padding(12.dp),
    ) {
        if (item != null) {
            PeekImage(
                image = item.image,
                contentDescription = item.contentDescription,
                modifier = Modifier.fillMaxWidth().height(136.dp).clip(RoundedCornerShape(12.dp)),
                contentScale = ContentScale.Crop,
            )
        }
        Text(displayHost(url), modifier = Modifier.padding(top = 10.dp), color = PaneMuted, style = TextStyle(fontFamily = Inter, fontSize = 12.sp))
        Text(url, color = PaneInk, maxLines = 2, style = TextStyle(fontFamily = Inter, fontSize = 16.sp, fontWeight = FontWeight.SemiBold))
    }
}

@Composable
private fun FailureViewer(
    url: String,
    onBack: () -> Unit,
    label: String,
    title: String,
    description: String,
    detail: String?,
    actionLabel: String = stringResource(R.string.home),
    onAction: () -> Unit = onBack,
    actionIcon: ImageVector = Icons.Rounded.Home,
) {
    Column(Modifier.fillMaxSize()) {
        UnsupportedHeader(onBack)
        Column(
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 22.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier.size(76.dp).clip(CircleShape).background(PaneTile),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.LinkOff, contentDescription = null, tint = PaneAccent, modifier = Modifier.size(34.dp))
            }
            Spacer(Modifier.height(18.dp))
            Row(
                modifier = Modifier.height(27.dp).clip(CircleShape).background(PaneChip).padding(horizontal = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Icon(Icons.Rounded.LinkOff, contentDescription = null, tint = PaneSecondary, modifier = Modifier.size(13.dp))
                Text(
                    text = label,
                    color = PaneSecondary,
                    style = TextStyle(fontFamily = GeistMono, fontSize = 8.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.6.sp),
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = title,
                modifier = Modifier.fillMaxWidth(),
                color = PaneInk,
                textAlign = TextAlign.Center,
                style = TextStyle(fontFamily = Geist, fontSize = 27.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-1.1).sp),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = description,
                modifier = Modifier.fillMaxWidth(),
                color = PaneSecondary,
                textAlign = TextAlign.Center,
                style = TextStyle(fontFamily = Inter, fontSize = 14.sp, lineHeight = 20.sp),
            )
            if (!detail.isNullOrBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = detail,
                    modifier = Modifier.fillMaxWidth(),
                    color = PaneSecondary,
                    textAlign = TextAlign.Center,
                    style = TextStyle(fontFamily = Inter, fontSize = 13.sp, lineHeight = 18.sp),
                )
            }
            Spacer(Modifier.height(18.dp))
            Row(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(PaneChip).padding(horizontal = 15.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier.size(34.dp).clip(CircleShape).background(PaneGround),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.Link, contentDescription = null, tint = PaneAccent, modifier = Modifier.size(16.dp))
                }
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        text = stringResource(R.string.link_source),
                        color = PaneSecondary,
                        style = TextStyle(fontFamily = GeistMono, fontSize = 8.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.7.sp),
                    )
                    Text(
                        text = url,
                        color = PaneInk,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        style = TextStyle(fontFamily = Inter, fontSize = 11.sp, lineHeight = 15.sp),
                    )
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 18.dp),
            horizontalArrangement = Arrangement.Center,
        ) {
            Row(
                modifier = Modifier.clip(CircleShape).border(1.dp, PaneBorder, CircleShape).clickable(role = Role.Button, onClick = onAction).padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(actionIcon, contentDescription = null, tint = PaneInk, modifier = Modifier.size(14.dp))
                Text(
                    text = actionLabel,
                    color = PaneInk,
                    style = TextStyle(fontFamily = Inter, fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
                )
            }
        }
    }
}

@Composable
private fun UnsupportedHeader(onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        LoadingHeaderPill(onClick = onBack) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.back), tint = PaneInk, modifier = Modifier.size(18.dp))
        }
        PaneLockup()
        Box(
            modifier = Modifier.size(38.dp).clip(CircleShape).background(PaneChip),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.MoreHoriz, contentDescription = stringResource(R.string.more_options), tint = PaneSecondary, modifier = Modifier.size(20.dp))
        }
    }
}
