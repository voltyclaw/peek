package com.mustafashakir.peek

import com.mustafashakir.peek.data.reddit.RedditBrowserNavigation
import com.mustafashakir.peek.data.reddit.RedditPageDocument
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RedditPageDocumentTest {
    @Test
    fun readsShredditPostAndNestedComments() {
        val read = RedditPageDocument.read(SHREDDIT_PAGE) as RedditPageDocument.Read.Ready

        assertEquals("1w3fcl7", read.post.id)
        assertEquals("The_Love-Tap", read.post.author)
        assertEquals("https://i.redd.it/h190t4tyzpmh1.jpeg", read.post.media.single().imageUrl)
        assertEquals("Sealed in 1972.", read.post.comments.single().body)
        assertTrue(read.post.comments.single().isSubmitter)
        assertEquals("No bugs?", read.post.comments.single().replies.single().body)
        assertFalse(read.post.comments.single().replies.single().isSubmitter)
    }

    @Test
    fun readsATextBodyFromThePostSlot() {
        val html = """
            <shreddit-post id="t3_txt111" post-title="A question" author="alice"
              subreddit-name="ask" score="4" comment-count="0" post-type="text">
              <div slot="text-body"><p>Hello there</p></div>
            </shreddit-post>
        """.trimIndent()

        val read = RedditPageDocument.read(html) as RedditPageDocument.Read.Ready

        assertEquals("Hello there", read.post.selfText)
        assertTrue(read.post.comments.isEmpty())
    }

    @Test
    fun prefersEmbeddedListingJsonIncludingWindowR() {
        val read = RedditPageDocument.read(WINDOW_R_PAGE) as RedditPageDocument.Read.Ready

        assertEquals("abc123", read.post.id)
        assertEquals("From JSON", read.post.title)
        assertEquals("Nice shot", read.post.comments.single().body)
        assertEquals("https://i.redd.it/photo.jpg", read.post.media.single().imageUrl)
    }

    @Test
    fun readsAListingNestedInNextData() {
        val html = """
            <script id="__NEXT_DATA__" type="application/json">
              {"props":{"pageProps":{"listings":$LISTING}}}
            </script>
        """.trimIndent()

        val read = RedditPageDocument.read(html) as RedditPageDocument.Read.Ready

        assertEquals("abc123", read.post.id)
        assertEquals("Nice shot", read.post.comments.single().body)
    }

    @Test
    fun mergesHtmlCommentsWhenEmbeddedJsonHasNone() {
        val html = """
            <script type="application/json">
              [{"kind":"Listing","data":{"children":[{"kind":"t3","data":{
                "id":"abc123","title":"From JSON","author":"alice","subreddit":"pics",
                "score":10,"num_comments":1,"created_utc":1700000000,"is_self":true,
                "permalink":"/r/pics/comments/abc123/from_json/","selftext":"Body"
              }}]}}]
            </script>
            <shreddit-comment author="bob" depth="0" thingid="t1_c1" score="2">
              <div id="t1_c1-post-rtjson-content"><p>From the page</p></div>
            </shreddit-comment>
        """.trimIndent()

        val read = RedditPageDocument.read(html) as RedditPageDocument.Read.Ready

        assertEquals("Body", read.post.selfText)
        assertEquals("From the page", read.post.comments.single().body)
    }

    @Test
    fun failsClosedForQuarantinePrivateLoginAndBlockPages() {
        assertEquals(
            RedditPageDocument.HIDDEN,
            (RedditPageDocument.read("""<shreddit-post id="t3_abc" post-title="Hidden" quarantine="true"></shreddit-post>""") as RedditPageDocument.Read.Failed).reason,
        )
        assertEquals(
            RedditPageDocument.HIDDEN,
            (RedditPageDocument.read("<html><body>This community is private</body></html>") as RedditPageDocument.Read.Failed).reason,
        )
        assertEquals(
            RedditPageDocument.LOGIN,
            (RedditPageDocument.read("<html><body>Log in to continue<input type=\"password\"></body></html>") as RedditPageDocument.Read.Failed).reason,
        )
        assertEquals(
            RedditPageDocument.BLOCKED,
            (RedditPageDocument.read("<html><title>whoa there, pardner!</title></html>") as RedditPageDocument.Read.Failed).reason,
        )
        assertNull((RedditPageDocument.read("<html><body></body></html>") as? RedditPageDocument.Read.Ready))
        assertTrue(RedditPageDocument.read("<html><body></body></html>") is RedditPageDocument.Read.Pending)
    }

    @Test
    fun hiddenEmbeddedJsonDoesNotBecomeAPublicPost() {
        val html = """
            <script type="application/json">
              [{"kind":"Listing","data":{"children":[{"kind":"t3","data":{
                "id":"hid1","title":"Secret","author":"mod","subreddit":"secret",
                "quarantine":true,"score":1,"num_comments":0,"created_utc":1,"is_self":true,
                "permalink":"/r/secret/comments/hid1/secret/"
              }}]}}]
            </script>
        """.trimIndent()

        val read = RedditPageDocument.read(html)

        assertTrue(read is RedditPageDocument.Read.Failed)
        assertEquals(RedditPageDocument.HIDDEN, (read as RedditPageDocument.Read.Failed).reason)
    }

    @Test
    fun browserNavigationStaysOnPublicRedditPages() {
        assertTrue(RedditBrowserNavigation.isAllowed("https://www.reddit.com/r/pics/comments/abc123/title/"))
        assertTrue(RedditBrowserNavigation.isAllowed("https://old.reddit.com/r/pics/s/Ab12Cd"))
        assertTrue(RedditBrowserNavigation.isLogin("https://www.reddit.com/login/?dest=comments"))
        assertFalse(RedditBrowserNavigation.isAllowed("https://www.reddit.com/login/"))
        assertFalse(RedditBrowserNavigation.isAllowed("https://accounts.google.com/o/oauth2"))
        assertFalse(RedditBrowserNavigation.isAllowed("http://www.reddit.com/r/pics/comments/abc123/title/"))
    }
}

