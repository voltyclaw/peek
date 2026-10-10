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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
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
import app.pane.android.ui.media.CONTROLS_AUTO_HIDE_MS
import app.pane.android.ui.media.InlineVideoChrome
import app.pane.android.ui.media.confirmedMediaTaps
import app.pane.android.ui.media.controlsAutoHide
import app.pane.android.ui.media.rememberTouchExplorationEnabled
import app.pane.android.ui.components.autolinkedCaption
import app.pane.android.ui.components.ArticleCard
import app.pane.android.ui.components.AuthorByline
import app.pane.android.ui.components.ExternalLinkCard
import app.pane.android.ui.components.QuoteCard
import app.pane.android.ui.components.AuthorThreadSection
import app.pane.android.ui.components.CaptionText
import app.pane.android.ui.components.CommentsSection
import app.pane.android.ui.components.PeekImage
import app.pane.android.ui.components.ProfileAvatar
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
import app.pane.android.ui.text.MentionNetwork
import app.pane.android.ui.text.userContent
import app.pane.android.ui.youtube.YouTubeConsentSurface
import app.pane.android.ui.tiktok.TikTokConsentSurface
import app.pane.android.ui.tiktok.TikTokDetails
import app.pane.android.ui.tiktok.TikTokFrame
import app.pane.android.ui.youtube.YouTubeFrame
import app.pane.android.ui.theme.PaneGround
import app.pane.android.ui.theme.PaneOnFill
import app.pane.android.ui.theme.PaneHandle
import app.pane.android.ui.theme.PaneHandlePressed
import app.pane.android.ui.theme.PaneInk
import app.pane.android.ui.theme.PaneMuted
import app.pane.android.ui.theme.PaneSecondary
import app.pane.android.ui.theme.PaneTile
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun ViewerView(
    uiState: ViewerUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onOpenMedia: (String, Int) -> Unit,
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
    onStar: () -> Unit = {},
    youtube: YouTubeFrame? = null,
    tiktok: TikTokFrame? = null,
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
                    starred = uiState.starred,
                    onStar = onStar,
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
                    onOpenLinked = onOpenOutbound,
                    youtube = youtube,
                    tiktok = tiktok,
                )
            }
        }
    }
}

