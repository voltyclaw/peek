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
    fun aTruncatedPreviewKeepsTheRestOfTheCaption() {
        val html = """
            <html>
              <meta property="og:title" content="Ben Ami Gallery - Facebook">
              <meta property="og:description" content="קול קורא לאמנים לתערוכת הגורם האנושי...">
              <meta property="og:image" content="https://scontent.example/show.jpg">
              <div data-testid="post_message">
                <div dir="auto">קול קורא לאמנים לתערוכת הגורם האנושי</div>
                <div dir="auto">גלריה בן עמי מזמינה אמנים ואמניות לקחת חלק בתערוכה חדשה עד הסוף.</div>
              </div>
            </html>
        """.trimIndent()

        val post = FacebookDocument.parse(html, "pfbid0123", "https://www.facebook.com/benami/posts/pfbid0123")

        assertTrue(post?.text?.contains("עד הסוף") == true)
        assertTrue(post?.text?.startsWith("קול קורא לאמנים") == true)
        assertTrue(FacebookDocument.isTruncatedPreview("קול קורא לאמנים..."))
    }

    @Test
    fun aMessageObjectCanExtendATruncatedPreview() {
        val html = """
            <html>
              <meta property="og:description" content="The moon, closer...">
              <script>{"message":{"delight_ranges":[],"text":"The moon, closer than it looks from orbit tonight."}}</script>
            </html>
        """.trimIndent()

        val post = FacebookDocument.parse(html, "1", "https://www.facebook.com/nasa/posts/1")

        assertEquals("The moon, closer than it looks from orbit tonight.", post?.text)
    }

    @Test
    fun anUnrelatedLongerStringDoesNotReplaceTheCaption() {
        val html = """
            <html>
              <meta property="og:description" content="The moon, closer than it looks.">
              <script>{"message":{"text":"This is a completely different and much longer piece of interface copy."}}</script>
            </html>
        """.trimIndent()

        val post = FacebookDocument.parse(html, "1", "https://www.facebook.com/nasa/posts/1")

        assertEquals("The moon, closer than it looks.", post?.text)
    }

    @Test
    fun directLoaderKeepsLookingWhenTheFirstCaptionIsCutOff() = runTest {
        val opened = java.util.Collections.synchronizedList(mutableListOf<String>())
        val loader = FacebookDirectPageLoader { url ->
            opened += url
            val html = if (url.contains("mbasic.facebook.com")) {
                """
                    <html>
                      <meta property="og:title" content="NASA - Facebook">
                      <div data-testid="post_message"><div dir="auto">The moon, closer than it looks from orbit tonight.</div></div>
                    </html>
                """.trimIndent()
            } else {
                """
                    <html>
                      <meta property="og:title" content="NASA - Facebook">
                      <meta property="og:description" content="The moon, closer...">
                      <meta property="og:image" content="https://scontent.example/moon.jpg">
                    </html>
                """.trimIndent()
            }
            htmlConnection(html)
        }

        val post = loader.resolve("https://www.facebook.com/nasa/posts/pfbid0123")

        assertEquals("The moon, closer than it looks from orbit tonight.", post.text)
        assertEquals(listOf("https://scontent.example/moon.jpg"), post.imageUrls)
        assertTrue(opened.any { it.contains("mbasic.facebook.com") })
        assertTrue(opened.any { it.contains("facebook.com") && !it.contains("mbasic.facebook.com") })
    }

    @Test
    fun aPostBodyInTheTitleIsNotTheAuthor() {
        val html = """
            <html>
              <meta property="og:title" content="טיולון- מבלים עם הילדים ומדווחים מהשטח | המלצה חמה: יום כיף בנחל בצת, נחל שרך">
              <meta property="og:description" content="טיולון- מבלים עם הילדים ומדווחים מהשטח | המלצה חמה: יום כיף בנחל בצת, נחל שרך וסיום מפנק בנהריה">
              <meta property="og:image" content="https://scontent.example/trail.jpg">
              "actors":[{"__typename":"Page","name":"טיולון","id":"9","profile_picture":{"uri":"https://scontent.example/avatar.jpg"}}]
            </html>
        """.trimIndent()

        val post = FacebookDocument.parse(html, "1", "https://www.facebook.com/tiulon/posts/1")

        assertEquals("טיולון", post?.author)
        assertEquals("https://scontent.example/avatar.jpg", post?.authorAvatarUrl)
        assertTrue(post?.text?.contains("נהריה") == true)
        assertTrue(FacebookDocument.hasDistinctAuthor(post!!))
    }

    @Test
    fun directLoaderKeepsLookingForThePageNameWhenTheTitleIsTheCaption() = runTest {
        val loader = FacebookDirectPageLoader { url ->
            val html = if (url.contains("mbasic.facebook.com")) {
                """
                    <html>
                      <h3><a href="/tiulon">טיולון</a></h3>
                      <img src="https://scontent.example/avatar.jpg" alt="טיולון">
                      <meta property="og:description" content="יום כיף בנחל">
                    </html>
                """.trimIndent()
            } else {
                """
                    <html>
                      <meta property="og:title" content="יום כיף בנחל בצת עם הילדים ומדווחים מהשטח כל הבוקר">
                      <meta property="og:description" content="יום כיף בנחל בצת עם הילדים ומדווחים מהשטח כל הבוקר ואחר הצהריים.">
                      <meta property="og:image" content="https://scontent.example/trail.jpg">
                    </html>
                """.trimIndent()
            }
            htmlConnection(html)
        }

        val post = loader.resolve("https://www.facebook.com/tiulon/posts/pfbid0123")

        assertEquals("טיולון", post.author)
        assertEquals("https://scontent.example/avatar.jpg", post.authorAvatarUrl)
        assertTrue(post.text.contains("אחר הצהריים"))
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
        val opened = java.util.Collections.synchronizedList(mutableListOf<String>())
        val started = java.util.concurrent.CountDownLatch(4)
        val loader = FacebookDirectPageLoader { url ->
            opened += url
            started.countDown()
            check(started.await(3, java.util.concurrent.TimeUnit.SECONDS))
            htmlConnection(PUBLIC_HTML)
        }

        val post = loader.resolve("https://www.facebook.com/nasa/posts/pfbid0123")

        assertEquals("NASA", post.author)
        assertEquals("The moon, closer than it looks.", post.text)
        assertTrue(opened.any { it.contains("plugins/post.php") && it.contains("nasa") })
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