private const val LISTING = """
[{"kind":"Listing","data":{"children":[{"kind":"t3","data":{
  "id":"abc123","title":"From JSON","author":"alice","subreddit":"pics",
  "score":10,"num_comments":1,"created_utc":1700000000,"is_self":false,
  "permalink":"/r/pics/comments/abc123/from_json/","url":"https://i.redd.it/photo.jpg"
}}]}},{"kind":"Listing","data":{"children":[{"kind":"t1","data":{
  "id":"c1","author":"bob","body":"Nice shot","created_utc":1700000100,"is_submitter":false,"replies":""
}}]}}]
"""

private const val WINDOW_R_PAGE = """
<html><body>
<script>window.__r = $LISTING;</script>
</body></html>
"""

private const val SHREDDIT_PAGE = """
<shreddit-post id="t3_1w3fcl7" post-title="Bottle garden" author="The_Love-Tap"
  subreddit-name="interestingasfuck" score="45372" comment-count="2"
  created-timestamp="2026-08-31T14:25:05.266000+0000"
  content-href="https://i.redd.it/h190t4tyzpmh1.jpeg" post-type="image"
  permalink="/r/interestingasfuck/comments/1w3fcl7/bottle/">
</shreddit-post>
<shreddit-comment author="The_Love-Tap" created="2026-08-31T14:26:45.403000+0000"
  depth="0" permalink="/r/interestingasfuck/comments/1w3fcl7/comment/p6zj3fz/"
  score="4342" thingid="t1_p6zj3fz" is-op="">
  <div id="t1_p6zj3fz-post-rtjson-content"><p>Sealed in 1972.</p></div>
  <shreddit-comment author="bob" created="2026-08-31T15:00:00.000000+0000"
    depth="1" score="3" thingid="t1_child1">
    <div id="t1_child1-post-rtjson-content"><p>No bugs?</p></div>
  </shreddit-comment>
</shreddit-comment>
"""
