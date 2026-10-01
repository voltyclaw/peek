package app.pane.android

import app.pane.android.data.x.XConversation
import app.pane.android.data.x.XDirectPageLoader
import app.pane.android.data.x.XSyndication
import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class XSyndicationTest {
    @Test
    fun tokenMatchesTheEmbedPage() {
        assertEquals("6dq1a2xwd93", XSyndication.token("20"))
        assertEquals("2jn1m8zmk1", XSyndication.token("1050118621198921728"))
        assertEquals(
            "4agma7xpol",
            XSyndication.token("1770000000000000000"),
        )
    }

    @Test
    fun readsAPublicSyndicationPost() {
        val body = """
            {
              "id_str": "20",
              "text": "just setting up my twttr",
              "user": {"name": "jack", "screen_name": "jack"},
              "photos": [{"url": "https://pbs.twimg.com/media/a.jpg"}],
              "conversation_count": 3
            }
        """.trimIndent()
        val post = XSyndication.parseJson(body, "20", "https://x.com/i/status/20")
        assertEquals("jack", post?.author)
        assertEquals("just setting up my twttr", post?.text)
        assertEquals(listOf("https://pbs.twimg.com/media/a.jpg"), post?.imageUrls)
        assertEquals(3, post?.commentCount)
    }

    @Test
    fun picksTheHighestBitrateMp4() {
        val body = """
            {
              "text": "clip",
              "user": {"name": "Ada"},
              "video": {
                "poster": "https://pbs.twimg.com/poster.jpg",
                "variants": [
                  {"type": "application/x-mpegURL", "src": "https://video.twimg.com/a.m3u8"},
                  {"content_type": "video/mp4", "url": "https://video.twimg.com/low.mp4", "bitrate": 256000},
                  {"type": "video/mp4", "src": "https://video.twimg.com/high.mp4", "bitrate": 832000}
                ]
              }
            }
        """.trimIndent()
        val post = XSyndication.parseJson(body, "9", "https://x.com/i/status/9")
        assertEquals("https://video.twimg.com/high.mp4", post?.videoUrl)
        assertTrue(post?.imageUrls?.contains("https://pbs.twimg.com/poster.jpg") == true)
    }

    @Test
    fun tombstoneIsNotAPost() {
        val body = """{"__typename":"TweetTombstone","text":"This post is unavailable."}"""
        assertNull(XSyndication.parseJson(body, "1", "https://x.com/i/status/1"))
    }

    @Test
    fun oEmbedStripsTheEmbedHtml() {
        val body = """
            {"author_name":"Ada","html":"<blockquote>Hello &amp; welcome</blockquote> — Ada (@ada)"}
        """.trimIndent()
        val post = XSyndication.parseOEmbed(body, "5", "https://x.com/i/status/5")
        assertEquals("Ada", post?.author)
        assertEquals("Hello & welcome", post?.text)
    }

    @Test
    fun loggedOutStatusHtmlIncludesRepliesBesidesThePost() {
        val html = """
            name:"jack" full_text:"just setting up my twttr" created_at_ms:1142974214000 entry_id:"tweet-20"
            name:"Ada" full_text:"@jack hello there" created_at_ms:1710000000000 entry_id:"conversationthread-8-tweet-8"
            entry_id:"cursor-bottom"
        """.trimIndent()

        val replies = XConversation.parseReplies(html, "20")

        assertEquals(1, replies.size)
        assertEquals("8", replies[0].id)
        assertEquals("Ada", replies[0].author)
        assertEquals("@jack hello there", replies[0].text)
        assertEquals(1710000000000L, replies[0].createdAtEpochMillis)
    }

    @Test
    fun repliesListedAfterTheirEntryIdKeepTheAuthorName() {
        val html = """
            entry_id:"tweet-20" full_text:"the post"
            entry_id:"conversationthread-8-tweet-8" __typename:"TimelineTimelineItem" screen_name:"ada" name:"Ada" full_text:"a public reply" created_at_ms:1710000000000
        """.trimIndent()

        val replies = XConversation.parseReplies(html, "20")

        assertEquals("Ada", replies.single().author)
        assertEquals("a public reply", replies.single().text)
    }

    @Test
    fun jsonAndEscapedReplyPayloadsStillParse() {
        val json = """
            {"entry_id":"conversationthread-8-tweet-8","__typename":"TimelineTimelineItem","screen_name":"ada","name":"Ada","full_text":"json reply","created_at_ms":1710000000000}
        """.trimIndent()
        val escaped = """
            entry_id:\"conversationthread-9-tweet-9\" name:\"Bea\" full_text:\"escaped reply\" created_at_ms:1710000000001
        """.trimIndent()

        assertEquals("json reply", XConversation.parseReplies(json, "20").single().text)
        assertEquals("Ada", XConversation.parseReplies(json, "20").single().author)
        assertEquals("escaped reply", XConversation.parseReplies(escaped, "20").single().text)
        assertEquals("Bea", XConversation.parseReplies(escaped, "20").single().author)
    }

    @Test
    fun loaderTriesTheNextStatusPageWhenTheFirstHasNoReplies() = runTest {
        val loader = XDirectPageLoader { url ->
            val body = when {
                url.contains("syndication") -> """
                    {"id_str":"20","text":"hello","user":{"name":"jack"},"conversation_count":1}
                """.trimIndent()
                url.contains("https://x.com/i/status") -> "<html>login wall</html>"
                url.contains("twitter.com/i/status") -> """
                    entry_id:"conversationthread-9-tweet-9" name:"Bea" full_text:"from twitter" created_at_ms:1710000000000
                """.trimIndent()
                else -> """{"author_name":"jack","html":"<p>fallback</p>"}"""
            }
            jsonConnection(body)
        }

        val post = loader.resolve("https://x.com/jack/status/20")

        assertEquals("from twitter", post.replies.single().text)
        assertEquals("Bea", post.replies.single().author)
    }

    @Test
    fun noteTextIsTheLongerBodyAfterThePreview() {
        val html = """
            full_text:"That's a +225 basis"
            __typename:"NoteTweet" text:"short entity" text:"That's a +225 basis point move and the rest of the note."
        """.trimIndent()

        assertEquals(
            "That's a +225 basis point move and the rest of the note.",
            XConversation.parseNoteText(html),
        )
        assertEquals(
            "That's a +225 basis point move and the rest of the note.",
            XConversation.longerCaption("That's a +225 basis", XConversation.parseNoteText(html)),
        )
    }

    @Test
    fun loaderCopiesTheNoteAndRepliesFromTheStatusPage() = runTest {
        val loader = XDirectPageLoader { url ->
            val body = when {
                url.contains("syndication") -> """
                    {"id_str":"20","text":"That's a +225 basis","user":{"name":"The Kobeissi Letter","screen_name":"KobeissiLetter"},"conversation_count":1}
                """.trimIndent()
                url.contains("/i/status/") -> "<html>login wall</html>"
                url.contains("/KobeissiLetter/status/") -> """
                    __typename:"NoteTweet" text:"That's a +225 basis point move and the rest of the note."
                    entry_id:"conversationthread-8-tweet-8" name:"Ada" full_text:"a public reply" created_at_ms:1710000000000
                """.trimIndent()
                else -> """{"author_name":"jack","html":"<p>fallback</p>"}"""
            }
            jsonConnection(body)
        }

        val post = loader.resolve("https://x.com/KobeissiLetter/status/20")

        assertEquals("That's a +225 basis point move and the rest of the note.", post.text)
        assertEquals("a public reply", post.replies.single().text)
    }

    @Test
    fun loaderAttachesRepliesFromTheStatusPage() = runTest {
        val loader = XDirectPageLoader { url ->
            val body = when {
                url.contains("syndication") -> """
                    {"id_str":"20","text":"just setting up my twttr","user":{"name":"jack"},"conversation_count":2}
                """.trimIndent()
                url.contains("x.com/i/status") -> """
                    full_text:"just setting up my twttr" entry_id:"tweet-20"
                    name:"Ada" full_text:"a public reply" created_at_ms:1710000000000 entry_id:"conversationthread-8-tweet-8"
                """.trimIndent()
                else -> """{"author_name":"jack","html":"<p>fallback</p>"}"""
            }
            jsonConnection(body)
        }

        val post = loader.resolve("https://x.com/jack/status/20")

        assertEquals("just setting up my twttr", post.text)
        assertEquals(2, post.commentCount)
        assertEquals("a public reply", post.replies.single().text)
        assertEquals("Ada", post.replies.single().author)
    }

    @Test
    fun loaderFallsThroughATombstoneToOEmbed() = runTest {
        val opened = mutableListOf<String>()
        val loader = XDirectPageLoader { url ->
            opened += url
            val body = if (url.contains("syndication")) {
                """{"__typename":"TweetTombstone","tombstone":{"text":"private"}}"""
            } else {
                """{"author_name":"Ada","html":"<p>Still public</p>"}"""
            }
            jsonConnection(body)
        }

        val post = loader.resolve("https://x.com/ada/status/20")

        assertEquals("Still public", post.text)
        assertTrue(opened[0].contains("cdn.syndication.twimg.com"))
        assertTrue(opened[0].contains("token=6dq1a2xwd93"))
        assertTrue(opened[1].startsWith("https://publish.twitter.com/oembed"))
    }

    private fun jsonConnection(body: String): HttpURLConnection =
        object : HttpURLConnection(URL("https://cdn.syndication.twimg.com/tweet-result")) {
            override fun setInstanceFollowRedirects(followRedirects: Boolean) {
                super.setInstanceFollowRedirects(followRedirects)
            }
            override fun setRequestMethod(method: String) {
                super.setRequestMethod(method)
            }
            override fun connect() = Unit
            override fun disconnect() = Unit
            override fun usingProxy(): Boolean = false
            override fun getResponseCode(): Int = HTTP_OK
            override fun getInputStream() = ByteArrayInputStream(body.toByteArray(StandardCharsets.UTF_8))
            override fun getErrorStream() = inputStream
        }
}
