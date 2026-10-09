package app.pane.android

import app.pane.android.ui.viewer.OverflowAction
import app.pane.android.ui.viewer.overflowActions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ViewerOverflowTest {
    @Test
    fun everySourceHasTheSameOverflow() {
        val withMedia = listOf(
            OverflowAction.Share,
            OverflowAction.CopyLink,
            OverflowAction.Download,
            OverflowAction.AddNote,
        )
        val withoutMedia = listOf(
            OverflowAction.Share,
            OverflowAction.CopyLink,
            OverflowAction.AddNote,
        )
        listOf("x", "instagram", "facebook", "reddit", "generic").forEach { source ->
            assertEquals(source, withMedia, overflowActions(canDownload = true))
            assertEquals(source, withoutMedia, overflowActions(canDownload = false))
        }
        assertFalse(overflowActions(true).any { it.name.contains("Open", ignoreCase = true) })
        assertFalse(overflowActions(false).any { it.name.contains("Open", ignoreCase = true) })
    }

    @Test
    fun redditTextHidesDownloadAndAMediaPostShowsIt() {
        val redditText = overflowActions(canDownload = false)
        val mediaPost = overflowActions(canDownload = true)
        assertEquals(
            listOf(OverflowAction.Share, OverflowAction.CopyLink, OverflowAction.AddNote),
            redditText,
        )
        assertEquals(
            listOf(OverflowAction.Share, OverflowAction.CopyLink, OverflowAction.Download, OverflowAction.AddNote),
            mediaPost,
        )
        assertFalse(redditText.any { it.name.contains("Open", ignoreCase = true) })
        assertFalse(mediaPost.any { it.name.contains("Open", ignoreCase = true) })
    }
}
