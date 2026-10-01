package app.pane.android

import app.pane.android.data.reddit.RedditUrls
import app.pane.android.domain.usecase.ExtractUrlFromTextUseCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Paste does not consult the manifest intent filters. It extracts an https URL and the
 * shared router classifies it with the same [RedditUrls.supports] check used for open-with.
 */
class RedditPasteClassificationTest {
    private val extractUrl = ExtractUrlFromTextUseCase()

    @Test
    fun pastedCommentsUrlIsARedditPost() {
        val clipboard = "In 1960, David Latimer planted a garden " +
            "https://www.reddit.com/r/interestingasfuck/comments/1w3fcl7/" +
            "in_1960_david_latimer_planted_a_garden_inside_of/?utm_source=share&utm_medium=android_app"
        val url = extractUrl(clipboard)

        assertEquals(
            "https://www.reddit.com/r/interestingasfuck/comments/1w3fcl7/" +
                "in_1960_david_latimer_planted_a_garden_inside_of/?utm_source=share&utm_medium=android_app",
            url,
        )
        assertTrue(RedditUrls.supports(url!!))
        assertEquals("1w3fcl7", RedditUrls.direct(url)?.id)
        assertFalse(RedditUrls.isShareLink(url))
    }

    @Test
    fun pastedShareShortlinkIsRecognizedButIsNotYetAPostId() {
        val url = extractUrl("https://www.reddit.com/r/interestingasfuck/s/VDgXcEIG1q")

        assertTrue(RedditUrls.supports(url!!))
        assertTrue(RedditUrls.isShareLink(url))
        assertEquals(null, RedditUrls.direct(url))
    }

    @Test
    fun nonPostRedditUrlsFailClosedBeforeAFetch() {
        val rejected = listOf(
            "https://www.reddit.com/r/interestingasfuck",
            "https://www.reddit.com/r/interestingasfuck/hot",
            "https://www.reddit.com/user/The_Love-Tap",
            "https://i.redd.it/h190t4tyzpmh1.jpeg",
        )

        rejected.forEach { clipboard ->
            val url = extractUrl(clipboard)
            assertEquals(clipboard, url)
            assertFalse(clipboard, RedditUrls.supports(url!!))
        }
    }
}
