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
    val fittedHeight = viewportWidthPx * (contentHeightPx / contentWidthPx)
    if (!fittedHeight.isFinite() || fittedHeight <= 0f) return 0f
    if (fittedHeight >= viewportHeightPx * FULL_BLEED_FILL) return 0f
    return (viewportHeightPx - fittedHeight).coerceAtLeast(0f)
}

private const val FULL_BLEED_FILL = 0.72f

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
