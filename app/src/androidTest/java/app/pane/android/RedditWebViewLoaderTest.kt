package app.pane.android

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.pane.android.data.reddit.AndroidRedditPageLoader
import app.pane.android.data.reddit.RedditFetchPlan
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RedditWebViewLoaderTest {
    @Test
    fun hiddenWebViewUsesDesktopUserAgentAndReadsARenderedPost() = runBlocking {
        val userAgent = RedditFetchPlan.DESKTOP_USER_AGENT
        assertTrue(userAgent.contains("Windows NT"))
        assertTrue(userAgent.contains("Chrome/"))
        assertFalse(userAgent.contains("Android"))
        assertFalse(userAgent.contains("Mobile"))

        val loader = AndroidRedditPageLoader(
            context = ApplicationProvider.getApplicationContext(),
            timeoutMillis = 5_000,
            pollIntervalMillis = 50,
        )
        val post = loader.loadHtmlForTesting(
            """
            <!doctype html><html><body>
              <shreddit-post id="t3_abc123" post-title="Desktop page" author="alice"
                subreddit-name="pics" score="12" comment-count="1" post-type="image"
                content-href="https://i.redd.it/photo.jpg"></shreddit-post>
              <shreddit-comment author="bob" depth="0" score="2" thingid="t1_c1">
                <div id="t1_c1-post-rtjson-content"><p>Rendered comment</p></div>
              </shreddit-comment>
            </body></html>
            """.trimIndent(),
        )

        assertEquals("abc123", post.id)
        assertEquals("alice", post.author)
        assertEquals("https://i.redd.it/photo.jpg", post.media.single().imageUrl)
        assertEquals("Rendered comment", post.comments.single().body)
    }
}
