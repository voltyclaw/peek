package app.pane.android

import app.pane.android.data.reddit.RedditDirectPageLoader
import app.pane.android.data.reddit.RedditFetchPlan
import app.pane.android.data.reddit.RedditPreviewElement
import app.pane.android.data.reddit.RedditUrls
import app.pane.android.data.resolver.PageLoadProgressElement
import app.pane.android.domain.model.LoadProgress
import app.pane.android.domain.repository.LoadProgressListener
import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RedditDirectPageLoaderTest {
    @Test
    fun requestsLoggedOutPostJsonAndParsesIt() = runTest {
        val page = "https://old.reddit.com/r/pics/comments/abc123/title/?utm_source=share"
        val planned = RedditFetchPlan.requests(page, "abc123")
        val opened = Collections.synchronizedList(mutableListOf<String>())
        val connections = ConcurrentHashMap<String, FakeHttpConnection>()
        val loader = RedditDirectPageLoader(connectionFactory = { url ->
            opened += url
            holdUntilJsonStarts(url, opened, planned)
            FakeHttpConnection(url, IMAGE_RESPONSE).also { connections[url] = it }
        })
        val progress = mutableListOf<LoadProgress>()

        val post = withContext(PageLoadProgressElement(LoadProgressListener { progress += it })) {
            loader.resolve(page)
        }

        val full = planned.first()
        assertTrue(opened.contains(full.url))
        assertTrue(opened.any { it.contains("limit=1") })
        val connection = connections.getValue(full.url)
        assertEquals("abc123", post.id)
        assertEquals("alice", post.author)
        assertEquals("GET", connection.requestedMethod)
        assertTrue(connection.redirectsEnabled)
        assertEquals("application/json", connection.getRequestProperty("Accept"))
        val userAgent = connection.getRequestProperty("User-Agent").orEmpty()
        assertEquals(RedditFetchPlan.CLIENT_USER_AGENT, userAgent)
        assertFalse(userAgent.contains("Android"))
        assertFalse(userAgent.contains("Chrome/"))
        assertEquals(1f, progress.last().fraction)
        assertFalse(connection.getRequestProperty("Authorization").orEmpty().isNotBlank())
    }

    @Test
    fun followsHttpRedirectShareLinksBeforeRequestingJson() = runTest {
        val share = "https://www.reddit.com/r/pics/s/Ab12Cd"
        val planned = RedditFetchPlan.requests("https://www.reddit.com/r/pics/comments/abc123/title", "abc123")
        val opened = Collections.synchronizedList(mutableListOf<String>())
        val loader = RedditDirectPageLoader(connectionFactory = { url ->
            opened += url
            holdUntilJsonStarts(url, opened, planned)
            if (url.contains("/s/")) {
                FakeHttpConnection(
                    url = url,
                    response = "",
                    status = 302,
                    responseHeaders = mapOf(
                        "Location" to "/r/pics/comments/abc123/title/",
                    ),
                )
            } else {
                FakeHttpConnection(url, IMAGE_RESPONSE)
            }
        })

        val post = loader.resolve(share)

        assertEquals("abc123", post.id)
        assertEquals(share, opened.first())
        assertTrue(
            opened.contains(
                RedditFetchPlan.requests("https://www.reddit.com/r/pics/comments/abc123/title", "abc123").first().url,
            ),
        )
    }

    @Test
    fun shareRedirectToACommentsUrlSkipsTheJavascriptChallenge() = runTest {
        val share = "https://www.reddit.com/r/interestingasfuck/s/VDgXcEIG1q"
        val challenge = "https://www.reddit.com/r/interestingasfuck/comments/1wubxdy/title/?js_challenge=1&jsc_token=abc"
        val planned = RedditFetchPlan.requests(
            "https://www.reddit.com/r/interestingasfuck/comments/1wubxdy/title",
            "1wubxdy",
        )
        val opened = Collections.synchronizedList(mutableListOf<String>())
        val loader = RedditDirectPageLoader(connectionFactory = { url ->
            opened += url
            holdUntilJsonStarts(url, opened, planned)
            if (url.contains("/s/")) {
                FakeHttpConnection(
                    url = url,
                    response = "",
                    status = 302,
                    responseHeaders = mapOf("Location" to challenge),
                )
            } else {
                FakeHttpConnection(url, POST_JSON)
            }
        })

        val post = loader.resolve(share)

        assertEquals("1wubxdy", post.id)
        assertEquals(share, opened.first())
        assertTrue(
            opened.contains(
                RedditFetchPlan.requests(
                    "https://www.reddit.com/r/interestingasfuck/comments/1wubxdy/title",
                    "1wubxdy",
                ).first().url,
            ),
        )
        assertFalse(opened.any { it.contains("js_challenge") })
    }

    @Test
    fun retriesJsonOnOldRedditWhenWwwReturnsABlockPage() = runTest {
        val pasted = "https://www.reddit.com/r/interestingasfuck/comments/1w3fcl7/in_1960_david_latimer_planted_a_garden_inside_of/"
        val planned = RedditFetchPlan.requests(pasted, "1w3fcl7")
        val opened = Collections.synchronizedList(mutableListOf<String>())
        val loader = RedditDirectPageLoader(connectionFactory = { url ->
            opened += url
            holdUntilJsonStarts(url, opened, planned)
            if (url == planned.first().url) {
                FakeHttpConnection(url, "<html><p>whoa there, pardner</p></html>", status = 403)
            } else {
                FakeHttpConnection(url, LATIMER_JSON)
            }
        })

        val post = loader.resolve(pasted)

        assertEquals("1w3fcl7", post.id)
        assertEquals("In 1960, David Latimer planted a garden", post.title)
        assertTrue(opened.contains(planned[0].url))
        assertTrue(opened.contains(planned[1].url))
        assertTrue(planned[0].url.startsWith("https://old.reddit.com/r/interestingasfuck/comments/1w3fcl7/"))
        assertFalse(planned[0].userAgent.contains("Android"))
    }

    @Test
    fun resolvesShareShortlinkFromCanonicalHtml() = runTest {
        val share = "https://www.reddit.com/r/interestingasfuck/s/VDgXcEIG1q"
        val page = "https://www.reddit.com/r/interestingasfuck/comments/1wubxdy/the_new_us_passport_reveal_akin_to_apple_product"
        val planned = RedditFetchPlan.requests(page, "1wubxdy")
        val opened = Collections.synchronizedList(mutableListOf<String>())
        val loader = RedditDirectPageLoader(connectionFactory = { url ->
            opened += url
            holdUntilJsonStarts(url, opened, planned)
            if (url.contains("/s/")) {
                FakeHttpConnection(url = url, response = SHARE_HTML, status = HTTP_OK)
            } else {
                FakeHttpConnection(url, POST_JSON)
            }
        })

        val post = loader.resolve(share)

        assertEquals("1wubxdy", post.id)
        assertEquals("A public photo", post.title)
        assertEquals(share, opened.first())
        assertTrue(opened.contains(RedditFetchPlan.requests(page, "1wubxdy").first().url))
    }

    @Test
    fun parsesShredditHtmlWhenEveryJsonHostIsBlocked() = runTest {
        val pasted = "https://www.reddit.com/r/interestingasfuck/comments/1w3fcl7/in_1960_david_latimer_planted_a_garden_inside_of/"
        val loader = RedditDirectPageLoader(connectionFactory = { url ->
            if (url.endsWith(".json") || url.contains(".json?")) {
                FakeHttpConnection(url, "<html><p>whoa there, pardner</p></html>", status = 403)
            } else {
                FakeHttpConnection(url, SHREDDIT_HTML)
            }
        })

        val post = loader.resolve(pasted)

        assertEquals("1w3fcl7", post.id)
        assertEquals("In 1960, David Latimer planted a garden", post.title)
        assertEquals("https://i.redd.it/h190t4tyzpmh1.jpeg", post.media.single().imageUrl)
        assertTrue(post.comments.isEmpty())
    }

    @Test
    fun sharePageWithoutAPublicPostFailsClosed() = runTest {
        val loader = RedditDirectPageLoader(connectionFactory = { url ->
            FakeHttpConnection(
                url = url,
                response = "<html><title>Blocked</title><p>whoa there, pardner</p></html>",
                status = 403,
            )
        })

        val result = runCatching {
            loader.resolve("https://www.reddit.com/r/interestingasfuck/s/VDgXcEIG1q")
        }

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("HTTP 403"))
    }

    @Test
    fun rejectsUnsupportedUrlsBeforeOpeningAConnection() = runTest {
        var opened = false
        val loader = RedditDirectPageLoader(connectionFactory = {
            opened = true
            FakeHttpConnection(it, IMAGE_RESPONSE)
        })

        val result = runCatching { loader.resolve("https://www.reddit.com/r/pics") }

        assertTrue(result.isFailure)
        assertFalse(opened)
    }

    @Test
    fun previewPaintsBeforeTheFullCommentPage() = runBlocking {
        val page = "https://www.reddit.com/r/pics/comments/abc123/title/"
        val previews = Collections.synchronizedList(mutableListOf<String>())
        val fullStarted = CountDownLatch(1)
        val releaseFull = CountDownLatch(1)
        val loader = RedditDirectPageLoader(connectionFactory = { url ->
            val body = if (Regex("limit=1(&|$)").containsMatchIn(url)) {
                IMAGE_RESPONSE
            } else if (url.contains(".json")) {
                fullStarted.countDown()
                check(releaseFull.await(5, TimeUnit.SECONDS))
                IMAGE_RESPONSE
            } else {
                IMAGE_RESPONSE
            }
            FakeHttpConnection(url, body)
        })

        val deferred = async(Dispatchers.IO + RedditPreviewElement { previews += it.title }) {
            loader.resolve(page)
        }
        assertTrue(fullStarted.await(5, TimeUnit.SECONDS))
        val deadline = System.currentTimeMillis() + 2_000
        while (previews.isEmpty() && System.currentTimeMillis() < deadline) {
            Thread.sleep(20)
        }
        assertEquals(listOf("A public photo"), previews.toList())
        releaseFull.countDown()
        val post = deferred.await()
        assertEquals("abc123", post.id)
    }

    @Test
    fun failsWhenRedditHidesThePost() = runTest {
        val loader = RedditDirectPageLoader(connectionFactory = { url ->
            FakeHttpConnection(url, """{"message":"Forbidden","error":403}""", status = 403)
        })

        val result = runCatching {
            loader.resolve("https://www.reddit.com/r/pics/comments/abc123/title/")
        }

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("HTTP 403"))
    }

    private fun holdUntilJsonStarts(
        url: String,
        opened: MutableList<String>,
        planned: List<RedditFetchPlan.Request>,
    ) {
        if (!url.contains(".json")) return
        val expected = planned.count { it.url.contains(".json") } + 1
        val ready = opened.count { it.contains(".json") } >= expected
        if (!ready) {
            val deadline = System.currentTimeMillis() + 2_000
            while (opened.count { it.contains(".json") } < expected && System.currentTimeMillis() < deadline) {
                Thread.sleep(5)
            }
        }
    }

    private class FakeHttpConnection(
        url: String,
        private val response: String,
        private val status: Int = HTTP_OK,
        private val finalUrl: String = url,
        private val responseHeaders: Map<String, String> = emptyMap(),
    ) : HttpURLConnection(URL(url)) {
        var redirectsEnabled: Boolean = true
        var requestedMethod: String = "GET"

        override fun setInstanceFollowRedirects(followRedirects: Boolean) {
            super.setInstanceFollowRedirects(followRedirects)
            redirectsEnabled = followRedirects
        }

        override fun setRequestMethod(method: String) {
            super.setRequestMethod(method)
            requestedMethod = method
        }

        override fun getURL(): URL = URL(finalUrl)

        override fun getHeaderField(name: String?): String? =
            responseHeaders.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value
        override fun getResponseCode(): Int = status
        override fun getInputStream() = ByteArrayInputStream(response.toByteArray(StandardCharsets.UTF_8))
        override fun getErrorStream() = inputStream
        override fun connect() = Unit
        override fun disconnect() = Unit
        override fun usingProxy(): Boolean = false
    }
}

