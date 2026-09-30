package app.pane.android.ui.viewer

import app.pane.android.ui.model.UiImage
import app.pane.android.ui.model.ViewerPostUiModel
import app.pane.android.ui.model.mediaItemsOrPrimary
import java.net.URI
import java.util.Locale

internal object VideoAutoplay {
    /** Video, photo, and gallery posts open on the media once. Text posts stay on the preview. */
    fun shouldOpen(alreadyOpened: Boolean, hasVisualMedia: Boolean): Boolean =
        !alreadyOpened && hasVisualMedia
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
