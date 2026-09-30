package app.pane.android

import app.pane.android.data.facebook.FacebookDirectPageLoader
import app.pane.android.data.facebook.FacebookDocument
import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FacebookDocumentTest {
    @Test
    fun readsAPublicPostFromOpenGraphTags() {
        val post = FacebookDocument.parse(PUBLIC_HTML, "pfbid0123", "https://www.facebook.com/nasa/posts/pfbid0123")

        assertEquals("NASA", post?.author)
        assertEquals("The moon, closer than it looks.", post?.text)
        assertEquals(listOf("https://scontent.example/moon.jpg"), post?.imageUrls)
        assertEquals("https://video.example/moon.mp4", post?.videoUrl)
    }

    @Test
    fun aLoginWallWithoutAPostIsNotContent() {
        val html = """
            <html><meta property="og:title" content="Facebook">
            <meta property="og:description" content="Log into Facebook to start sharing.">
            <form id="login_form"></form></html>
        """.trimIndent()
        assertNull(FacebookDocument.parse(html, "1", "https://www.facebook.com/permalink.php?story_fbid=1"))
    }

    @Test
    fun directLoaderUsesTheEmbedPluginThenParsesThePost() = runTest {
        val opened = mutableListOf<String>()
        val loader = FacebookDirectPageLoader { url ->
            opened += url
            htmlConnection(PUBLIC_HTML)
        }

        val post = loader.resolve("https://www.facebook.com/nasa/posts/pfbid0123")

        assertEquals("NASA", post.author)
        assertTrue(opened.first().startsWith("https://www.facebook.com/plugins/post.php?href="))
        assertTrue(opened.first().contains("nasa"))
    }

    private fun htmlConnection(html: String): HttpURLConnection =
        object : HttpURLConnection(URL("https://www.facebook.com/plugins/post.php")) {
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
            override fun getInputStream() = ByteArrayInputStream(html.toByteArray(StandardCharsets.UTF_8))
            override fun getErrorStream() = inputStream
        }

    private companion object {
        val PUBLIC_HTML = """
            <html>
              <meta property="og:title" content="NASA - Facebook">
              <meta property="og:description" content="The moon, closer than it looks.">
              <meta property="og:image" content="https://scontent.example/moon.jpg">
              <meta property="og:video:secure_url" content="https://video.example/moon.mp4">
            </html>
        """.trimIndent()
    }
}
