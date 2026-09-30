package com.mustafashakir.peek

import com.mustafashakir.peek.data.x.XDirectPageLoader
import com.mustafashakir.peek.data.x.XSyndication
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
