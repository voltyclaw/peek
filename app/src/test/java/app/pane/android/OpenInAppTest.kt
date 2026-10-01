package app.pane.android

import app.pane.android.ui.actions.openInAppPackages
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenInAppTest {
    @Test
    fun eachSourcePrefersItsInstalledApp() {
        assertEquals(listOf("com.twitter.android"), openInAppPackages("https://x.com/jack/status/20"))
        assertEquals(
            listOf("com.facebook.katana", "com.facebook.lite"),
            openInAppPackages("https://www.facebook.com/nasa/posts/pfbid0123"),
        )
        assertEquals(
            listOf("com.reddit.frontpage"),
            openInAppPackages("https://www.reddit.com/r/pics/comments/abc123/title/"),
        )
        assertEquals(
            listOf("com.instagram.android"),
            openInAppPackages("https://www.instagram.com/p/abc123/"),
        )
        assertTrue(openInAppPackages("https://example.com/post").isEmpty())
    }
}
