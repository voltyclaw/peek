package app.pane.android.ui.viewer

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pane.android.R
import app.pane.android.ui.components.AuthorByline
import app.pane.android.ui.components.AuthorThreadSection
import app.pane.android.ui.components.CaptionText
import app.pane.android.ui.components.CommentsSection
import app.pane.android.ui.components.PostActionsRow
import app.pane.android.ui.components.PeekImage
import app.pane.android.ui.media.framedMediaMaxHeightDp
import app.pane.android.ui.media.MutedInlineVideo
import app.pane.android.ui.media.VideoPlaybackQuality
import app.pane.android.ui.media.VideoQuality
import app.pane.android.ui.media.PHOTO_FALLBACK_ASPECT
import app.pane.android.ui.media.VIDEO_FALLBACK_ASPECT
import app.pane.android.ui.media.contentAspectRatio
import app.pane.android.ui.media.fittedContentPx
import app.pane.android.ui.model.UiImage
import app.pane.android.ui.model.sourceFace
import app.pane.android.ui.model.ViewerPostUiModel
import app.pane.android.ui.model.ViewerUiState
import app.pane.android.ui.model.ViewerMediaItemUiModel
import app.pane.android.ui.model.hasDownloadableMedia
import app.pane.android.ui.model.mediaItemsOrPrimary
import app.pane.android.ui.theme.GeistMono
import app.pane.android.ui.theme.Inter
import app.pane.android.ui.theme.PaneAccent
import app.pane.android.ui.theme.PaneBorder
import app.pane.android.ui.theme.PaneFill
import app.pane.android.ui.theme.PaneGround
import app.pane.android.ui.theme.PaneOnFill
import app.pane.android.ui.theme.PaneInk
import app.pane.android.ui.theme.PaneMuted
import app.pane.android.ui.theme.PaneTile
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
    onLeave: () -> Unit = onBack,
    onOpenInBrowser: (String) -> Unit = {},
) {
    Box(modifier = modifier.fillMaxSize().background(PaneGround), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier.widthIn(max = 390.dp).fillMaxWidth().fillMaxHeight(),
        ) {
            when (uiState) {
                is ViewerUiState.Loading -> LoadingViewer(onBack)
                is ViewerUiState.Unavailable -> UnavailableViewer(uiState.url, onBack, onOpenInBrowser)
                is ViewerUiState.LoadFailed -> LoadFailedViewer(uiState.reason, onBack, onRefresh)
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
) {
    var buffering by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth()) {
        ViewerHeader(
            sourceUrl = post.sourceUrl,
            onBack = onBack,
            onRefresh = onRefresh,
            onLeave = onLeave,
            onShareMedia = {
                onShare(post.mediaItemsOrPrimary().filter(ViewerMediaItemUiModel::hasDownloadableMedia))
            },
            canShareMedia = post.mediaItemsOrPrimary().any(ViewerMediaItemUiModel::hasDownloadableMedia),
        )
        if (buffering) {
            Box(Modifier.fillMaxWidth().height(2.dp).background(PaneAccent))
        }
    }
    val scrollState = rememberScrollState()
    val items = post.mediaItemsOrPrimary()
    val pagerState = rememberPagerState(
        initialPage = post.initialMediaIndex.coerceIn(0, items.lastIndex),
        pageCount = items::size,
    )
    Column(
        modifier = Modifier.fillMaxWidth().weight(1f).verticalScroll(scrollState).padding(horizontal = 16.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (items.any(::hasVisualMedia)) {
            MediaCanvas(
                post,
                items,
                pagerState,
                onOpenMedia,
                onMediaMeasured,
                videoQuality,
                startMuted,
                onMutedChange,
                onBuffering = { buffering = it },
            )
        }
        AuthorByline(post = post)
        Column(Modifier.fillMaxWidth()) {
            if (post.authorThread.size >= 2) {
                AuthorThreadSection(post)
            } else if (post.title.isNotBlank()) {
                CaptionText(post)
            }
            Spacer(Modifier.height(8.dp))
            PostActionsRow(
                onCopyLink = { onCopyLink(post.sourceUrl) },
                onSharePost = { onSharePost(post.sourceUrl, post.title) },
                onOpenInApp = { onOpenInApp(post.sourceUrl) },
                onDownload = { onDownload(items.filter(ViewerMediaItemUiModel::hasDownloadableMedia)) },
                canDownload = items.any(ViewerMediaItemUiModel::hasDownloadableMedia),
            )
            Spacer(Modifier.height(16.dp))
            CommentsSection(
                post = post,
                isLoadingMore = isLoadingMoreComments,
                scrollOffset = scrollState.value,
                onLoadMore = onLoadMoreComments,
            )
        }
        Spacer(Modifier.height(18.dp))
    }
}

@Composable
private fun ViewerHeader(
    sourceUrl: String,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onLeave: () -> Unit,
    onShareMedia: suspend () -> Unit,
    canShareMedia: Boolean,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val face = sourceFace(sourceUrl)
    Row(
        modifier = Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(48.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = stringResource(R.string.back),
                tint = PaneInk,
                modifier = Modifier.size(22.dp),
            )
        }
        if (face.chip.isNotBlank()) {
            Box(
                modifier = Modifier.size(20.dp).clip(CircleShape).background(PaneTile),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = face.chip.take(1),
                    color = PaneInk,
                    style = TextStyle(fontFamily = Inter, fontSize = 9.sp, fontWeight = FontWeight.Medium),
                )
            }
            Spacer(Modifier.size(8.dp))
            Text(
                text = face.label,
                color = PaneInk,
                style = TextStyle(fontFamily = Inter, fontSize = 14.sp, fontWeight = FontWeight.Medium),
            )
        }
        Spacer(Modifier.weight(1f))
        Box {
            Box(
                modifier = Modifier.size(48.dp).clip(CircleShape).clickable(role = Role.Button) { menuOpen = true },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.MoreVert,
                    contentDescription = stringResource(R.string.more_options),
                    tint = PaneInk,
                    modifier = Modifier.size(22.dp),
                )
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.refresh)) },
                    onClick = {
                        menuOpen = false
                        onRefresh()
                    },
                )
                if (canShareMedia) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.share_media)) },
                        onClick = {
                            menuOpen = false
                            scope.launch { onShareMedia() }
                        },
                    )
                }
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.leave)) },
                    onClick = {
                        menuOpen = false
                        onLeave()
                    },
                )
            }
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
    onBuffering: (Boolean) -> Unit = {},
) {
    val coroutineScope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
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
        val windowCap = with(density) { framedMediaMaxHeightDp(LocalConfiguration.current.screenHeightDp.toFloat()).dp.toPx() }
        val boxH = (boxW / frameAspect)
            .coerceAtMost(windowCap)
            .coerceAtLeast(1f)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(with(density) { boxH.toDp() })
                .clip(RoundedCornerShape(12.dp))
                .border(1.dp, PaneBorder, RoundedCornerShape(12.dp))
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
                    contentScale = ContentScale.Fit,
                    onIntrinsicSize = if (videoUrl == null) reportSize else null,
                )
                if (videoUrl != null && page == pagerState.currentPage) {
                    // Poster and caption paint first. The player attaches on the next frame.
                    var attachPlayer by remember(videoUrl) { mutableStateOf(false) }
                    LaunchedEffect(videoUrl) { attachPlayer = true }
                    if (attachPlayer) {
                        MutedInlineVideo(
                            videoUrl = videoUrl,
                            modifier = mediaModifier,
                            muted = inlineMuted,
                            onVideoSize = reportSize,
                            onBuffering = onBuffering,
                        )
                    }
                }
                if (videoUrl != null) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        Row(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(12.dp)
                                .height(28.dp)
                                .clip(CircleShape)
                                .background(PaneFill.copy(alpha = 0.85f))
                                .clickable(role = Role.Button) {
                                    inlineMuted = !inlineMuted
                                    onMutedChange(inlineMuted)
                                    haptic.performHapticFeedback(
                                        if (inlineMuted) HapticFeedbackType.ToggleOff else HapticFeedbackType.ToggleOn,
                                    )
                                }
                                .padding(horizontal = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(
                                if (inlineMuted) Icons.AutoMirrored.Rounded.VolumeOff else Icons.AutoMirrored.Rounded.VolumeUp,
                                contentDescription = stringResource(if (inlineMuted) R.string.unmute else R.string.mute),
                                tint = PaneOnFill,
                                modifier = Modifier.size(14.dp),
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
            Row(
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                items.indices.forEach { index ->
                    val active = index == pagerState.currentPage
                    Box(
                        modifier = Modifier
                            .size(if (active) 7.dp else 6.dp)
                            .clip(CircleShape)
                            .background(if (active) PaneAccent else PaneMuted),
                    )
                }
            }
        } else {
            post.duration?.let { duration ->
                Box(
                    modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp).height(28.dp).clip(CircleShape).background(PaneFill.copy(alpha = 0.85f)).padding(horizontal = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(duration, color = PaneOnFill, style = TextStyle(fontFamily = GeistMono, fontSize = 9.sp, fontWeight = FontWeight.Bold))
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
private fun LoadingViewer(onBack: () -> Unit) {
    val shimmer = rememberInfiniteTransition(label = "openShimmer")
    val alpha by shimmer.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse),
        label = "openShimmerAlpha",
    )
    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(48.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = stringResource(R.string.back),
                    tint = PaneInk,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
        Column(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(40.dp),
                color = PaneAccent,
                strokeWidth = 3.dp,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.opening_post),
                color = PaneInk,
                style = TextStyle(fontFamily = Inter, fontSize = 15.sp, fontWeight = FontWeight.Medium),
            )
            Spacer(Modifier.height(28.dp))
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(36.dp).clip(CircleShape).background(PaneTile.copy(alpha = alpha)))
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(Modifier.size(width = 120.dp, height = 12.dp).clip(RoundedCornerShape(6.dp)).background(PaneTile.copy(alpha = alpha)))
                        Box(Modifier.size(width = 72.dp, height = 10.dp).clip(RoundedCornerShape(5.dp)).background(PaneTile.copy(alpha = alpha)))
                    }
                }
                Box(Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(6.dp)).background(PaneTile.copy(alpha = alpha)))
                Box(Modifier.fillMaxWidth(0.85f).height(12.dp).clip(RoundedCornerShape(6.dp)).background(PaneTile.copy(alpha = alpha)))
                Box(Modifier.fillMaxWidth(0.55f).height(12.dp).clip(RoundedCornerShape(6.dp)).background(PaneTile.copy(alpha = alpha)))
            }
        }
    }
}


