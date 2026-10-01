package app.pane.android

import app.pane.android.ui.actions.sharePostText
import org.junit.Assert.assertEquals
import org.junit.Test

class SharePostTest {
    @Test
    fun shareTextKeepsThePostUrlAndAddsATitleWhenItIsDifferent() {
        assertEquals(
            "A quiet morning\nhttps://www.reddit.com/r/pics/comments/abc/title/",
            sharePostText(
                url = "https://www.reddit.com/r/pics/comments/abc/title/",
                title = "A quiet morning",
            ),
        )
    }

    @Test
    fun shareTextIsJustTheUrlWhenThereIsNoSeparateTitle() {
        val url = "https://www.instagram.com/p/abc123/"
        assertEquals(url, sharePostText(url, null))
        assertEquals(url, sharePostText(url, "  "))
        assertEquals(url, sharePostText(url, url))
    }
}
