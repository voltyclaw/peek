package app.pane.android.ui.navigation

/**
 * How system and on-screen Back move through Pane.
 * The immersive player sits above the framed post. Back from that layer pops to the post.
 * A post opened from another app finishes so that app resumes.
 * A post opened from the hub pops back to the hub.
 * Home itself is not finished here; the platform back dispatcher does that.
 */
internal enum class PeekBackAction { Pop, Finish, DeferToSystem, ClearToHome }

/**
 * [topIsPlayer] is the immersive layer. Back from there returns to the framed post.
 * Share-in Done and Back finish the activity. Hub-opened Done and Back pop one entry.
 */
internal fun peekBackAction(
    launchedFromViewLink: Boolean,
    stackSize: Int,
    topIsPlayer: Boolean = false,
): PeekBackAction = when {
    topIsPlayer && stackSize > 1 -> PeekBackAction.Pop
    launchedFromViewLink && stackSize > 1 -> PeekBackAction.Finish
    stackSize > 1 -> PeekBackAction.Pop
    else -> PeekBackAction.DeferToSystem
}

/**
 * End-of-video Leave returns to the app that opened Pane.
 * A session started from the launcher or Home closes the post and lands on Home.
 * Overflow Leave is not a separate control in this shell.
 */
internal fun peekLeaveVideoAction(launchedFromViewLink: Boolean): PeekBackAction =
    if (launchedFromViewLink) PeekBackAction.Finish else PeekBackAction.ClearToHome
