package app.pane.android.ui.player

/**
 * Spare height under wide media, in the same pixels as the viewport.
 * Tall media that already fills most of a portrait screen stays full-bleed.
 */
internal fun spareBelowPeekPx(
    viewportWidthPx: Float,
    viewportHeightPx: Float,
    contentWidthPx: Float,
    contentHeightPx: Float,
): Float {
    if (viewportWidthPx <= 1f || viewportHeightPx <= 1f) return 0f
    if (contentWidthPx <= 1f || contentHeightPx <= 1f) return 0f
    if (mediaFillsPortrait(viewportWidthPx, viewportHeightPx, contentWidthPx, contentHeightPx)) return 0f
    val fittedHeight = viewportWidthPx * (contentHeightPx / contentWidthPx)
    if (!fittedHeight.isFinite() || fittedHeight <= 0f) return 0f
    return (viewportHeightPx - fittedHeight).coerceAtLeast(0f)
}

/**
 * Tall media whose fitted height would cover most of a portrait phone.
 * Wide media leaves a letterbox, so the framed post can use that space.
 */
internal fun mediaFillsPortrait(
    viewportWidthPx: Float,
    viewportHeightPx: Float,
    contentWidthPx: Float,
    contentHeightPx: Float,
): Boolean {
    if (viewportWidthPx <= 1f || viewportHeightPx <= 1f) return false
    if (contentWidthPx <= 1f || contentHeightPx <= 1f) return false
    val fittedHeight = viewportWidthPx * (contentHeightPx / contentWidthPx)
    return fittedHeight.isFinite() && fittedHeight >= viewportHeightPx * PORTRAIT_FILL_FRACTION
}

/** Where a fullscreen scrubber drag should land, in milliseconds. */
internal fun seekPositionMs(durationMs: Long, fraction: Float): Long {
    if (durationMs <= 0L) return 0L
    return (durationMs * fraction.coerceIn(0f, 1f)).toLong().coerceIn(0L, durationMs)
}

/** Scrubber position. An unknown duration stays at the start. */
internal fun playbackFraction(positionMs: Long, durationMs: Long): Float {
    if (durationMs <= 0L || positionMs <= 0L) return 0f
    return (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
}

/** Clock label beside the fullscreen scrubber. */
internal fun formatPlaybackClock(positionMs: Long): String {
    val totalSeconds = positionMs.coerceAtLeast(0L) / 1_000L
    val hours = totalSeconds / 3_600
    val minutes = (totalSeconds % 3_600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(java.util.Locale.US, hours, minutes, seconds)
    } else {
        "%d:%02d".format(java.util.Locale.US, minutes, seconds)
    }
}

/**
 * Swipe-down does not leave fullscreen. Sideways movement stays a gallery swipe.
 * The parameters stay so a later lock can tell a vertical drag from a sideways one.
 */
internal fun swipeExitsFullscreen(
    @Suppress("UNUSED_PARAMETER") totalDx: Float,
    @Suppress("UNUSED_PARAMETER") totalDy: Float,
    @Suppress("UNUSED_PARAMETER") thresholdPx: Float,
): Boolean = false

private const val PORTRAIT_FILL_FRACTION = 0.72f

/** Photos and video open on the picture, with the comments sheet lowered. */
internal fun playerShowsControlsOnOpen(): Boolean = false

/** Swiping onto a video brings the controls back. Image pages stay on the picture. */
internal fun playerShowsControlsAfterPageChange(videoUrl: String?): Boolean = !videoUrl.isNullOrBlank()

/** A mostly upward swipe raises comments. A mostly sideways move stays a gallery swipe. */
internal fun swipeRaisesComments(totalDx: Float, totalDy: Float, thresholdPx: Float): Boolean {
    if (thresholdPx <= 0f) return false
    if (kotlin.math.abs(totalDx) > kotlin.math.abs(totalDy)) return false
    return totalDy <= -thresholdPx
}

internal enum class CommentsRaise { ShowPeek, Expand, None }

/** Swipe up matches the tap that raises the sheet, then opens the rest of the comments. */
internal fun commentsRaiseForSwipe(commentsLowered: Boolean, sheetExpanded: Boolean): CommentsRaise = when {
    sheetExpanded -> CommentsRaise.None
    commentsLowered -> CommentsRaise.ShowPeek
    else -> CommentsRaise.Expand
}
