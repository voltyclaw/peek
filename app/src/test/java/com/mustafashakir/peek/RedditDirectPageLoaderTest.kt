package com.mustafashakir.peek

import com.mustafashakir.peek.data.reddit.RedditDirectPageLoader
import com.mustafashakir.peek.data.reddit.RedditUrls
import com.mustafashakir.peek.data.resolver.PageLoadProgressElement
import com.mustafashakir.peek.domain.model.LoadProgress
import com.mustafashakir.peek.domain.repository.LoadProgressListener
import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RedditDirectPageLoaderTest {
    @Test
    fun requestsLoggedOutPostJsonAndParsesIt() = runTest {
        val connection = FakeHttpConnection(RedditUrls.jsonUrl("abc123"), IMAGE_RESPONSE)
        var requestedUrl: String? = null
        val loader = RedditDirectPageLoader(connectionFactory = { url ->
            requestedUrl = url
            connection
        })
        val progress = mutableListOf<LoadProgress>()

        val post = withContext(PageLoadProgressElement(LoadProgressListener { progress += it })) {
            loader.resolve("https://old.reddit.com/r/pics/comments/abc123/title/?utm_source=share")
        }

        assertEquals(RedditUrls.jsonUrl("abc123"), requestedUrl)
        assertEquals("abc123", post.id)
        assertEquals("alice", post.author)
        assertEquals("GET", connection.requestedMethod)
        assertTrue(connection.redirectsEnabled)
        assertEquals("application/json", connection.getRequestProperty("Accept"))
        assertTrue(connection.getRequestProperty("User-Agent").orEmpty().contains("Chrome/"))
        assertEquals(1f, progress.last().fraction)
        assertFalse(connection.getRequestProperty("Authorization").orEmpty().isNotBlank())
    }

    @Test
    fun followsShareLinksBeforeRequestingJson() = runTest {
        val opened = mutableListOf<String>()
        val loader = RedditDirectPageLoader(connectionFactory = { url ->
            opened += url
            if (url.contains("/s/")) {
                FakeHttpConnection(
                    url = url,
                    response = "<html>redirect</html>",
                    finalUrl = "https://www.reddit.com/r/pics/comments/abc123/title/",
                )
            } else {
                FakeHttpConnection(url, IMAGE_RESPONSE)
            }
        })

        val post = loader.resolve("https://www.reddit.com/r/pics/s/Ab12Cd")

        assertEquals("abc123", post.id)
        assertEquals("https://www.reddit.com/r/pics/s/Ab12Cd", opened.first())
        assertEquals(RedditUrls.jsonUrl("abc123"), opened.last())
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
    fun failsWhenRedditHidesThePost() = runTest {
        val loader = RedditDirectPageLoader(connectionFactory = {
            FakeHttpConnection(it, """{"message":"Forbidden","error":403}""", status = 403)
        })

        val result = runCatching {
            loader.resolve("https://www.reddit.com/r/pics/comments/abc123/title/")
        }

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("HTTP 403"))
    }

    private class FakeHttpConnection(
        url: String,
        private val response: String,
        private val status: Int = HTTP_OK,
        private val finalUrl: String = url,
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
        override fun getResponseCode(): Int = status
        override fun getInputStream() = ByteArrayInputStream(response.toByteArray(StandardCharsets.UTF_8))
        override fun getErrorStream() = inputStream
        override fun connect() = Unit
        override fun disconnect() = Unit
        override fun usingProxy(): Boolean = false
    }
}

private const val IMAGE_RESPONSE = """
[{"kind":"Listing","data":{"children":[{"kind":"t3","data":{
  "id":"abc123","title":"A public photo","author":"alice","subreddit":"pics","score":10,"num_comments":0,
  "created_utc":1700000000,"permalink":"/r/pics/comments/abc123/a_public_photo/","is_self":false,
  "url":"https://i.redd.it/photo.jpg"
}}]}}]
"""
