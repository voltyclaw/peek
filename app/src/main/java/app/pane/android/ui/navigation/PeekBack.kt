package app.pane.android.ui.navigation

/**
 * How system and on-screen Back move through Pane.
 * The immersive player sits above the framed post (stack size greater than 2).
 * Back from that layer always pops to the framed post.
 * With [backClosesPeek], a framed post opened from another app finishes so that app resumes.
 * When Back goes home, one Back clears every post above Home.
 * A session started from the launcher pops toward Home.
 * Home itself is not finished here; the platform back dispatcher does that.
 */
internal enum class PeekBackAction { Pop, Finish, DeferToSystem, ClearToHome }

/**
 * [topIsPlayer] is the immersive layer. Back from there always returns to the framed post.
 * When Back is set to go home, one Back from a post clears every post in between and lands on Home.
 */
internal fun peekBackAction(
    launchedFromViewLink: Boolean,
    stackSize: Int,
    backClosesPeek: Boolean = true,
    topIsPlayer: Boolean = false,
): PeekBackAction = when {
    topIsPlayer && stackSize > 1 -> PeekBackAction.Pop
    !backClosesPeek && stackSize > 1 -> PeekBackAction.ClearToHome
    backClosesPeek && launchedFromViewLink && stackSize == 2 -> PeekBackAction.Finish
    stackSize > 1 -> PeekBackAction.Pop
    else -> PeekBackAction.DeferToSystem
}

/**
 * End-of-video Leave returns to the app that opened Pane.
 * A session started from the launcher or Home closes the post and lands on Home.
 * The overflow Leave control is separate and always clears to Home.
 */
internal fun peekLeaveVideoAction(launchedFromViewLink: Boolean): PeekBackAction =
    if (launchedFromViewLink) PeekBackAction.Finish else PeekBackAction.ClearToHome
