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
import app.pane.android.data.tiktok.TikTokUrls
import app.pane.android.domain.tiktok.TikTokLinkKind
import app.pane.android.domain.tiktok.TikTokLinks
import app.pane.android.domain.tiktok.TikTokPlayback
import app.pane.android.domain.youtube.YouTubeLinkKind
import app.pane.android.domain.youtube.YouTubePlayback
import app.pane.android.ui.tiktok.TikTokFrame
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
    tikTokConsented: Boolean = false,
    onAcceptTikTok: () -> Unit = {},
    onTikTokPlayerShown: (String) -> Unit = {},
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
    val tiktokLink = remember(sourceUrl) { TikTokUrls.parse(sourceUrl) }
    val tiktokPost = tiktokLink != null && TikTokLinks.supports(sourceUrl)
    var acceptedTikTok by remember(sourceUrl) { mutableStateOf(false) }
    var runtimeRemoved by remember(sourceUrl) { mutableStateOf(false) }
    var runtimeBlocked by remember(sourceUrl) { mutableStateOf(false) }
    val tiktokConsentedNow = tikTokConsented || acceptedTikTok
    val tiktokVideoId = content?.post?.tiktokVideoId?.ifBlank { null } ?: tiktokLink?.videoId.orEmpty()
    val tiktokRemoved = runtimeRemoved || content?.post?.tiktokRemoved == true
    val tiktokBlocked = runtimeBlocked || content?.post?.tiktokEmbedOff == true
    val tiktokLive = content?.post?.tiktokLive == true
    val tiktokHtml = if (tiktokPost && tiktokConsentedNow && !tiktokRemoved && !tiktokBlocked && !tiktokLive) {
        TikTokPlayback.iframeHtml(tiktokVideoId, consented = true)
    } else {
        null
    }
    LaunchedEffect(tiktokVideoId, tikTokConsented, acceptedTikTok) {
        if (tiktokVideoId.isNotBlank() && tikTokConsented && !acceptedTikTok) onTikTokPlayerShown(tiktokVideoId)
    }
    val tiktokHandle = content?.post?.tiktokHandle?.ifBlank { null } ?: tiktokLink?.handle.orEmpty()
    val shortUnresolved = tiktokLink?.kind == TikTokLinkKind.Short && tiktokVideoId.isBlank()
    val tiktokHostLine = when {
        shortUnresolved -> "tiktok.com · ${stringResource(R.string.short_link_label)}"
        tiktokHandle.isNotBlank() -> "tiktok.com · @$tiktokHandle"
        else -> "tiktok.com"
    }
    val tiktokFrame = if (!tiktokPost) {
        null
    } else {
        TikTokFrame(
            embedHtml = tiktokHtml,
            embedOff = tiktokBlocked && tiktokConsentedNow,
            removed = tiktokRemoved && tiktokConsentedNow,
            live = tiktokLive,
            hostLine = tiktokHostLine,
            onPlay = {
                acceptedTikTok = true
                onAcceptTikTok()
                viewModel.onTikTokAccepted()
            },
            onOpenLink = { link ->
                val started = openExternally(context, link, finishAfter = false)
                if (!started) {
                    Toast.makeText(context, context.getString(R.string.action_failed), Toast.LENGTH_SHORT).show()
                }
            },
            onOpenInTikTok = {
                val started = openExternally(context, sourceUrl, finishAfter = true)
                if (shouldFinishAfterExternalOpen(started, finishAfter = true)) onBack()
            },
            onGone = {
                runtimeRemoved = true
                viewModel.onTikTokRemoved()
            },
            onEmbedOff = {
                runtimeBlocked = true
                viewModel.onTikTokEmbedOff()
            },
        )
    }
    val youtubeLink = remember(sourceUrl) { YouTubeUrls.parse(sourceUrl) }
    val youtubeId = youtubeLink?.videoId
    var acceptedYouTube by remember(sourceUrl) { mutableStateOf(false) }
    val consented = youTubeConsented || acceptedYouTube
    val embedOff = content?.post?.youtubeEmbedOff == true
    val ageRestricted = content?.post?.youtubeAgeRestricted == true
    LaunchedEffect(youtubeId, youTubeConsented, acceptedYouTube) {
        if (youtubeId != null && youTubeConsented && !acceptedYouTube) onYouTubePlayerShown(youtubeId)
    }
    val youtubeFrame = youtubeLink?.takeIf { it.videoId != null }?.let { link ->
        val id = link.videoId ?: return@let null
        val kind = link.kind
        YouTubeFrame(
            embedHtml = YouTubePlayback.iframeHtml(
                videoId = id,
                startSeconds = link.startSeconds,
                kind = kind,
                consented = consented,
                embeddable = !embedOff,
                ageRestricted = ageRestricted,
            ),
            embedOff = consented && embedOff,
            ageRestricted = consented && ageRestricted,
            portrait = false,
            title = content?.post?.title.orEmpty(),
            onPlay = {
                acceptedYouTube = true
                onAcceptYouTube(id)
                viewModel.onYouTubeAccepted()
            },
            onOpenLink = { link ->
                val started = openExternally(context, link, finishAfter = false)
                if (!started) {
                    Toast.makeText(context, context.getString(R.string.action_failed), Toast.LENGTH_SHORT).show()
                }
            },
            onLeave = onBack,
            onOpenInYouTube = {
                val started = openExternally(context, sourceUrl, finishAfter = true)
                if (shouldFinishAfterExternalOpen(started, finishAfter = true)) onBack()
            },
        )
    }
    val items = content?.post?.mediaItemsOrPrimary().orEmpty()
    val mediaIndex = content?.post?.initialMediaIndex?.coerceIn(0, items.lastIndex.coerceAtLeast(0)) ?: 0
    val item = items.getOrNull(mediaIndex)
    val contentWidth = item?.width?.takeIf { it > 1 }?.toFloat() ?: measuredWidth
    val contentHeight = item?.height?.takeIf { it > 1 }?.toFloat() ?: measuredHeight
    val openImmersive = youtubeId == null && !tiktokPost && VideoAutoplay.shouldOpen(
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
        tiktok = tiktokFrame,
        modifier = Modifier.fillMaxSize(),
    )
        PaneSnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = 88.dp))
    }
}