private const val HTTP_OK = 200

private const val SHARE_HTML = """
<!DOCTYPE html>
<html><head>
  <link rel="canonical" href="https://www.reddit.com/r/interestingasfuck/comments/1wubxdy/the_new_us_passport_reveal_akin_to_apple_product/">
  <meta property="og:url" content="https://www.reddit.com/r/interestingasfuck/comments/1wubxdy/the_new_us_passport_reveal_akin_to_apple_product/">
  <a href="https://www.reddit.com/r/PassportPorn/comments/1vslmrg/other_post/">related</a>
</head></html>
"""

private const val POST_JSON = """
[{"kind":"Listing","data":{"children":[{"kind":"t3","data":{
  "id":"1wubxdy","title":"A public photo","author":"alice","subreddit":"interestingasfuck","score":10,"num_comments":0,
  "created_utc":1700000000,"permalink":"/r/interestingasfuck/comments/1wubxdy/the_new_us_passport_reveal_akin_to_apple_product/",
  "is_self":false,"url":"https://i.redd.it/photo.jpg"
}}]}}]
"""

private const val SHREDDIT_HTML = """
<html><shreddit-post id="t3_1w3fcl7" post-title="In 1960, David Latimer planted a garden" author="The_Love-Tap" subreddit-name="interestingasfuck" score="45372" comment-count="676" created-timestamp="2026-08-31T14:25:05.266000+0000" permalink="/r/interestingasfuck/comments/1w3fcl7/in_1960_david_latimer_planted_a_garden_inside_of/" content-href="https://i.redd.it/h190t4tyzpmh1.jpeg" post-type="image"></shreddit-post></html>
"""

private const val LATIMER_JSON = """
[{"kind":"Listing","data":{"children":[{"kind":"t3","data":{
  "id":"1w3fcl7","title":"In 1960, David Latimer planted a garden","author":"The_Love-Tap",
  "subreddit":"interestingasfuck","score":45377,"num_comments":1,"created_utc":1788186305.0,
  "permalink":"/r/interestingasfuck/comments/1w3fcl7/in_1960_david_latimer_planted_a_garden_inside_of/",
  "is_self":false,"url":"https://i.redd.it/h190t4tyzpmh1.jpeg"
}}]}}]
"""

private const val IMAGE_RESPONSE = """
[{"kind":"Listing","data":{"children":[{"kind":"t3","data":{
  "id":"abc123","title":"A public photo","author":"alice","subreddit":"pics","score":10,"num_comments":0,
  "created_utc":1700000000,"permalink":"/r/pics/comments/abc123/a_public_photo/","is_self":false,
  "url":"https://i.redd.it/photo.jpg"
}}]}}]
"""
