package app.pane.android.ui.viewer

/**
 * The post overflow, on every screen that has one.
 * Share, copy link, download when the post has media, and add note.
 * Opening the source app stays on the coral button.
 */
internal enum class OverflowAction {
    Share,
    CopyLink,
    Download,
    AddNote,
}

internal fun overflowActions(canDownload: Boolean): List<OverflowAction> = buildList {
    add(OverflowAction.Share)
    add(OverflowAction.CopyLink)
    if (canDownload) add(OverflowAction.Download)
    add(OverflowAction.AddNote)
}
