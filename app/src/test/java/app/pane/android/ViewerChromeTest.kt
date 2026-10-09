package app.pane.android

import app.pane.android.ui.viewer.ViewerChromeControl
import app.pane.android.ui.viewer.viewerTopChrome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ViewerChromeTest {
    @Test
    fun postViewerChromeIsDoneAndRefreshWithoutTheHost() {
        val chrome = viewerTopChrome()
        assertEquals(
            listOf(ViewerChromeControl.Done, ViewerChromeControl.Star, ViewerChromeControl.Refresh),
            chrome,
        )
        listOf(
            "m.facebook.com",
            "facebook.com",
            "instagram.com",
            "x.com",
            "reddit.com",
            "www.reddit.com",
        ).forEach { host ->
            assertFalse(chrome.any { control -> control.name.equals(host, ignoreCase = true) })
            assertFalse(host in chrome.joinToString(" ") { it.name })
        }
    }
}
