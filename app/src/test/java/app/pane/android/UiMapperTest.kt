package app.pane.android

import app.pane.android.data.fixture.FixtureCatalog
import app.pane.android.domain.model.Clock
import app.pane.android.domain.model.Author
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
        assertEquals("Instagram", result.recentLinks.single().sourceLabel)
        assertEquals("IG", result.recentLinks.single().sourceChip)
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

        assertEquals("Reddit", home.recentLinks.single().sourceLabel)
        assertEquals("Reddit", home.recentLinks.single().sourceChip)
        assertEquals(null, home.recentLinks.single().thumbnail)
        assertTrue(viewed.isVideo)
        assertEquals("https://v.redd.it/clip/DASH_720.mp4", viewed.videoUrl)
        assertEquals("https://preview.redd.it/poster.jpg", (viewed.media as app.pane.android.ui.model.UiImage.Url).value)
    }

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