@Composable
private fun LoadFailedViewer(reason: String, onBack: () -> Unit, onRetry: () -> Unit) {
    val kind = runCatching { OpenFailureKind.valueOf(reason) }.getOrDefault(OpenFailureKind.Network)
    val detail = when (kind) {
        OpenFailureKind.Private -> R.string.reason_private
        OpenFailureKind.Expired -> R.string.reason_expired
        OpenFailureKind.Network -> R.string.reason_network
    }
    StatusPage(
        title = stringResource(R.string.couldnt_open),
        body = stringResource(detail),
        primary = stringResource(R.string.try_again),
        onPrimary = onRetry,
        onBack = onBack,
    )
}

private fun isFacebookMarketplace(url: String): Boolean {
    val lower = url.lowercase()
    return "facebook.com/marketplace" in lower || "fb.com/marketplace" in lower
}

@Composable
private fun UnavailableViewer(url: String, onBack: () -> Unit, onOpenInBrowser: (String) -> Unit) {
    StatusPage(
        title = stringResource(R.string.pane_cant_show),
        body = stringResource(R.string.not_a_single_post),
        primary = stringResource(R.string.open_in_browser),
        onPrimary = { onOpenInBrowser(url) },
        onBack = onBack,
    )
}

@Composable
private fun StatusPage(
    title: String,
    body: String,
    primary: String,
    onPrimary: () -> Unit,
    onBack: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(48.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = stringResource(R.string.back),
                    tint = PaneInk,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
        Column(
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = title,
                color = PaneInk,
                textAlign = TextAlign.Center,
                style = TextStyle(fontFamily = Inter, fontSize = 20.sp, fontWeight = FontWeight.SemiBold),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = body,
                modifier = Modifier.widthIn(max = 280.dp),
                color = PaneMuted,
                textAlign = TextAlign.Center,
                style = TextStyle(fontFamily = Inter, fontSize = 15.sp, lineHeight = 22.sp),
            )
            Spacer(Modifier.height(24.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(PaneFill)
                    .clickable(role = Role.Button, onClick = onPrimary),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = primary,
                    color = PaneOnFill,
                    style = TextStyle(fontFamily = Inter, fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
                )
            }
            Text(
                text = stringResource(R.string.back),
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .clickable(role = Role.Button, onClick = onBack)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                color = PaneAccent,
                style = TextStyle(fontFamily = Inter, fontSize = 15.sp, fontWeight = FontWeight.Medium),
            )
        }
    }
}

