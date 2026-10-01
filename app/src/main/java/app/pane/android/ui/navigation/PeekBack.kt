package app.pane.android.ui.navigation

/**
 * How system and on-screen Back move through Pane.
 * The immersive player sits above the framed post (stack size greater than 2).
 * Back from that layer always pops to the framed post.
 * With [backClosesPeek], a framed post opened from another app finishes so that app resumes.
 * A session started from the launcher pops toward Home.
 * Home itself is not finished here; the platform back dispatcher does that.
 */
internal enum class PeekBackAction { Pop, Finish, DeferToSystem }

internal fun peekBackAction(
    launchedFromViewLink: Boolean,
    stackSize: Int,
    backClosesPeek: Boolean = true,
): PeekBackAction = when {
    stackSize > 2 -> PeekBackAction.Pop
    backClosesPeek && launchedFromViewLink && stackSize > 1 -> PeekBackAction.Finish
    stackSize > 1 -> PeekBackAction.Pop
    else -> PeekBackAction.DeferToSystem
}