@Composable
private fun ColumnScope.ViewerContent(
    post: ViewerPostUiModel,
    starred: Boolean,
    onStar: () -> Unit,
    isLoadingMoreComments: Boolean,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onLoadMoreComments: () -> Unit,
    onOpenMedia: (String, Int) -> Unit,
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
    onOpenLinked: (String) -> Unit,
    youtube: YouTubeFrame? = null,
    tiktok: TikTokFrame? = null,
) {
    val host = displayHost(post.sourceUrl)
    val play = app.pane.android.ui.media.rememberThreadPlay(startMuted())
    val playback = app.pane.android.ui.media.rememberPlaybackSession()
    androidx.compose.runtime.LaunchedEffect(play.winner) {
        if (play.reports.isNotEmpty() && play.winner == null) playback.park()
    }
    val affordance = rememberOpenAffordance(post.sourceUrl)
    val appName = affordance.appNameRes?.let { stringResource(it) }.orEmpty()
    val openLabel = if (affordance.opensInApp && affordance.appNameRes != null) {
        stringResource(R.string.open_in_named_app, appName)
    } else {
        stringResource(R.string.open_in_browser)
    }
    val scope = rememberCoroutineScope()
    var overflow by remember { mutableStateOf(false) }
    var editingNote by remember { mutableStateOf(false) }
    var videoPaused by remember { mutableStateOf(false) }
    var showWarned by remember(post.sourceUrl) { mutableStateOf(false) }
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
    val overMedia = hasMedia && !linkCard && post.authorThread.size < 2 && !post.bluesky
    Box(Modifier.fillMaxWidth().weight(1f)) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(scrollState).padding(bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (tiktok != null) {
                ViewerTopBar(
                    onBack,
                    onRefresh,
                    overMedia = false,
                    starred = starred,
                    onStar = onStar,
                    showRefresh = tiktok.embedHtml != null,
                )
                TikTokConsentSurface(tiktok)
            } else if (youtube != null) {
                ViewerTopBar(
                    onBack,
                    onRefresh,
                    overMedia = false,
                    starred = starred,
                    onStar = onStar,
                    showRefresh = youtube.embedHtml != null,
                )
                YouTubeConsentSurface(youtube)
            } else if (overMedia) {
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
                        post.sourceUrl,
                        { videoPaused = it },
                    )
                    Box(
                        Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(72.dp).background(
                            Brush.verticalGradient(listOf(Color.Transparent, PaneGround)),
                        ),
                    )
                    ViewerTopBar(onBack, onRefresh, overMedia = true, starred = starred, onStar = onStar)
                }
            } else {
                ViewerTopBar(onBack, onRefresh, overMedia = false, starred = starred, onStar = onStar)
            }
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                val showAuthor = post.authorName.isNotBlank() || post.authorMetadata.isNotBlank()
                if (tiktok != null) {
                    TikTokDetails(
                        authorName = post.authorName,
                        handle = post.tiktokHandle.ifBlank { post.authorMetadata },
                        caption = post.title,
                        postedAtEpochSeconds = post.tiktokPostedAtEpochSeconds,
                        detailsFailed = post.tiktokDetailsFailed,
                        removed = tiktok.removed,
                        live = tiktok.live,
                        embedOff = tiktok.embedOff,
                        onOpenProfile = onOpenLinked,
                        onOpenMention = onOpenLinked,
                        onOpenComments = { scope.launch { onOpenInApp(post.sourceUrl) } },
                        onRetry = onRefresh,
                    )
                } else if (youtube != null) {
                    if (post.title.isNotBlank()) {
                        Text(
                            text = post.title,
                            color = PaneInk,
                            style = TextStyle(fontFamily = Inter, fontSize = 20.sp, fontWeight = FontWeight.Medium, lineHeight = 26.sp).userContent(),
                        )
                    }
                    if (post.metaLine.isNotBlank()) {
                        Text(
                            text = post.metaLine,
                            color = PaneMuted,
                            style = TextStyle(fontFamily = Inter, fontSize = 13.sp).userContent(),
                        )
                    }
                    if (showAuthor) AuthorCaption(post, onOpenLinked)
                    if (post.description.isNotBlank()) {
                        Text(
                            text = post.description,
                            color = PaneInk,
                            style = TextStyle(fontFamily = Inter, fontSize = 15.sp, lineHeight = 22.sp).userContent(),
                        )
                    }
                } else if (post.bluesky) {
                    BlueskyBody(post, play, onOpenLinked, onOpenMedia, showWarned) { showWarned = true }
                } else if (reddit) {
                    RedditBody(post, onOpenLinked)
                    if (outbound != null && linkCard && post.linkCards.isEmpty()) {
                        LinkPreviewCard(outbound, currentItem, onOpenLinked)
                    }
                } else {
                    if (showAuthor) AuthorCaption(post, onOpenLinked)
                    if (post.authorThread.size >= 2) {
                        AuthorThreadSection(post, onOpenLinked, play, onOpenMedia)
                    } else if (outbound != null && linkCard && post.linkCards.isEmpty() && post.article == null) {
                        CaptionText(post, onOpen = onOpenLinked)
                        LinkPreviewCard(outbound, currentItem, onOpenLinked)
                    } else if (textOnly && post.article == null) {
                        Box(Modifier.padding(vertical = 8.dp).width(48.dp).height(1.dp).background(PaneBorder))
                        CaptionText(post, large = true, onOpen = onOpenLinked)
                    } else {
                        CaptionText(post, onOpen = onOpenLinked)
                    }
                }
                val hideWarned = post.blueskyWarning && !showWarned
                if (!hideWarned) {
                    post.article?.let { ArticleCard(it, onOpenLinked) }
                    post.quote?.let { QuoteCard(it, onOpenLinked, maxLines = if (post.bluesky) 6 else Int.MAX_VALUE) }
                    post.linkCards.forEach { card -> ExternalLinkCard(card, onOpenLinked) }
                }
                if (overMedia && post.authorThread.size < 2 && !reddit) {
                    // caption already placed above for the image/video path
                }
                if (tiktok == null) CommentsSection(
                    post = post,
                    isLoadingMore = isLoadingMoreComments,
                    scrollOffset = scrollState.value,
                    onLoadMore = onLoadMoreComments,
                    host = host,
                    onOpenSource = { scope.launch { onOpenInApp(post.sourceUrl) } },
                    onOpen = onOpenLinked,
                    play = play,
                    onOpenMedia = onOpenMedia,
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
        sourceMark = if (post.bluesky) null else affordance.markRes,
        useGlobe = affordance.useGlobe && !post.bluesky,
        showOpen = tiktok == null || (!tiktok.embedOff && !tiktok.removed && !tiktok.live),
        openWord = when {
            post.bluesky -> stringResource(R.string.source_bluesky)
            tiktok != null && affordance.opensInApp -> stringResource(R.string.source_tiktok)
            else -> null
        },
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
    onOpenMedia: (String, Int) -> Unit,
    onMediaMeasured: (Float, Float) -> Unit,
    videoQuality: VideoQuality,
    startMuted: () -> Boolean,
    onMutedChange: (Boolean) -> Unit,
    videoPaused: Boolean = false,
    onToggleVideo: () -> Unit = {},
    postUrl: String = "",
    onPausedChange: (Boolean) -> Unit = {},
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
            val touchExploration = rememberTouchExplorationEnabled()
            var showControls by remember(videoUrl) { mutableStateOf(videoUrl != null && touchExploration) }
            var scrubbing by remember(videoUrl) { mutableStateOf(false) }
            LaunchedEffect(touchExploration, videoUrl) {
                if (videoUrl != null && touchExploration) showControls = true
            }
            LaunchedEffect(showControls, videoPaused, scrubbing, touchExploration, videoUrl) {
                if (
                    videoUrl != null &&
                    showControls &&
                    controlsAutoHide(
                        playing = !videoPaused,
                        scrubbing = scrubbing,
                        touchExplorationEnabled = touchExploration,
                    )
                ) {
                    delay(CONTROLS_AUTO_HIDE_MS)
                    showControls = false
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (videoUrl == null) {
                            Modifier.clickable(role = Role.Button, onClick = { onOpenMedia("", page) })
                        } else {
                            Modifier.confirmedMediaTaps(
                                onSingleTapConfirmed = { showControls = !showControls },
                                onDoubleTap = { onOpenMedia("", page) },
                            )
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                PeekImage(
                    image = item.image,
                    contentDescription = if (videoUrl == null) item.contentDescription else null,
                    modifier = mediaModifier.then(
                        if (videoUrl == null) {
                            Modifier.clickable(role = Role.Button, onClick = { onOpenMedia("", page) })
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
                            postUrl = postUrl,
                            mediaKey = item.id,
                            videoUrl = videoUrl,
                            modifier = mediaModifier.fillMaxSize(),
                            muted = inlineMuted,
                            paused = videoPaused,
                            onContinuity = { playing, isMuted ->
                                inlineMuted = isMuted
                                onPausedChange(!playing)
                            },
                            onVideoSize = reportSize,
                        )
                    }
                }
                if (videoUrl != null && showControls) {
                    InlineVideoChrome(
                        paused = videoPaused,
                        muted = inlineMuted,
                        onTogglePlay = onToggleVideo,
                        onToggleMute = {
                            inlineMuted = !inlineMuted
                            onMutedChange(inlineMuted)
                        },
                        onEnterFullscreen = { onOpenMedia("", page) },
                        onScrubbing = { scrubbing = it },
                    )
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
        } else if (items[pagerState.currentPage].videoUrl == null) {
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
    reason: OpenFailureKind,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onCopy: () -> Unit,
) {
    ErrorShell(url, onBack, onOpen, onShare, onCopy, showOpen = false) {
        OpenRecovery(url = url, reason = reason, onOpen = onOpen, onRetry = onRetry)
    }
}

@Composable
private fun UnavailableViewer(url: String, onBack: () -> Unit, onOpen: () -> Unit, onShare: () -> Unit, onCopy: () -> Unit) {
    ErrorShell(url, onBack, onOpen, onShare, onCopy, showOpen = false) {
        OpenRecovery(url = url, reason = null, onOpen = onOpen, onRetry = {})
    }
}

@Composable
private fun ErrorShell(
    url: String,
    onBack: () -> Unit,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onCopy: () -> Unit,
    showOpen: Boolean = true,
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
            showOpen = showOpen,
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
private fun BlueskyBody(
    post: ViewerPostUiModel,
    play: app.pane.android.ui.media.ThreadPlayController,
    onOpen: (String) -> Unit,
    onOpenMedia: (String, Int) -> Unit,
    showWarned: Boolean,
    onShowPost: () -> Unit,
) {
    if (!post.replyingTo.isNullOrBlank()) {
        val line = stringResource(R.string.bs_replying_to, post.replyingTo)
        Text(
            text = line,
            color = PaneMuted,
            modifier = Modifier.then(
                if (post.replyingToUrl != null) {
                    Modifier.clickable(role = Role.Button) { onOpen(post.replyingToUrl) }
                } else {
                    Modifier
                },
            ),
            style = TextStyle(fontFamily = Inter, fontSize = 14.sp).userContent(),
        )
    }
    if (post.authorName.isNotBlank() || post.authorMetadata.isNotBlank() || post.blueskyAvatarHidden) {
        AuthorCaption(post, onOpen)
    }
    if (post.blueskyWarning && !showWarned) {
        Column(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(PaneTile).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(stringResource(R.string.bs_cover_warn), color = PaneInk, style = TextStyle(fontFamily = Inter, fontSize = 16.sp, fontWeight = FontWeight.SemiBold))
            Text(
                stringResource(R.string.bs_show_post),
                color = PaneInk,
                modifier = Modifier.heightIn(min = 48.dp).clickable(role = Role.Button, onClick = onShowPost).padding(vertical = 12.dp),
                style = TextStyle(fontFamily = Inter, fontSize = 15.sp, fontWeight = FontWeight.Medium),
            )
        }
        return
    }
    if (post.authorThread.size < 2) {
        if (post.title.isNotBlank()) BskyRichText(post.title, post.blueskySpans, onOpen)
        BlueskyMedia(post.sourceUrl, post.mediaItemsOrPrimary().filter { hasVisualMedia(it) || !it.cover.isNullOrBlank() }, play, onOpenMedia)
    }
}

@Composable
private fun BlueskyMedia(
    postUrl: String,
    items: List<app.pane.android.ui.model.ViewerMediaItemUiModel>,
    play: app.pane.android.ui.media.ThreadPlayController,
    onOpenMedia: (String, Int) -> Unit,
) {
    if (items.isEmpty()) return
    if (items.size >= 5) {
        val pager = rememberPagerState(pageCount = { items.size })
        HorizontalPager(state = pager, modifier = Modifier.fillMaxWidth()) { page ->
            app.pane.android.ui.media.ThreadMediaBlock(
                postUrl = postUrl,
                items = listOf(items[page]),
                playingId = play.winner,
                muted = play.muted,
                userPaused = play.userPaused,
                onVisible = { id, fraction, top -> play.report(id, fraction, top) },
                onTogglePlay = { id -> play.togglePlay(id) },
                onToggleMute = { play.toggleMute() },
                onOpen = { onOpenMedia("", page) },
            )
        }
        return
    }
    app.pane.android.ui.media.ThreadMediaBlock(
        postUrl = postUrl,
        items = items,
        playingId = play.winner,
        muted = play.muted,
        userPaused = play.userPaused,
        onVisible = { id, fraction, top -> play.report(id, fraction, top) },
        onTogglePlay = { id -> play.togglePlay(id) },
        onToggleMute = { play.toggleMute() },
        onOpen = { index -> onOpenMedia("", index) },
    )
}

@Composable
private fun BskyRichText(text: String, spans: List<app.pane.android.ui.model.TextSpanUi>, onOpen: (String) -> Unit) {
    val annotated = androidx.compose.ui.text.buildAnnotatedString {
        append(text)
        spans.forEach { span ->
            val start = span.start.coerceIn(0, text.length)
            val end = span.end.coerceIn(start, text.length)
            val url = span.url
            if (end <= start || url.isNullOrBlank()) return@forEach
            addLink(
                androidx.compose.ui.text.LinkAnnotation.Clickable(
                    tag = url,
                    linkInteractionListener = { onOpen(url) },
                ),
                start,
                end,
            )
            addStyle(
                androidx.compose.ui.text.SpanStyle(color = PaneHandle, fontWeight = FontWeight.Medium),
                start,
                end,
            )
        }
    }
    Text(
        text = annotated,
        style = TextStyle(fontFamily = Inter, fontSize = 16.sp, lineHeight = 22.sp, color = PaneInk).userContent(),
    )
}

@Composable
private fun AuthorCaption(post: ViewerPostUiModel, onOpen: (String) -> Unit) {
    val name = post.authorName.ifBlank { stringResource(R.string.author_unknown) }
    val detail = post.authorMetadata.takeIf { post.authorName.isNotBlank() && it.isNotBlank() }
    val handle = detail?.trim()?.removePrefix("@")?.takeIf { detail.trim().startsWith("@") }
    val profile = post.authorProfileUrl
    val label = if (profile != null) {
        app.pane.android.ui.components.profileOpenLabel(profile, handle)
    } else {
        name
    }
    Row(
        modifier = Modifier.then(
            if (profile != null) {
                Modifier
                    .heightIn(min = 48.dp)
                    .clickable(role = Role.Button, onClick = { onOpen(profile) })
                    .clearAndSetSemantics {
                        role = Role.Button
                        contentDescription = label
                        this.onClick { onOpen(profile); true }
                    }
            } else {
                Modifier
            },
        ),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ProfileAvatar(image = post.authorAvatar, label = name, size = 40.dp, empty = post.blueskyAvatarHidden)
        Column {
            val redditAccount = profile?.contains("reddit.com") == true
            val handleAsName = post.bluesky && post.authorMetadata.isBlank() && name.startsWith("@")
            Text(
                name,
                color = if (handleAsName || (redditAccount && profile != null)) PaneHandle else PaneInk,
                style = TextStyle(
                    fontFamily = Inter,
                    fontSize = 15.sp,
                    fontWeight = if (redditAccount && profile != null) FontWeight.Medium else FontWeight.SemiBold,
                ).userContent(),
            )
            if (detail != null) {
                val handleLine = detail.trim().startsWith("@")
                Text(
                    detail,
                    color = if (handleLine) PaneHandle else PaneMuted,
                    style = TextStyle(fontFamily = Inter, fontSize = 13.sp, fontWeight = FontWeight.Medium).userContent(),
                )
            }
        }
    }
}

@Composable
private fun RedditBody(post: ViewerPostUiModel, onOpen: (String) -> Unit) {
    if (post.authorName.isNotBlank() || !post.authorProfileUrl.isNullOrBlank()) {
        AuthorCaption(post, onOpen)
    }
    if (post.authorMetadata.isNotBlank()) {
        Text(
            text = autolinkedCaption(post.authorMetadata, PaneMuted, MentionNetwork.Reddit, onOpen, PaneHandle, PaneHandlePressed),
            color = PaneMuted,
            style = TextStyle(fontFamily = Inter, fontSize = 13.sp).userContent(),
        )
    }
    Text(
        text = autolinkedCaption(post.title, PaneInk, MentionNetwork.Reddit, onOpen, PaneHandle, PaneHandlePressed),
        color = PaneInk,
        style = TextStyle(fontFamily = app.pane.android.ui.theme.PaneDisplay, fontSize = 23.sp, fontWeight = FontWeight.Medium, letterSpacing = (-0.02).em, lineHeight = 28.sp).userContent(),
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
