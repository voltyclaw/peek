package app.pane.android

import app.pane.android.data.facebook.FacebookUrls
import app.pane.android.data.instagram.InstagramStories
import app.pane.android.ui.actions.openInAppTargets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StoryHandoffTest {
    @Test
    fun shareTextKeepsFacebookAndInstagramStoryUrls() {
        val facebook = "https://www.facebook.com/stories/100064321098765/UzpfSVEwMDA2NDMyMTA5ODc2NToxMjM0NTY3ODkwMTIzNDU2/" +
            "?view_single=1&bucket_id=100064321098765&story_fbid=1234567890123456&fs=e&mibextid=wwXIfr"
        assertEquals(
            facebook,
            IncomingLink.urlFrom(
                action = "android.intent.action.SEND",
                dataString = null,
                extraText = "Maya shared a story $facebook",
                extraHtml = null,
            ),
        )

        val instagram = "https://www.instagram.com/stories/maya.ren/3456789012345678901/" +
            "?utm_source=ig_story_item_share&igsh=MWRxYzEyMzQ1Njc4OQ=="
        assertEquals(
            instagram,
            IncomingLink.urlFrom(
                action = "android.intent.action.SEND",
                dataString = null,
                extraText = null,
                extraHtml = """<a href="$instagram">story</a>""",
            ),
        )
    }

    @Test
    fun viewPrefersTheFullStoryOverAShortWatchLink() {
        val story = "https://www.facebook.com/stories/100064321098765/UzpfSVsampleStory1/" +
            "?view_single=1&bucket_id=100064321098765&story_fbid=sampleStory1&mibextid=wwXIfr"
        assertEquals(
            story,
            IncomingLink.urlFrom(
                action = "android.intent.action.VIEW",
                dataString = "https://fb.watch/aBcDeFgHiJ/",
                extraText = "Open the story $story",
                extraHtml = null,
            ),
        )
    }

    @Test
    fun facebookStoryOpenKeepsTheShareUrl() {
        val stories = "https://www.facebook.com/stories/100064321098765/UzpfSVsampleStory1/" +
            "?view_single=1&bucket_id=100064321098765&story_fbid=sampleStory1&fs=e&mibextid=wwXIfr"
        val storyPhp = "https://www.facebook.com/story.php?story_fbid=pfbid0AbCdEfGhIjKlMnOp" +
            "&id=100064321098765&bucket_id=100064321098765&mibextid=wwXIfr"
        val watch = "https://fb.watch/aBcDeFgHiJ/?story_fbid=998877665544&id=100064321098765" +
            "&bucket_id=100064321098765&mibextid=wwXIfr"
        val share = "https://www.facebook.com/share/s/AbCdEfGh/?mibextid=wwXIfr"

        listOf(stories, storyPhp, watch, share).forEach { url ->
            val parsed = FacebookUrls.parse(url)
            assertEquals(url, FacebookUrls.Kind.Story, parsed?.kind)
            assertEquals(url, parsed?.sourceUrl)
            val targets = openInAppTargets(url)
            assertEquals(listOf("com.facebook.katana", "com.facebook.lite"), targets.mapNotNull { it.packageName })
            assertTrue(targets.all { it.uri == url })
            assertTrue(targets.last().handoffBrowser)
            assertFalse(targets.any { it.uri.contains("permalink.php") })
            assertTrue(url.substringAfter('?').split('&').all { param -> param in targets.first().uri })
        }
    }

    @Test
    fun instagramStoryOpenKeepsTheShareQuery() {
        val url = "https://www.instagram.com/stories/maya.ren/3456789012345678901/" +
            "?utm_source=ig_story_item_share&igsh=MWRxYzEyMzQ1Njc4OQ=="
        val story = InstagramStories.parse(url)
        assertEquals("maya.ren", story?.username)
        assertEquals("3456789012345678901", story?.mediaId)
        val targets = openInAppTargets(url)
        assertEquals("com.instagram.android", targets.first().packageName)
        assertTrue(targets.first().uri.contains("/stories/maya.ren/3456789012345678901"))
        assertTrue(targets.first().uri.contains("igsh=MWRxYzEyMzQ1Njc4OQ=="))
        assertTrue(targets.first().uri.contains("utm_source=ig_story_item_share"))
        assertFalse(targets.first().uri.contains("/p/") || targets.first().uri.contains("/reel/"))
        assertTrue(targets.last().handoffBrowser)
        assertEquals(targets.first().uri, targets.last().uri)
    }

    @Test
    fun aNormalPostIsStillNotHandedOffAsAStory() {
        val targets = openInAppTargets("https://m.facebook.com/nasa/posts/pfbid0123")
        assertEquals("https://www.facebook.com/nasa/posts/pfbid0123", targets.first().uri)
        assertFalse(targets.last().handoffBrowser)
        assertEquals(FacebookUrls.Kind.Post, FacebookUrls.parse("https://www.facebook.com/permalink.php?story_fbid=555&id=9")?.kind)
    }
}
