package app.pane.android.ui.media

import android.content.Context
import android.content.ContextWrapper
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import app.pane.android.ui.theme.tokens.PaneTokenColors
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import app.pane.android.R
import app.pane.android.ui.player.playbackFraction
import app.pane.android.ui.player.seekPositionMs
import kotlinx.coroutines.delay
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import android.app.Application
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView as Media3PlayerView
import java.net.URI
import java.util.Locale

private const val REDDIT_MEDIA_USER_AGENT =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

@androidx.annotation.OptIn(UnstableApi::class)
internal fun exoPlayerFor(context: Context, videoUrl: String): ExoPlayer {
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

/** Framed posts play in place. The same player is reused when the post opens fullscreen. */
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
internal fun MutedInlineVideo(
    postUrl: String,
    mediaKey: String,
    videoUrl: String,
    modifier: Modifier = Modifier,
    muted: Boolean = true,
    paused: Boolean = false,
    onContinuity: (playing: Boolean, muted: Boolean) -> Unit = { _, _ -> },
    onVideoSize: ((width: Float, height: Float) -> Unit)? = null,
    resizeMode: Int = AspectRatioFrameLayout.RESIZE_MODE_ZOOM,
) {
    val session = rememberPlaybackSession()
    val acquired = session.acquire(postUrl, mediaKey, videoUrl, freshMuted = muted)
    val exoPlayer = acquired.player.exo
    val reused = remember(postUrl, mediaKey) { acquired.reused }
    var skipMute by remember(postUrl, mediaKey) { mutableStateOf(reused) }
    var skipPause by remember(postUrl, mediaKey) { mutableStateOf(reused) }
    SideEffect { exoPlayer.repeatMode = Player.REPEAT_MODE_ONE }
    LaunchedEffect(postUrl, mediaKey) {
        if (reused) onContinuity(exoPlayer.playWhenReady, exoPlayer.volume <= 0.001f)
    }
    LaunchedEffect(exoPlayer, muted) {
        if (skipMute) {
            skipMute = false
            return@LaunchedEffect
        }
        exoPlayer.volume = if (muted) 0f else exoPlayer.volume.takeIf { it > 0.001f } ?: 1f
    }
    LaunchedEffect(exoPlayer, paused) {
        if (skipPause) {
            skipPause = false
            return@LaunchedEffect
        }
        exoPlayer.playWhenReady = !paused
    }
    val sizeCallback = rememberUpdatedState(onVideoSize)
    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                val display = displayVideoSize(
                    videoSize.width,
                    videoSize.height,
                    videoSize.pixelWidthHeightRatio,
                ) ?: return
                sizeCallback.value?.invoke(display.first, display.second)
            }
        }
        exoPlayer.addListener(listener)
        onDispose { exoPlayer.removeListener(listener) }
    }
    VideoSurface(
        exoPlayer = exoPlayer,
        resizeMode = resizeMode,
        modifier = modifier,
    )
}

/**
 * Controls for the framed video. Play and pause live on the center button.
 * The fullscreen button is the bottom-right control. A bare tap never reaches this layer.
 */
