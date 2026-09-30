package com.mustafashakir.peek

import com.mustafashakir.peek.data.reddit.RedditSharePage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RedditSharePageTest {
    @Test
    fun prefersCanonicalOverLaterCommentLinks() {
        val html = """
            <link rel="canonical" href="https://www.reddit.com/r/interestingasfuck/comments/1wubxdy/title/">
            <a href="https://www.reddit.com/r/PassportPorn/comments/1vslmrg/other/">related</a>
        """.trimIndent()

        assertEquals(
            "https://www.reddit.com/comments/1wubxdy/",
            RedditSharePage.postUrl(html),
        )
    }

    @Test
    fun readsOgUrlMetaRefreshJavascriptAndPermalink() {
        assertEquals(
            "https://www.reddit.com/comments/ogurl1/",
            RedditSharePage.postUrl(
                """<meta property="og:url" content="https://www.reddit.com/r/pics/comments/ogurl1/title/">""",
            ),
        )
        assertEquals(
            "https://www.reddit.com/comments/refresh1/",
            RedditSharePage.postUrl(
                """<meta http-equiv="refresh" content="0;url=/r/pics/comments/refresh1/title/">""",
            ),
        )
        assertEquals(
            "https://www.reddit.com/comments/jsloc1/",
            RedditSharePage.postUrl(
                """<script>window.location.replace("https://www.reddit.com/r/pics/comments/jsloc1/title/");</script>""",
            ),
        )
        assertEquals(
            "https://www.reddit.com/comments/perm111/",
            RedditSharePage.postUrl(
                """<shreddit-post permalink="/r/pics/comments/perm111/title/"></shreddit-post>""",
            ),
        )
    }

    @Test
    fun decodesHtmlEntitiesInTheDestination() {
        val html = """
            <link href="https://www.reddit.com/r/pics/comments/amp111/title/?share_id=a&amp;utm_source=share" rel="canonical">
        """.trimIndent()

        assertEquals("https://www.reddit.com/comments/amp111/", RedditSharePage.postUrl(html))
    }

    @Test
    fun returnsNullWhenThePageHasNoPublicPost() {
        assertNull(RedditSharePage.postUrl("<html><title>Blocked</title><p>whoa there, pardner</p></html>"))
        assertNull(RedditSharePage.postUrl("<html><a href=\"https://www.reddit.com/r/pics\">home</a></html>"))
    }
}
