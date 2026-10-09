package app.pane.android

import androidx.datastore.core.DataStore
import app.pane.android.data.cache.LinkContentCacheDocument
import app.pane.android.data.cache.LinkContentCacheStore
import app.pane.android.data.reddit.ParsedRedditComment
import app.pane.android.data.reddit.ParsedRedditMedia
import app.pane.android.data.reddit.ParsedRedditPost
import app.pane.android.data.reddit.RedditFetchPlan
import app.pane.android.data.reddit.RedditLinkContentRepository
import app.pane.android.data.reddit.RedditMoreComments
import app.pane.android.data.reddit.RedditPageLoader
import app.pane.android.domain.model.LinkKind
import app.pane.android.domain.model.LinkSource
import app.pane.android.domain.model.MediaLocation
import app.pane.android.domain.model.RedditMetadata
import java.io.ByteArrayInputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RedditRepositoryTest {
    @Test
    fun canonicalizesHostsAndMapsAPublicPost() = runTest {
        val loader = FakeLoader(imagePost())
        val repository = RedditLinkContentRepository(listOf(loader), newCacheStore())
        val requested = "https://old.reddit.com/r/pics/comments/abc123/a_title/?utm_source=share"

        val content = repository.resolve(requested).getOrThrow()

        assertEquals(requested, loader.urls.single())
        assertEquals(requested, content.url)
        assertEquals(LinkSource.Reddit, content.source)
        assertEquals(LinkKind.Post, content.kind)
        assertEquals("alice", content.author.name)
        assertEquals("REDDIT · r/pics · 1.2K POINTS", content.author.metadata)
        assertEquals("PHOTO", content.media.badge)
        assertEquals("https://i.redd.it/photo.jpg", (content.thumbnail as MediaLocation.Remote).url)
        assertEquals("Nice shot", content.comments.single().body)
        assertEquals("Thanks", content.comments.single().replies.single().body)
        assertTrue(content.comments.single().replies.single().isCreator)
        val metadata = content.sourceMetadata as RedditMetadata
        assertEquals("abc123", metadata.postId)
        assertEquals("pics", metadata.subreddit)
    }

    @Test
    fun samePostIdFromAnotherHostReusesTheCache() = runTest {
        val loader = FakeLoader(imagePost())
        val repository = RedditLinkContentRepository(listOf(loader), newCacheStore())

        repository.resolve("https://www.reddit.com/r/pics/comments/abc123/title/").getOrThrow()
        val again = repository.resolve("https://redd.it/abc123").getOrThrow()

        assertEquals("https://redd.it/abc123", again.url)
        assertEquals(1, loader.urls.size)
    }

    @Test
    fun mapsTextAndVideoPosts() = runTest {
        val repository = RedditLinkContentRepository(
            listOf(FakeLoader(textPost(), videoPost())),
            newCacheStore(),
        )

        val text = repository.resolve("https://www.reddit.com/r/ask/comments/txt111/a_question/").getOrThrow()
        val video = repository.resolve("https://www.reddit.com/r/videos/comments/vid111/a_clip/").getOrThrow()

        assertEquals("A question\n\nHello there", text.title)
        assertEquals("TEXT", text.media.badge)
        assertEquals("", (text.thumbnail as MediaLocation.Remote).url)
        assertEquals(LinkKind.Video, video.kind)
        assertEquals("VIDEO", video.media.badge)
        assertEquals("00:12", video.media.duration)
        assertEquals(
            "https://v.redd.it/clip/DASH_720.mp4",
            (video.sourceMetadata as RedditMetadata).mediaItems.single().videoUrl,
        )
    }

    @Test
    fun passesShareLinksThroughAndRejectsFeedsWithoutARequest() = runTest {
        val loader = FakeLoader(imagePost())
        val repository = RedditLinkContentRepository(listOf(loader), newCacheStore())

        repository.resolve("https://www.reddit.com/r/pics/s/Ab12Cd").getOrThrow()
        val rejected = repository.resolve("https://www.reddit.com/r/pics")

        assertEquals("https://www.reddit.com/r/pics/s/Ab12Cd", loader.urls.single())
        assertTrue(rejected.isFailure)
        assertFalse(repository.supports("https://i.redd.it/photo.jpg"))
    }

    @Test
    fun usesTheNextLoaderWhenTheJsonLoaderIsBlocked() = runTest {
        val blocked = FailingLoader(IOException("Reddit blocked the request (HTTP 403)"))
        val web = FakeLoader(imagePost())
        val repository = RedditLinkContentRepository(listOf(blocked, web), newCacheStore())

        val content = repository.resolve("https://www.reddit.com/r/pics/comments/abc123/title/").getOrThrow()

        assertEquals(1, blocked.urls.size)
        assertEquals(1, web.urls.size)
        assertEquals("abc123", (content.sourceMetadata as RedditMetadata).postId)
    }

    @Test
    fun surfacesTheWebViewFailureWhenThatFallbackAlsoFails() = runTest {
        val blocked = FailingLoader(IOException("Reddit blocked the request (HTTP 403)"))
        val web = FailingLoader(IOException("Reddit blocked the page in the browser (HTTP 403)"))
        val repository = RedditLinkContentRepository(listOf(blocked, web), newCacheStore())

        val error = repository.resolve("https://www.reddit.com/r/pics/comments/abc123/title/").exceptionOrNull()

        assertEquals("Reddit blocked the page in the browser (HTTP 403)", error?.message)
        assertTrue(error is IOException)
    }

    @Test
    fun refreshBypassesTheCache() = runTest {
        val loader = FakeLoader(imagePost())
        val repository = RedditLinkContentRepository(listOf(loader), newCacheStore())
        val url = "https://www.reddit.com/comments/abc123/"

        repository.resolve(url).getOrThrow()
        repository.refresh(url).getOrThrow()

        assertEquals(2, loader.urls.size)
    }

    @Test
    fun resolvedContentRoundTripsThroughTheCacheJson() = runTest {
        val repository = RedditLinkContentRepository(listOf(FakeLoader(imagePost())), newCacheStore())
        val content = repository.resolve("https://redd.it/abc123").getOrThrow()
        val json = Json { ignoreUnknownKeys = true }

        val decoded = json.decodeFromString<app.pane.android.domain.model.LinkContent>(
            json.encodeToString(content),
        )

        assertEquals("abc123", (decoded.sourceMetadata as RedditMetadata).postId)
        assertEquals(LinkSource.Reddit, decoded.source)
    }

    @Test
    fun loadMoreCommentsAppendsTheNextPublicPage() = runTest {
        val url = "https://www.reddit.com/comments/abc123/"
        var requested: String? = null
        lateinit var connection: MoreCommentsConnection
        val more = RedditMoreComments { requestUrl ->
            requested = requestUrl
            MoreCommentsConnection(requestUrl, MORE_CHILDREN_PAGE).also { connection = it }
        }
        val repository = RedditLinkContentRepository(
            listOf(FakeLoader(imagePost().copy(moreCommentIds = listOf("c3")))),
            newCacheStore(),
            more,
        )

        repository.resolve(url).getOrThrow()
        val paged = repository.loadMoreComments(url).getOrThrow()

        assertEquals(
            "https://www.reddit.com/api/morechildren.json?api_type=json&raw_json=1&sort=confidence&limit_children=false&link_id=t3_abc123&children=c3",
            requested,
        )
        assertFalse(requested.orEmpty().contains("%2C"))
        assertEquals(RedditFetchPlan.DESKTOP_USER_AGENT, connection.getRequestProperty("User-Agent"))
        assertEquals(listOf("Nice shot", "Later"), paged.comments.map { it.body })
        assertEquals(listOf("c4"), (paged.sourceMetadata as RedditMetadata).moreCommentIds)
    }

    @Test
    fun loadMoreCommentsClearsTheCursorWhenThePageAddsNothing() = runTest {
        val url = "https://www.reddit.com/comments/abc123/"
        val more = RedditMoreComments { requestUrl ->
            MoreCommentsConnection(requestUrl, """{"json":{"data":{"things":[]}}}""")
        }
        val repository = RedditLinkContentRepository(
            listOf(FakeLoader(imagePost().copy(moreCommentIds = listOf("c3")))),
            newCacheStore(),
            more,
        )

        repository.resolve(url).getOrThrow()
        val paged = repository.loadMoreComments(url).getOrThrow()

        assertEquals(listOf("Nice shot"), paged.comments.map { it.body })
        assertTrue((paged.sourceMetadata as RedditMetadata).moreCommentIds.isEmpty())
    }

    @Test
    fun loadMoreCommentsKeepsTheCursorWhenTheRequestFails() = runTest {
        val url = "https://www.reddit.com/comments/abc123/"
        val more = RedditMoreComments { requestUrl ->
            MoreCommentsConnection(requestUrl, "nope", status = 404)
        }
        val repository = RedditLinkContentRepository(
            listOf(FakeLoader(imagePost().copy(moreCommentIds = listOf("c3")))),
            newCacheStore(),
            more,
        )

        val loaded = repository.resolve(url).getOrThrow()
        val failed = repository.loadMoreComments(url)

        assertTrue(failed.isFailure)
        assertEquals(listOf("c3"), (loaded.sourceMetadata as RedditMetadata).moreCommentIds)
        val cached = repository.peekCached(url)
        assertEquals(listOf("c3"), (cached?.sourceMetadata as RedditMetadata).moreCommentIds)
    }

    private fun newCacheStore() = LinkContentCacheStore(FakeCacheDataStore())

    private class FakeCacheDataStore : DataStore<LinkContentCacheDocument> {
        private val state = MutableStateFlow(LinkContentCacheDocument())
        override val data = state
        override suspend fun updateData(
            transform: suspend (LinkContentCacheDocument) -> LinkContentCacheDocument,
        ): LinkContentCacheDocument {
            val updated = transform(state.value)
            state.value = updated
            return updated
        }
    }

    private class FailingLoader(private val error: IOException) : RedditPageLoader {
        val urls = mutableListOf<String>()

        override suspend fun resolve(url: String): ParsedRedditPost {
            urls += url
            throw error
        }
    }

    private class FakeLoader(private vararg val posts: ParsedRedditPost) : RedditPageLoader {
        val urls = mutableListOf<String>()

        override suspend fun resolve(url: String): ParsedRedditPost {
            urls += url
            val id = app.pane.android.data.reddit.RedditUrls.direct(url)?.id ?: return posts.first()
            return posts.first { it.id == id }
        }
    }

    private fun imagePost() = ParsedRedditPost(
        id = "abc123",
        title = "A public photo",
        selfText = "",
        author = "alice",
        subreddit = "pics",
        score = 1_200,
        commentCount = 2,
        createdUtcEpochSeconds = 1_700_000_000L,
        permalink = "/r/pics/comments/abc123/a_public_photo/",
        over18 = false,
        spoiler = false,
        isSelf = false,
        media = listOf(
            ParsedRedditMedia(
                id = "abc123",
                imageUrl = "https://i.redd.it/photo.jpg",
                videoUrl = null,
                width = 1080,
                height = 720,
                durationSeconds = null,
                contentDescription = "A public photo",
            ),
        ),
        comments = listOf(
            ParsedRedditComment(
                id = "c1",
                author = "bob",
                body = "Nice shot",
                createdUtcEpochSeconds = 1_700_000_100L,
                isSubmitter = false,
                replies = listOf(
                    ParsedRedditComment(
                        id = "c2",
                        author = "alice",
                        body = "Thanks",
                        createdUtcEpochSeconds = 1_700_000_200L,
                        isSubmitter = true,
                        replies = emptyList(),
                    ),
                ),
            ),
        ),
    )

    private fun textPost() = ParsedRedditPost(
        id = "txt111",
        title = "A question",
        selfText = "Hello there",
        author = "alice",
        subreddit = "ask",
        score = 4,
        commentCount = 0,
        createdUtcEpochSeconds = 1_700_000_000L,
        permalink = "/r/ask/comments/txt111/a_question/",
        over18 = false,
        spoiler = false,
        isSelf = true,
        media = emptyList(),
        comments = emptyList(),
    )

    private fun videoPost() = ParsedRedditPost(
        id = "vid111",
        title = "A clip",
        selfText = "",
        author = "alice",
        subreddit = "videos",
        score = 10,
        commentCount = 0,
        createdUtcEpochSeconds = 1_700_000_000L,
        permalink = "/r/videos/comments/vid111/a_clip/",
        over18 = false,
        spoiler = false,
        isSelf = false,
        media = listOf(
            ParsedRedditMedia(
                id = "vid111",
                imageUrl = "https://preview.redd.it/poster.jpg",
                videoUrl = "https://v.redd.it/clip/DASH_720.mp4",
                width = 1280,
                height = 720,
                durationSeconds = 12,
                contentDescription = "A clip",
            ),
        ),
        comments = emptyList(),
    )
}

private class MoreCommentsConnection(
    url: String,
    private val response: String,
    private val status: Int = 200,
) : HttpURLConnection(URL(url)) {
    override fun getResponseCode(): Int = status
    override fun getInputStream() = ByteArrayInputStream(response.toByteArray(StandardCharsets.UTF_8))
    override fun getErrorStream() = inputStream
    override fun connect() = Unit
    override fun disconnect() = Unit
    override fun usingProxy(): Boolean = false
}

private const val MORE_CHILDREN_PAGE = """
{"json":{"data":{"things":[
  {"kind":"t1","data":{"id":"c3","parent_id":"t3_abc123","author":"cara","body":"Later","created_utc":1,"replies":""}},
  {"kind":"more","data":{"parent_id":"t3_abc123","children":["c4"]}}
]}}}
"""
