package app.pane.android.ui.player

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
