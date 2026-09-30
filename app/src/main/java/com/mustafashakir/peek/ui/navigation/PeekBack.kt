package com.mustafashakir.peek.ui.navigation

/**
 * How system and on-screen Back move through Peek.
 * A link opened from another app leaves with [PeekBackAction.Finish] so that app resumes.
 * A session started from the launcher pops the player back to the preview, then Home.
 * Home itself is not finished here; the platform back dispatcher does that.
 */
internal enum class PeekBackAction { Pop, Finish, DeferToSystem }

internal fun peekBackAction(launchedFromViewLink: Boolean, stackSize: Int): PeekBackAction = when {
    launchedFromViewLink && stackSize > 1 -> PeekBackAction.Finish
    stackSize > 1 -> PeekBackAction.Pop
    else -> PeekBackAction.DeferToSystem
}
