package app.pane.android

import app.pane.android.data.sample.SampleLinkContentRepository
import app.pane.android.data.sample.SampleMedia
import app.pane.android.data.sample.SamplePosts
import app.pane.android.domain.model.ExternalPostMetadata
import app.pane.android.domain.model.InstagramMetadata
import app.pane.android.domain.model.LinkKind
import app.pane.android.domain.model.MediaLocation
import app.pane.android.domain.model.RedditMetadata
import app.pane.android.ui.actions.openInAppLabelRes
import app.pane.android.ui.actions.sourceMarkRes
import app.pane.android.ui.mapper.UiImageMapper
import app.pane.android.ui.mapper.ViewerUiMapper
import app.pane.android.ui.model.CommentUiModel
import app.pane.android.ui.viewer.outboundLink
import java.io.IOException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SamplePostsTest {
    private val files = SampleMedia.Files(
        ridge = "file:///tmp/ridge.jpg",
        link = "file:///tmp/link.jpg",
        kyoto = "file:///tmp/kyoto.jpg",
        material = "file:///tmp/material.jpg",
        video = "file:///tmp/reel.mp4",
    )
    private val repository = SampleLinkContentRepository(files)
    private val mapper = ViewerUiMapper(UiImageMapper())

    @Test
    fun catalogCoversTheSourceMatrix() {
        val ids = SamplePosts.entries.map { it.id }
        assertEquals(
            listOf(
                "x-image",
                "x-video",
                "x-text",
                "x-link",
                "x-thread",
                "x-gallery",
                "ig-image",
                "ig-carousel",
                "ig-reel",
                "ig-story",
                "fb-text",
                "fb-image",
                "fb-reel",
                "fb-story",
                "reddit-text",
                "reddit-image",
                "reddit-video",
                "reddit-link",
                "error",
                "unsupported",
            ),
            ids,
        )
        assertEquals(listOf("X", "Instagram", "Facebook", "Reddit", "Other"), SamplePosts.entries.map { it.group }.distinct())
    }

    @Test
    fun deepLinksResolveToCanonicalPosts() {
        SamplePosts.entries.forEach { entry ->
            assertEquals(entry.canonicalUrl, SamplePosts.canonicalForDeepLink(SamplePosts.deepLink(entry.id)))
            assertEquals(entry, SamplePosts.entryFor(SamplePosts.deepLink(entry.id)))
            assertEquals(entry.canonicalUrl, SamplePosts.canonicalForDeepLink("pane://sample/${entry.id}/"))
        }
        assertNull(SamplePosts.canonicalForDeepLink("pane://sample/missing"))
        assertNull(SamplePosts.canonicalForDeepLink("https://x.com/mayaren/status/1842700101"))
    }

    @Test
    fun postsUseEachSourceShape() = runBlocking {
        val image = post("x-image")
        assertEquals("@mayaren", image.author.metadata)
        assertEquals("PHOTO", image.media.badge)
        assertTrue(image.commentCount > image.comments.size)
        assertEquals(1, (image.sourceMetadata as ExternalPostMetadata).mediaItems.size)
        val imageUi = mapper.map(image)
        assertTrue(imageUi.commentsTruncated)
        assertTrue(!imageUi.canLoadMoreComments)

        val video = post("x-video")
        assertEquals(LinkKind.Video, video.kind)
        assertEquals(files.video, (video.sourceMetadata as ExternalPostMetadata).mediaItems.first().videoUrl)

        val text = post("x-text")
        assertEquals("TEXT", text.media.badge)
        assertTrue((text.media.location as MediaLocation.Remote).url.isBlank())
        assertTrue((text.sourceMetadata as ExternalPostMetadata).mediaItems.isEmpty())
        assertNull(outboundLink(text.title, text.url))

        val link = post("x-link")
        assertEquals("https://longtrail.review/quiet-loop", outboundLink(link.title, link.url))
        assertEquals(files.link, (link.media.location as MediaLocation.Remote).url)

        val thread = post("x-thread")
        val threadMeta = thread.sourceMetadata as ExternalPostMetadata
        val posts = threadMeta.authorThread
        assertTrue(posts.size >= 5)
        assertEquals(threadMeta.postId, posts.last().id)
        assertEquals(posts.size, mapper.map(thread).authorThread.size)

        val gallery = post("x-gallery")
        assertTrue((gallery.sourceMetadata as ExternalPostMetadata).mediaItems.size >= 2)
        assertEquals("GALLERY", gallery.media.badge)

        val igImage = post("ig-image")
        val igMeta = igImage.sourceMetadata as InstagramMetadata
        assertEquals("INSTAGRAM · 128 LIKES", igImage.author.metadata)
        assertEquals("2", igMeta.commentsEndCursor)
        assertTrue(mapper.map(igImage).canLoadMoreComments)
        assertTrue(igImage.comments.any { it.replies.isNotEmpty() })

        val carousel = post("ig-carousel")
        assertEquals("CAROUSEL", carousel.media.badge)
        assertTrue((carousel.sourceMetadata as InstagramMetadata).mediaItems.size >= 2)

        val reel = post("ig-reel")
        assertEquals(LinkKind.Video, reel.kind)
        assertEquals("REEL", reel.media.badge)
        assertEquals("INSTAGRAM · VERIFIED · 240 LIKES", reel.author.metadata)
        assertEquals(files.video, (reel.sourceMetadata as InstagramMetadata).videoVariants.first().url)

        val fbText = post("fb-text")
        assertEquals("mayaren", fbText.author.metadata)
        assertTrue(fbText.title.length > 240)
        assertTrue((fbText.sourceMetadata as ExternalPostMetadata).mediaItems.isEmpty())
        assertEquals(0, fbText.commentCount)

        val fbImage = post("fb-image")
        assertEquals("PHOTO", fbImage.media.badge)
        assertEquals(files.kyoto, (fbImage.media.location as MediaLocation.Remote).url)

        val fbReel = post("fb-reel")
        assertEquals(LinkKind.Video, fbReel.kind)
        assertEquals("VIDEO", fbReel.media.badge)
        assertEquals(files.video, (fbReel.sourceMetadata as ExternalPostMetadata).mediaItems.first().videoUrl)

        val redditText = post("reddit-text")
        assertTrue(redditText.author.metadata.startsWith("REDDIT · r/hiking · "))
        assertTrue(redditText.comments.any { it.replies.isNotEmpty() })
        assertTrue((redditText.sourceMetadata as RedditMetadata).moreCommentIds.isNotEmpty())
        assertTrue(mapper.map(redditText).canLoadMoreComments)
        assertEquals("TEXT", redditText.media.badge)

        val redditImage = post("reddit-image")
        assertEquals("PHOTO", redditImage.media.badge)
        assertEquals(1, (redditImage.sourceMetadata as RedditMetadata).mediaItems.size)

        val redditVideo = post("reddit-video")
        assertEquals(LinkKind.Video, redditVideo.kind)
        assertEquals("00:03", redditVideo.media.duration)
        assertEquals(files.video, (redditVideo.sourceMetadata as RedditMetadata).mediaItems.first().videoUrl)

        val redditLink = post("reddit-link")
        assertEquals("https://longtrail.review/ridge-notes", outboundLink(redditLink.title, redditLink.url))
        assertEquals(files.link, (redditLink.media.location as MediaLocation.Remote).url)

        assertEquals(R.drawable.ic_source_x, sourceMarkRes(image.url))
        assertEquals(R.drawable.ic_source_instagram, sourceMarkRes(reel.url))
        assertEquals(R.drawable.ic_source_facebook, sourceMarkRes(fbImage.url))
        assertEquals(R.drawable.ic_source_reddit, sourceMarkRes(redditText.url))
        assertEquals(R.string.open_in_x, openInAppLabelRes(image.url))
        assertEquals(R.string.open_in_instagram, openInAppLabelRes(reel.url))
        assertEquals(R.string.open_in_facebook, openInAppLabelRes(fbReel.url))
        assertEquals(R.string.open_in_reddit, openInAppLabelRes(redditText.url))
    }

    @Test
    fun redditAndInstagramPageCommentsLocally() = runBlocking {
        val redditUrl = url("reddit-text")
        val first = repository.resolve(redditUrl).getOrThrow()
        val firstCount = count(mapper.map(first).comments)
        val second = repository.loadMoreComments(redditUrl).getOrThrow()
        val secondMeta = second.sourceMetadata as RedditMetadata
        assertTrue(count(mapper.map(second).comments) > firstCount)
        assertTrue(secondMeta.moreCommentIds.isEmpty())
        assertTrue(!mapper.map(second).canLoadMoreComments)
        val refreshed = repository.refresh(redditUrl).getOrThrow()
        assertTrue((refreshed.sourceMetadata as RedditMetadata).moreCommentIds.isNotEmpty())

        val igUrl = url("ig-image")
        val igFirst = repository.resolve(igUrl).getOrThrow()
        assertEquals("2", (igFirst.sourceMetadata as InstagramMetadata).commentsEndCursor)
        val igSecond = repository.loadMoreComments(igUrl).getOrThrow()
        assertNull((igSecond.sourceMetadata as InstagramMetadata).commentsEndCursor)
        assertTrue(igSecond.comments.size > igFirst.comments.size)
    }

    @Test
    fun errorAndUnsupportedStayOffline() = runBlocking {
        val error = repository.resolve(url("error"))
        val failure = error.exceptionOrNull()
        assertTrue(failure is app.pane.android.domain.model.SourceFailure.Gone)
        assertTrue(failure!!.message!!.contains("removed"))
        assertEquals("This post was removed", repository.peekCached(url("error"))?.title)

        val unsupported = repository.resolve(url("unsupported"))
        assertTrue(unsupported.exceptionOrNull() is app.pane.android.domain.model.SourceFailure.Unsupported)
        assertEquals("Marketplace listing", repository.peekCached(url("unsupported"))?.title)
        assertEquals(R.drawable.ic_source_facebook, sourceMarkRes(url("unsupported")))
        assertEquals(R.string.open_in_facebook, openInAppLabelRes(url("unsupported")))
        assertNull(sourceMarkRes("https://www.youtube.com/watch?v=abc123"))
    }

    private suspend fun post(id: String) = repository.resolve(url(id)).getOrThrow()

    private fun url(id: String) = SamplePosts.entry(id)!!.canonicalUrl

    private fun count(comments: List<CommentUiModel>): Int =
        comments.sumOf { 1 + count(it.replies) }
}
