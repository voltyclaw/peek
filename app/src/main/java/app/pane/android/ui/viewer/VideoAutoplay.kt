package app.pane.android.ui.viewer

import app.pane.android.ui.model.UiImage
import app.pane.android.ui.model.ViewerPostUiModel
import app.pane.android.ui.model.mediaItemsOrPrimary
import app.pane.android.ui.player.mediaFillsPortrait
import java.net.URI
import java.util.Locale

internal object VideoAutoplay {
    /**
     * Tall media opens immersive first. Wide media stays on the framed screen,
     * where the leftover space holds the caption. Returning from immersive
     * does not send the reader back into fullscreen.
     */
    fun shouldOpen(
        alreadyOpened: Boolean,
        contentWidthPx: Float,
        contentHeightPx: Float,
        viewportWidthPx: Float,
        viewportHeightPx: Float,
    ): Boolean {
        if (alreadyOpened) return false
        return mediaFillsPortrait(
            viewportWidthPx,
            viewportHeightPx,
            contentWidthPx,
            contentHeightPx,
        )
    }
}

internal fun ViewerPostUiModel.hasVisualMedia(): Boolean =
    mediaItemsOrPrimary().any { item ->
        !item.videoUrl.isNullOrBlank() || item.image.isDisplayablePicture()
    }

private fun UiImage.isDisplayablePicture(): Boolean = when (this) {
    is UiImage.Resource -> true
    is UiImage.Url -> isDirectPictureUrl(value)
}

internal fun isDirectPictureUrl(url: String): Boolean {
    if (!url.startsWith("https://")) return false
    val host = runCatching { URI(url).host }.getOrNull()?.lowercase(Locale.US).orEmpty()
    if (host == "reddit.com" || host.endsWith(".reddit.com") || host == "redd.it" || host == "www.redd.it") {
        return false
    }
    return host.isNotEmpty()
}
