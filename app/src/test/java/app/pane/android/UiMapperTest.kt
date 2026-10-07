package app.pane.android

import app.pane.android.data.fixture.FixtureCatalog
import app.pane.android.domain.model.Clock
import app.pane.android.domain.model.Author
import app.pane.android.domain.model.Comment
import app.pane.android.domain.model.ExternalPostMetadata
import app.pane.android.domain.model.ExternalThreadPost
import app.pane.android.domain.model.XReplyContinuation
import app.pane.android.domain.model.InstagramMediaItem
import app.pane.android.domain.model.InstagramMetadata
import app.pane.android.domain.model.LinkContent
import app.pane.android.domain.model.LinkKind
import app.pane.android.domain.model.LinkSource
import app.pane.android.domain.model.Media
import app.pane.android.domain.model.MediaLocation
import app.pane.android.domain.model.RecentContent
import app.pane.android.domain.model.RedditMediaItem
import app.pane.android.domain.model.RedditMetadata
import app.pane.android.domain.model.RecentLink
import app.pane.android.ui.mapper.HomeUiMapper
import app.pane.android.ui.mapper.UiImageMapper
import app.pane.android.ui.mapper.ViewerUiMapper
import app.pane.android.ui.model.HomeUiState
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UiMapperTest {
    private val now = 1_800_000L

    @Test
    fun homeMapperProducesCompleteRenderingModels() {
        val content = FixtureCatalog.contents.getValue(FixtureCatalog.KYOTO_URL)
        val mapper = HomeUiMapper(UiImageMapper(), Clock { now }, ZoneId.of("UTC"))

        val result = mapper.map(listOf(RecentContent(RecentLink(content.url, now - 120_000), content)))

        assertTrue(result is HomeUiState.Content)
        result as HomeUiState.Content
        assertEquals("A quiet morning in Kyoto", result.recentLinks.single().title)
        assertEquals("2m", result.recentLinks.single().ageLabel)
        assertEquals("INSTAGRAM · POST", result.recentLinks.single().sourceLabel)
    }

    @Test
    fun viewerMapperRetainsThreadStructure() {
        val content = FixtureCatalog.contents.getValue(FixtureCatalog.MATERIAL_URL)

        val result = ViewerUiMapper(UiImageMapper()).map(content)

        assertTrue(result.isVideo)
        assertEquals(2, result.comments.size)
        assertEquals(1, result.comments.first().replies.size)
    }

    @Test
    fun viewerMapperHonorsRequestedCarouselIndex() {
        val base = FixtureCatalog.contents.getValue(FixtureCatalog.KYOTO_URL)
        val content = base.copy(
            url = "https://www.instagram.com/p/example/?img_index=2",
            sourceMetadata = InstagramMetadata(
                postId = "post",
                shortcode = "example",
                code = "example",
                takenAtEpochSeconds = 0,
                likeCount = 0,
                commentCount = 0,
                authorId = "author",
                authorUsername = "author",
                authorFullName = null,
                authorProfilePictureUrl = null,
                authorIsVerified = false,
                videoVariants = emptyList(),
                mediaItems = listOf(
                    InstagramMediaItem("one", "https://example.com/one.jpg", "One"),
                    InstagramMediaItem("two", "https://example.com/two.jpg", "Two"),
                ),
            ),
        )

        val result = ViewerUiMapper(UiImageMapper()).map(content)

        assertEquals(2, result.mediaItems.size)
        assertEquals(1, result.initialMediaIndex)
    }

    @Test
    fun redditVideoAndTextPostsMapOntoTheSharedViewer() {
        val mapper = HomeUiMapper(UiImageMapper(), Clock { now }, ZoneId.of("UTC"))
        val text = redditContent(
            url = "https://www.reddit.com/r/ask/comments/txt111/a_question/",
            title = "A question",
            kind = LinkKind.Post,
            imageUrl = "",
            videoUrl = null,
        )
        val video = redditContent(
            url = "https://www.reddit.com/r/videos/comments/vid111/a_clip/",
            title = "A clip",
            kind = LinkKind.Video,
            imageUrl = "https://preview.redd.it/poster.jpg",
            videoUrl = "https://v.redd.it/clip/DASH_720.mp4",
        )

        val home = mapper.map(
            listOf(RecentContent(RecentLink(text.url, now - 60_000), text)),
        ) as HomeUiState.Content
        val viewed = ViewerUiMapper(UiImageMapper()).map(video)

        assertEquals("REDDIT · POST", home.recentLinks.single().sourceLabel)
        assertEquals(null, home.recentLinks.single().thumbnail)
        assertTrue(viewed.isVideo)
        assertEquals("https://v.redd.it/clip/DASH_720.mp4", viewed.videoUrl)
        assertEquals("https://preview.redd.it/poster.jpg", (viewed.media as app.pane.android.ui.model.UiImage.Url).value)
    }

    @Test
    fun threadDropsTheOpenedStatusForASinglePostAndAnAuthorThread() {
        val mapper = ViewerUiMapper(UiImageMapper())
        val single = mapper.map(
            xContent(
                postId = "200",
                comments = listOf(
                    comment("200", "the opened post"),
                    comment("9", "a real reply", handle = "happy", avatar = "https://pbs.twimg.com/profile_images/happy.jpg"),
                ),
                commentCount = 5,
                continuation = XReplyContinuation.Blocked,
            ),
        )
        val thread = mapper.map(
            xContent(
                postId = "200",
                comments = listOf(comment("200", "the opened post"), comment("9", "a real reply")),
                commentCount = 2,
                continuation = XReplyContinuation.Exhausted,
                authorThread = listOf(
                    ExternalThreadPost("19", "Anshu", "earlier"),
                    ExternalThreadPost("200", "Anshu", "the opened post"),
                ),
            ),
        )

        assertEquals(listOf("9"), single.comments.map { it.id })
        assertEquals("happy", single.comments.single().handle)
        assertEquals("https://pbs.twimg.com/profile_images/happy.jpg", single.comments.single().avatarUrl)
        assertTrue(single.commentsTruncated)
        assertFalse(single.canLoadMoreComments)
        assertEquals(listOf("9"), thread.comments.map { it.id })
        assertEquals(listOf("19", "200"), thread.authorThread.map { it.id })
        assertFalse(thread.commentsTruncated)
    }

    @Test
    fun xCursorKeepsPagingUntilTheThreadIsExhausted() {
        val mapper = ViewerUiMapper(UiImageMapper())
        val paging = mapper.map(
            xContent(
                postId = "1",
                comments = listOf(comment("2", "first")),
                commentCount = 4,
                continuation = XReplyContinuation.More,
                cursor = "cursor-next",
            ),
        )
        val done = mapper.map(
            xContent(
                postId = "1",
                comments = listOf(comment("2", "first")),
                commentCount = 4,
                continuation = XReplyContinuation.Exhausted,
            ),
        )

        assertTrue(paging.canLoadMoreComments)
        assertFalse(paging.commentsTruncated)
        assertFalse(done.canLoadMoreComments)
        assertFalse(done.commentsTruncated)
    }

    private fun comment(
        id: String,
        body: String,
        handle: String? = null,
        avatar: String? = null,
    ): Comment = Comment(
        id = id,
        author = "Ada",
        initial = "A",
        age = "1h",
        body = body,
        handle = handle,
        avatarUrl = avatar,
    )

    private fun xContent(
        postId: String,
        comments: List<Comment>,
        commentCount: Int,
        continuation: XReplyContinuation?,
        cursor: String? = null,
        authorThread: List<ExternalThreadPost> = emptyList(),
    ): LinkContent = LinkContent(
        url = "https://x.com/anshuc/status/$postId",
        title = "the opened post",
        source = LinkSource.X,
        kind = LinkKind.Post,
        thumbnail = MediaLocation.Remote(""),
        media = Media(MediaLocation.Remote(""), "the opened post", badge = "TEXT"),
        author = Author("Anshu", "@anshuc", avatarUrl = "https://pbs.twimg.com/profile_images/anshu.jpg"),
        commentCount = commentCount,
        comments = comments,
        sourceMetadata = ExternalPostMetadata(
            postId = postId,
            authorThread = authorThread,
            repliesCursor = cursor,
            replyContinuation = continuation,
        ),
    )

    private fun redditContent(
        url: String,
        title: String,
        kind: LinkKind,
        imageUrl: String,
        videoUrl: String?,
    ): LinkContent = LinkContent(
        url = url,
        title = title,
        source = LinkSource.Reddit,
        kind = kind,
        thumbnail = MediaLocation.Remote(imageUrl),
        media = Media(MediaLocation.Remote(imageUrl), title, badge = if (videoUrl == null) "TEXT" else "VIDEO"),
        author = Author("alice", "REDDIT · r/pics · 1 POINT"),
        commentCount = 0,
        comments = emptyList(),
        sourceMetadata = RedditMetadata(
            postId = "post",
            subreddit = "pics",
            permalink = "/r/pics/comments/post/title/",
            score = 1,
            commentCount = 0,
            createdUtcEpochSeconds = 0,
            author = "alice",
            over18 = false,
            spoiler = false,
            mediaItems = if (videoUrl == null && imageUrl.isBlank()) {
                emptyList()
            } else {
                listOf(RedditMediaItem("post", imageUrl, title, videoUrl))
            },
        ),
    )
}
