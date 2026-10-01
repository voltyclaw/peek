package app.pane.android

import app.pane.android.ui.actions.OpenInAppTarget
import app.pane.android.ui.actions.openInAppPackages
import app.pane.android.ui.actions.openInAppTargets
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
        assertEquals(
            listOf("com.instagram.android"),
            openInAppPackages("https://m.instagram.com/reel/abc123/"),
        )
        assertTrue(openInAppPackages("https://example.com/post").isEmpty())
    }

    @Test
    fun redditOpensTheOfficialAppThenTheBrowser() {
        assertEquals(
            listOf(
                OpenInAppTarget("https://www.reddit.com/r/pics/comments/abc123/title", "com.reddit.frontpage"),
                OpenInAppTarget("reddit://reddit/r/pics/comments/abc123/title", "com.reddit.frontpage"),
                OpenInAppTarget("https://www.reddit.com/r/pics/comments/abc123/title", null),
            ),
            openInAppTargets("https://old.reddit.com/r/pics/comments/abc123/title/"),
        )
    }

    @Test
    fun xFacebookAndInstagramUseTheSameAttemptShape() {
        assertEquals(
            listOf(
                OpenInAppTarget("https://x.com/i/status/20", "com.twitter.android"),
                OpenInAppTarget("twitter://status?id=20", "com.twitter.android"),
                OpenInAppTarget("https://x.com/i/status/20", null),
            ),
            openInAppTargets("https://x.com/jack/status/20"),
        )
        assertEquals(
            "com.facebook.katana",
            openInAppTargets("https://m.facebook.com/nasa/posts/pfbid0123").first().packageName,
        )
        assertEquals(
            "https://www.instagram.com/reel/abc123/",
            openInAppTargets("https://m.instagram.com/reel/abc123/").first().uri,
        )
        assertEquals(
            "com.instagram.android",
            openInAppTargets("https://l.instagram.com/?u=https%3A%2F%2Fwww.instagram.com%2Fp%2Fabc123%2F").first().packageName,
        )
    }
}
