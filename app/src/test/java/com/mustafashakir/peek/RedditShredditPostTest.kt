package com.mustafashakir.peek

import com.mustafashakir.peek.data.reddit.RedditShredditPost
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RedditShredditPostTest {
    @Test
    fun readsAPublicImagePostFromTheServerRenderedElement() {
        val html = """
            <shreddit-post id="t3_1w3fcl7" post-title="In 1960, David Latimer planted a garden"
              author="The_Love-Tap" subreddit-name="interestingasfuck" score="45372"
              comment-count="676" created-timestamp="2026-08-31T14:25:05.266000+0000"
              content-href="https://i.redd.it/h190t4tyzpmh1.jpeg" post-type="image">
            </shreddit-post>
        """.trimIndent()

        val post = RedditShredditPost.parse(html)

        assertEquals("1w3fcl7", post?.id)
        assertEquals("The_Love-Tap", post?.author)
        assertEquals("interestingasfuck", post?.subreddit)
        assertEquals(45372, post?.score)
        assertEquals("https://i.redd.it/h190t4tyzpmh1.jpeg", post?.media?.single()?.imageUrl)
        assertTrue(post?.createdUtcEpochSeconds ?: 0L > 0L)
    }

    @Test
    fun failsClosedForQuarantineAndRemovedPosts() {
        assertNull(
            RedditShredditPost.parse("""<shreddit-post id="t3_abc" post-title="Hidden" quarantine="true"></shreddit-post>"""),
        )
        assertNull(
            RedditShredditPost.parse("""<shreddit-post id="t3_abc" post-title="[removed]"></shreddit-post>"""),
        )
        assertNull(RedditShredditPost.parse("<html><title>Blocked</title></html>"))
    }
}
