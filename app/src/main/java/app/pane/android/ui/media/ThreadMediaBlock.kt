package app.pane.android.ui.media

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.media3.ui.AspectRatioFrameLayout
import app.pane.android.ui.components.PeekImage
import app.pane.android.ui.model.ViewerMediaItemUiModel
import app.pane.android.ui.theme.PaneTile
import kotlinx.coroutines.delay

@Composable
fun ThreadMediaBlock(
    postUrl: String,
    items: List<ViewerMediaItemUiModel>,
    playingId: String?,
    muted: Boolean,
    userPaused: Boolean = false,
    onVisible: (String, Float, Int) -> Unit,
    onTogglePlay: (String) -> Unit = {},
    onToggleMute: () -> Unit = {},
    onOpen: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shown = items.take(ThreadMedia.shownCount(items.size))
    if (shown.isEmpty()) return
    val view = LocalView.current
    val grid = ThreadMedia.grid(items.size)
    Column(
        modifier
            .fillMaxWidth()
            .onGloballyPositioned { coordinates ->
                val videos = shown.filter { !it.videoUrl.isNullOrBlank() }
                if (videos.isEmpty()) return@onGloballyPositioned
                val bounds = coordinates.boundsInWindow()
                val window = view.height.toFloat().coerceAtLeast(1f)
                val height = coordinates.size.height.coerceAtLeast(1).toFloat()
                val topCut = if (bounds.top < 0f) -bounds.top else 0f
                val bottomCut = if (bounds.bottom > window) bounds.bottom - window else 0f
                val visible = ((height - topCut - bottomCut) / height).coerceIn(0f, 1f)
                videos.forEach { video -> onVisible(video.id, visible, bounds.top.toInt()) }
            },
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        when (grid) {
            MediaGrid.None -> Unit
            MediaGrid.Single -> Cell(postUrl, shown[0], 0, playingId, muted, userPaused, onTogglePlay, onToggleMute, onOpen, Modifier.fillMaxWidth())
            MediaGrid.Pair -> Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                shown.forEachIndexed { index, item ->
                    Cell(postUrl, item, index, playingId, muted, userPaused, onTogglePlay, onToggleMute, onOpen, Modifier.weight(1f))
                }
            }
            MediaGrid.Feature -> Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.height(220.dp)) {
                Cell(postUrl, shown[0], 0, playingId, muted, userPaused, onTogglePlay, onToggleMute, onOpen, Modifier.weight(1.4f).fillMaxSize())
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Cell(postUrl, shown[1], 1, playingId, muted, userPaused, onTogglePlay, onToggleMute, onOpen, Modifier.weight(1f).fillMaxWidth())
                    Cell(postUrl, shown[2], 2, playingId, muted, userPaused, onTogglePlay, onToggleMute, onOpen, Modifier.weight(1f).fillMaxWidth())
                }
            }
            MediaGrid.Quad -> {
                shown.chunked(2).forEachIndexed { rowIndex, row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        row.forEachIndexed { column, item ->
                            Cell(
                                postUrl, item, rowIndex * 2 + column, playingId, muted, userPaused,
                                onTogglePlay, onToggleMute, onOpen, Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Cell(
    postUrl: String,
    item: ViewerMediaItemUiModel,
    index: Int,
    playingId: String?,
    muted: Boolean,
    userPaused: Boolean,
    onTogglePlay: (String) -> Unit,
    onToggleMute: () -> Unit,
    onOpen: (Int) -> Unit,
    modifier: Modifier,
) {
    val ratio = ThreadMedia.aspectCap(item.width ?: 0, item.height ?: 0)
    val video = item.videoUrl
    val shape = RoundedCornerShape(12.dp)
    val active = !video.isNullOrBlank() && playingId == item.id
    val paused = !active || userPaused
    if (!video.isNullOrBlank()) {
        val touchExploration = rememberTouchExplorationEnabled()
        var showControls by remember(item.id) { mutableStateOf(touchExploration && !item.gif) }
        var scrubbing by remember(item.id) { mutableStateOf(false) }
        LaunchedEffect(showControls, paused, scrubbing, touchExploration, item.id) {
            if (
                showControls &&
                controlsAutoHide(playing = !paused, scrubbing = scrubbing, touchExplorationEnabled = touchExploration)
            ) {
                delay(CONTROLS_AUTO_HIDE_MS)
                showControls = false
            }
        }
        Box(
            modifier
                .aspectRatio(ratio)
                .clip(shape)
                .background(PaneTile)
                .confirmedMediaTaps(
                    onSingleTapConfirmed = { showControls = !showControls },
                    onDoubleTap = { onOpen(index) },
                ),
        ) {
            PeekImage(
                image = item.image,
                contentDescription = item.contentDescription,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )
            if (active) {
                MutedInlineVideo(
                    postUrl = postUrl,
                    mediaKey = item.id,
                    videoUrl = video,
                    muted = muted || item.gif,
                    paused = userPaused,
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            if (showControls && !item.gif) {
                InlineVideoChrome(
                    paused = paused,
                    muted = muted,
                    onTogglePlay = { onTogglePlay(item.id) },
                    onToggleMute = onToggleMute,
                    onEnterFullscreen = { onOpen(index) },
                    onScrubbing = { scrubbing = it },
                )
            }
        }
    } else {
        PeekImage(
            image = item.image,
            contentDescription = item.contentDescription,
            modifier = modifier
                .aspectRatio(ratio)
                .clip(shape)
                .background(PaneTile)
                .clickable(role = Role.Button) { onOpen(index) },
            contentScale = ContentScale.Fit,
        )
    }
}

class ThreadPlayController(startMuted: Boolean) {
    var reports by mutableStateOf(mapOf<String, VisibleSlice>())
    var manualId by mutableStateOf<String?>(null)
    var muted by mutableStateOf(startMuted)
    var userPaused by mutableStateOf(false)
    val winner: String? get() = ThreadVisibility.winner(reports.values.toList(), manualId)

    fun report(id: String, fraction: Float, top: Int) {
        reports = reports + (id to VisibleSlice(id, fraction, top))
        if (manualId == id && fraction < ThreadMedia.VISIBLE) {
            manualId = null
            userPaused = false
        }
    }

    fun togglePlay(id: String) {
        if (winner == id && !userPaused) {
            userPaused = true
        } else {
            manualId = id
            userPaused = false
        }
    }

    fun toggleMute() {
        muted = !muted
    }
}

@Composable
fun rememberThreadPlay(startMuted: Boolean): ThreadPlayController =
    remember { ThreadPlayController(startMuted) }
