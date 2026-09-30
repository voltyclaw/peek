package app.pane.android.ui.navigation

/**
 * How system and on-screen Back move through Peek.
 * With [backClosesPeek], a link opened from another app leaves with [PeekBackAction.Finish]
 * so that app resumes. Otherwise Back pops toward Home and stays in Peek.
 * A session started from the launcher always pops the player back to the preview, then Home.
 * Home itself is not finished here; the platform back dispatcher does that.
 */
internal enum class PeekBackAction { Pop, Finish, DeferToSystem }

internal fun peekBackAction(
    launchedFromViewLink: Boolean,
    stackSize: Int,
    backClosesPeek: Boolean = true,
): PeekBackAction = when {
    backClosesPeek && launchedFromViewLink && stackSize > 1 -> PeekBackAction.Finish
    stackSize > 1 -> PeekBackAction.Pop
    else -> PeekBackAction.DeferToSystem
}
