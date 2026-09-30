package app.pane.android

import app.pane.android.data.reddit.RedditUrls
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RedditUrlsTest {
    @Test
    fun matchesPublicPostHostsAndNormalizesThePostId() {
        val expected = "https://www.reddit.com/comments/abc123/"
        val urls = listOf(
            "https://www.reddit.com/r/pics/comments/abc123/a_title/",
            "https://reddit.com/r/pics/comments/abc123/a_title",
            "https://old.reddit.com/r/pics/comments/ABC123/a_title/?utm_source=share",
            "https://np.reddit.com/r/pics/comments/abc123/a_title/def456/",
            "https://new.reddit.com/comments/abc123",
            "https://m.reddit.com/r/pics/comments/abc123.json",
            "https://www.reddit.com/gallery/abc123",
            "https://redd.it/abc123",
            "https://www.redd.it/abc123/",
        )

        urls.forEach { url ->
            val direct = RedditUrls.direct(url)
            assertEquals(url, "abc123", direct?.id)
            assertEquals(url, expected, direct?.canonicalUrl)
        }
        assertEquals(
            "https://old.reddit.com/comments/abc123.json?raw_json=1&limit=50",
            RedditUrls.jsonUrl("ABC123"),
        )
    }

    @Test
    fun recognizesShareLinksWithoutTreatingThemAsPostIds() {
        val share = "https://www.reddit.com/r/pics/s/Ab12Cd/?utm_source=share"
        val pasted = "https://www.reddit.com/r/interestingasfuck/s/VDgXcEIG1q"

        assertTrue(RedditUrls.isShareLink(share))
        assertTrue(RedditUrls.supports(pasted))
        assertTrue(RedditUrls.isShareLink(pasted))
        assertNull(RedditUrls.direct(pasted))
        assertNull(RedditUrls.direct(share))
        assertTrue(RedditUrls.supports(share))
        assertFalse(RedditUrls.isShareLink("https://redd.it/abc123"))
    }

    @Test
    fun classifiesAPastedCommentsUrlAndRejectsTheSubredditHome() {
        val pasted = "https://www.reddit.com/r/interestingasfuck/comments/1w3fcl7/" +
            "in_1960_david_latimer_planted_a_garden_inside_of/?utm_source=share&utm_medium=android_app"
        val direct = RedditUrls.direct(pasted)

        assertTrue(RedditUrls.supports(pasted))
        assertFalse(RedditUrls.isShareLink(pasted))
        assertEquals("1w3fcl7", direct?.id)
        assertEquals("https://www.reddit.com/comments/1w3fcl7/", direct?.canonicalUrl)
        val path = "/r/interestingasfuck/comments/1w3fcl7/in_1960_david_latimer_planted_a_garden_inside_of"
        assertEquals(path, RedditUrls.commentsPath(pasted))
        assertEquals(
            listOf(
                "https://old.reddit.com$path.json?raw_json=1&limit=50",
                "https://www.reddit.com$path.json?raw_json=1&limit=50",
                "https://old.reddit.com/comments/1w3fcl7.json?raw_json=1&limit=50",
                "https://www.reddit.com/comments/1w3fcl7.json?raw_json=1&limit=50",
            ),
            RedditUrls.jsonCandidates("1w3fcl7", path),
        )
        assertFalse(RedditUrls.supports("https://www.reddit.com/r/interestingasfuck"))
        assertFalse(RedditUrls.supports("https://www.reddit.com/r/interestingasfuck/hot"))
        assertFalse(RedditUrls.supports("https://www.reddit.com/user/The_Love-Tap"))
    }

    @Test
    fun rejectsFeedsProfilesMediaHostsAndInsecureUrls() {
        val rejected = listOf(
            "https://www.reddit.com/r/pics",
            "https://www.reddit.com/r/pics/hot",
            "https://www.reddit.com/user/spez",
            "https://www.reddit.com/u/spez",
            "https://i.redd.it/photo.jpg",
            "https://v.redd.it/video",
            "http://www.reddit.com/r/pics/comments/abc123/title/",
            "https://example.com/r/pics/comments/abc123/title/",
            "https://redd.it/",
        )

        rejected.forEach { url ->
            assertFalse(url, RedditUrls.supports(url))
        }
    }
}
