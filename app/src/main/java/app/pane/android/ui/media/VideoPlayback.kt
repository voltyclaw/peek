package app.pane.android.ui.media

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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

/** Framed posts play in place. Sound follows the saved mute mode until a tap opens fullscreen. */
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
internal fun MutedInlineVideo(
    videoUrl: String,
    modifier: Modifier = Modifier,
    muted: Boolean = true,
    paused: Boolean = false,
    onVideoSize: ((width: Float, height: Float) -> Unit)? = null,
) {
    val context = LocalContext.current
    val exoPlayer = remember(videoUrl) {
        exoPlayerFor(context, videoUrl).apply {
            setMediaItem(MediaItem.fromUri(videoUrl))
            repeatMode = Player.REPEAT_MODE_ONE
            volume = if (muted) 0f else 1f
            playWhenReady = true
            prepare()
        }
    }
    LaunchedEffect(exoPlayer, muted) {
        exoPlayer.volume = if (muted) 0f else 1f
    }
    LaunchedEffect(exoPlayer, paused) {
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
        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }
    VideoSurface(
        exoPlayer = exoPlayer,
        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM,
        modifier = modifier,
    )
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
    )
}
