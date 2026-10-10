package app.pane.android

import app.pane.android.data.facebook.FacebookDirectPageLoader
import app.pane.android.data.facebook.FacebookDocument
import app.pane.android.data.facebook.FacebookShareRedirect
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
    fun jsonUnicodeAndAMissingAvatarFallBackToAProfilePhoto() {
        val escaped = FacebookDocument.parse(
            """
            <html>
              <meta property="og:description" content="A public caption long enough to keep.">
              <meta property="og:image" content="https://scontent.example/post-photo.jpg">
              <script>{"actors":[{"name":"\\u05e7\\u05d5\\u05e0\\u05d4","id":"9"}]}</script>
              <img alt="קונה" src="https://scontent.example/face.jpg">
            </html>
            """.trimIndent(),
            "1",
            "https://www.facebook.com/page/posts/1",
        )
        assertEquals("קונה", escaped?.author)
        assertEquals("https://scontent.example/face.jpg", escaped?.authorAvatarUrl)

        val postPhoto = FacebookDocument.parse(
            """
            <html>
              <meta property="og:description" content="A public caption long enough to keep.">
              <meta property="og:image" content="https://scontent.example/post-photo.jpg">
              <script>{"actors":[{"name":"NASA","id":"1"}]}</script>
            </html>
            """.trimIndent(),
            "1",
            "https://www.facebook.com/nasa/posts/1",
        )
        assertNull(postPhoto?.authorAvatarUrl)

        val profileOg = FacebookDocument.parse(
            """
            <html>
              <meta property="og:description" content="A public caption long enough to keep.">
              <meta property="og:image" content="https://scontent.example/profile_pic.jpg">
              <script>{"actors":[{"name":"NASA","id":"1"}]}</script>
            </html>
            """.trimIndent(),
            "1",
            "https://www.facebook.com/nasa/posts/1",
        )
        assertEquals("https://scontent.example/profile_pic.jpg", profileOg?.authorAvatarUrl)
    }

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
    fun readsPlayableVideoFilesFromAReelDocument() {
        val html = """
            <html>
              <meta property="og:title" content="NASA - Facebook">
              <meta property="og:description" content="A public reel.">
              <meta property="og:image" content="https://scontent.example/reel.jpg">
              <meta property="og:type" content="video.other">
              "browser_native_hd_url":"https:\/\/video.xx.fbcdn.net\/v\/hd.mp4?x=1","height":1920,"width":1080
              "browser_native_sd_url":"https:\/\/video.xx.fbcdn.net\/v\/sd.mp4","height":960
            </html>
        """.trimIndent()

        val post = FacebookDocument.parse(html, "1234567890", "https://www.facebook.com/reel/1234567890")

        assertEquals("https://video.xx.fbcdn.net/v/hd.mp4?x=1", post?.videoUrl)
        assertEquals(listOf(1920, 960), post?.videos?.map { it.height })
        assertTrue(post?.videoHint == true)
    }

    @Test
    fun reelLoaderWaitsForThePlayableFile() = runTest {
        val loader = FacebookDirectPageLoader { url ->
            val html = if (url.contains("mbasic.facebook.com")) {
                """
                    <html>
                      <meta property="og:title" content="NASA - Facebook">
                      <meta property="og:description" content="A public reel.">
                      <meta property="og:image" content="https://scontent.example/reel.jpg">
                      "browser_native_sd_url":"https:\/\/video.xx.fbcdn.net\/v\/reel.mp4"
                    </html>
                """.trimIndent()
            } else {
                """
                    <html>
                      <meta property="og:title" content="NASA - Facebook">
                      <meta property="og:description" content="A public reel.">
                      <meta property="og:image" content="https://scontent.example/reel.jpg">
                      <meta property="og:type" content="video.other">
                    </html>
                """.trimIndent()
            }
            htmlConnection(html)
        }

        val post = loader.resolve("https://www.facebook.com/reel/1234567890")

        assertEquals("https://video.xx.fbcdn.net/v/reel.mp4", post.videoUrl)
        assertEquals("https://scontent.example/reel.jpg", post.imageUrls.first())
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

    @Test
    fun storyFetchKeepsTheShareUrlAndSkipsThePostPlugin() = runTest {
        val opened = java.util.Collections.synchronizedList(mutableListOf<String>())
        val loader = FacebookDirectPageLoader { url ->
            opened += url
            htmlConnection(PUBLIC_HTML)
        }
        val url = "https://www.facebook.com/stories/100064321098765/UzpfSVsampleStory1/" +
            "?view_single=1&bucket_id=100064321098765&story_fbid=sampleStory1&mibextid=wwXIfr"

        val post = loader.resolve(url)

        assertEquals("NASA", post.author)
        assertEquals("https://scontent.example/moon.jpg", post.imageUrls.first())
        assertTrue(opened.any { it.contains("story_fbid=sampleStory1") && it.contains("bucket_id=") && it.contains("mibextid=") })
        assertTrue(opened.none { it.contains("plugins/post.php") || it.contains("permalink.php") })
    }

    @Test
    fun aShareShortlinkRedirectResolvesToThePublicPost() = runTest {
        val opened = java.util.Collections.synchronizedList(mutableListOf<String>())
        val loader = FacebookDirectPageLoader { url ->
            opened += url
            if (url.contains("/share/19j1v6TgD9")) {
                redirectConnection("https://www.facebook.com/nasa/posts/pfbid0123")
            } else {
                htmlConnection(PUBLIC_HTML)
            }
        }

        val post = loader.resolve("https://www.facebook.com/share/19j1v6TgD9/")

        assertEquals("NASA", post.author)
        assertEquals("The moon, closer than it looks.", post.text)
        assertTrue(opened.any { it.contains("/share/19j1v6TgD9") })
        assertTrue(opened.any { it.contains("nasa") && it.contains("pfbid0123") })
    }

    @Test
    fun aShareShortlinkThatDoesNotLandOnAPostStaysUnloadable() = runTest {
        val loader = FacebookDirectPageLoader {
            htmlConnection(
                """
                <html><meta property="og:title" content="Facebook">
                <meta property="og:description" content="Log into Facebook">
                <form id="login_form"></form></html>
                """.trimIndent(),
            )
        }

        val error = runCatching { loader.resolve("https://www.facebook.com/share/19j1v6TgD9/") }.exceptionOrNull()

        assertTrue(error is app.pane.android.domain.model.SourceFailure.Unsupported)
    }

    @Test
    fun shareRedirectReadsALocationHeaderAndAnOgUrl() {
        assertEquals(
            "https://www.facebook.com/nasa/posts/pfbid0123",
            FacebookShareRedirect.nextUrl(
                status = 302,
                location = "https://www.facebook.com/nasa/posts/pfbid0123",
                html = "",
                current = "https://www.facebook.com/share/19j1v6TgD9/",
            ),
        )
        val html = """<meta property="og:url" content="https://www.facebook.com/nasa/posts/pfbid0123&amp;ref=share">"""
        assertEquals(
            "https://www.facebook.com/nasa/posts/pfbid0123&ref=share",
            FacebookShareRedirect.nextUrl(200, null, html, "https://www.facebook.com/share/19j1v6TgD9/"),
        )
    }

    private fun redirectConnection(location: String): HttpURLConnection =
        object : HttpURLConnection(URL("https://www.facebook.com/share/19j1v6TgD9/")) {
            override fun setInstanceFollowRedirects(followRedirects: Boolean) {
                super.setInstanceFollowRedirects(followRedirects)
            }
            override fun setRequestMethod(method: String) {
                super.setRequestMethod(method)
            }
            override fun connect() = Unit
            override fun disconnect() = Unit
            override fun usingProxy(): Boolean = false
            override fun getResponseCode(): Int = HTTP_MOVED_TEMP
            override fun getHeaderField(name: String?): String? =
                if (name.equals("Location", ignoreCase = true)) location else super.getHeaderField(name)
            override fun getInputStream() = ByteArrayInputStream(ByteArray(0))
            override fun getErrorStream() = inputStream
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