@Composable
internal fun InlineVideoChrome(
    paused: Boolean,
    muted: Boolean,
    onTogglePlay: () -> Unit,
    onToggleMute: () -> Unit,
    onEnterFullscreen: () -> Unit,
    onScrubbing: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val session = rememberPlaybackSession()
    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var scrubbing by remember { mutableStateOf(false) }
    LaunchedEffect(scrubbing) { onScrubbing(scrubbing) }
    LaunchedEffect(scrubbing) {
        while (true) {
            val exo = session.player?.exo
            if (exo != null && !scrubbing) {
                positionMs = exo.currentPosition.coerceAtLeast(0L)
                val duration = exo.duration
                durationMs = if (duration > 0L) duration else 0L
            }
            delay(200)
        }
    }
    val scrubLabel = stringResource(R.string.playback_position)
    Box(modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(64.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.55f))
                .clickable(role = Role.Button, onClick = onTogglePlay),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (paused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                contentDescription = stringResource(if (paused) R.string.play_video else R.string.pause),
                tint = Color.White,
                modifier = Modifier.size(28.dp),
            )
        }
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.45f))
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .clickable(role = Role.Button, onClick = onToggleMute),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (muted) Icons.AutoMirrored.Rounded.VolumeOff else Icons.AutoMirrored.Rounded.VolumeUp,
                        contentDescription = stringResource(if (muted) R.string.unmute else R.string.mute),
                        tint = Color.White,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Slider(
                    value = playbackFraction(positionMs, durationMs),
                    onValueChange = { fraction ->
                        val exo = session.player?.exo ?: return@Slider
                        val duration = exo.duration
                        if (duration <= 0L) return@Slider
                        scrubbing = true
                        durationMs = duration
                        val target = seekPositionMs(duration, fraction)
                        positionMs = target
                        exo.seekTo(target)
                    },
                    onValueChangeFinished = { scrubbing = false },
                    enabled = durationMs > 0L,
                    modifier = Modifier
                        .weight(1f)
                        .semantics { contentDescription = scrubLabel },
                    colors = SliderDefaults.colors(
                        thumbColor = PaneTokenColors.ColorTextPrimary,
                        activeTrackColor = PaneTokenColors.ColorTextMeta,
                        inactiveTrackColor = PaneTokenColors.ColorBorderHairline,
                    ),
                )
                Box(
                    modifier = Modifier
                        .testTag(app.pane.android.ui.PaneTestTags.PLAYER_ENTER_FULLSCREEN)
                        .size(36.dp)
                        .clip(CircleShape)
                        .clickable(role = Role.Button, onClick = onEnterFullscreen),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Rounded.Fullscreen,
                        contentDescription = stringResource(R.string.enter_fullscreen),
                        tint = Color.White,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
    }
}

@Composable
internal fun rememberPlaybackSession(): PlaybackSession<ExoContinuable> {
    val context = LocalContext.current
    val owner = context.findActivity()
    checkNotNull(owner) { "Playback needs an activity." }
    return viewModel<PlaybackSessionViewModel>(owner).session
}

internal class PlaybackSessionViewModel(app: Application) : AndroidViewModel(app) {
    val session: PlaybackSession<ExoContinuable> = PlaybackSession { url ->
        ExoContinuable(exoPlayerFor(getApplication(), url))
    }

    override fun onCleared() {
        session.release()
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
internal class ExoContinuable(val exo: ExoPlayer) : ContinuablePlayer {
    override var mediaUrl: String? = null
    override var positionMs: Long
        get() = exo.currentPosition.coerceAtLeast(0L)
        set(value) {
            exo.seekTo(value)
        }
    override var playWhenReady: Boolean
        get() = exo.playWhenReady
        set(value) {
            exo.playWhenReady = value
        }
    override var volume: Float
        get() = exo.volume
        set(value) {
            exo.volume = value
        }

    override fun load(url: String, positionMs: Long) {
        mediaUrl = url
        exo.setMediaItem(MediaItem.fromUri(url), positionMs)
        exo.prepare()
    }

    override fun release() {
        exo.release()
    }
}

internal fun Context.findActivity(): ComponentActivity? {
    var current: Context = this
    while (current is ContextWrapper) {
        if (current is ComponentActivity) return current
        current = current.baseContext
    }
    return null
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
internal fun VideoSurface(
    exoPlayer: ExoPlayer,
    resizeMode: Int,
    modifier: Modifier = Modifier,
    shutterColor: Int = android.graphics.Color.BLACK,
) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            Media3PlayerView(context).apply {
                player = exoPlayer
                useController = false
                setShutterBackgroundColor(shutterColor)
                this.resizeMode = resizeMode
                isClickable = false
                isFocusable = false
            }
        },
        update = { view ->
            view.player = exoPlayer
            view.resizeMode = resizeMode
        },
        onRelease = { view -> view.player = null },
    )
}
