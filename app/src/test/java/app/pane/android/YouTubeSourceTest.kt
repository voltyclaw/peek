package app.pane.android

import app.pane.android.data.links.PaneEntry
import app.pane.android.data.links.YOUTUBE_PACKAGE
import app.pane.android.data.links.paneEntry
import app.pane.android.data.links.profileLink
import app.pane.android.data.history.HistoryUrls
import app.pane.android.data.youtube.YouTubeDataApiClient
import app.pane.android.data.youtube.YouTubeJson
import app.pane.android.data.youtube.YouTubeLinkContentRepository
import app.pane.android.data.youtube.YouTubeTransport
import app.pane.android.data.youtube.YouTubeUrls
import app.pane.android.domain.model.SourceApp
import app.pane.android.domain.youtube.MapYouTubeConsentStore
import app.pane.android.domain.youtube.YouTubeComments
import app.pane.android.domain.youtube.YouTubeCopyExpiry
import app.pane.android.domain.youtube.YouTubeEntry
import app.pane.android.domain.youtube.YouTubeLinkKind
import app.pane.android.domain.youtube.YouTubeLinks
import app.pane.android.domain.youtube.YouTubePlayback
import app.pane.android.domain.youtube.YouTubePlayer
import app.pane.android.domain.youtube.YouTubeSession
import app.pane.android.domain.youtube.YouTubeShorts
import app.pane.android.domain.youtube.YouTubeSiteData
import app.pane.android.domain.youtube.YouTubeSlot
import app.pane.android.ui.actions.openInAppTargets
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class YouTubeSourceTest {
    @Test
    fun urlsClassifyWatchShortLiveAndChannel() {
        val watch = YouTubeUrls.parse("https://www.youtube.com/watch?v=$VIDEO&t=90&list=PL1")
        assertEquals(YouTubeLinkKind.Watch, watch?.kind)
        assertEquals(VIDEO, watch?.videoId)
        assertEquals(90, watch?.startSeconds)
        assertEquals("https://www.youtube.com/watch?v=$VIDEO", watch?.canonicalUrl)

        assertEquals(YouTubeLinkKind.Watch, YouTubeUrls.parse("https://youtu.be/$VIDEO?t=1m30s")?.kind)
        assertEquals(90, YouTubeLinks.startSeconds("1m30s"))
        assertEquals(3723, YouTubeLinks.startSeconds("1h2m3s"))
        assertEquals(YouTubeLinkKind.Watch, YouTubeUrls.parse("https://m.youtube.com/watch?v=$VIDEO")?.kind)
        assertEquals(YouTubeLinkKind.Short, YouTubeUrls.parse("https://www.youtube.com/shorts/$VIDEO")?.kind)
        assertEquals(YouTubeLinkKind.Live, YouTubeUrls.parse("https://www.youtube.com/live/$VIDEO")?.kind)
        assertEquals(YouTubeLinkKind.Embed, YouTubeUrls.parse("https://www.youtube-nocookie.com/embed/$VIDEO")?.kind)

        val handle = YouTubeUrls.parse("https://www.youtube.com/@pane")
        assertEquals(YouTubeLinkKind.Channel, handle?.kind)
        assertNull(handle?.videoId)
        assertEquals(YouTubeLinkKind.Channel, YouTubeUrls.parse("https://www.youtube.com/channel/UC1234567890")?.kind)
        assertEquals(YouTubeLinkKind.Channel, YouTubeUrls.parse("https://www.youtube.com/c/Pane")?.kind)
        assertEquals(YouTubeLinkKind.Playlist, YouTubeUrls.parse("https://www.youtube.com/playlist?list=PL1")?.kind)
        assertNull(YouTubeUrls.videoId("https://www.youtube.com/"))

        assertEquals(SourceApp.YouTube, HistoryUrls.sourceApp("https://youtu.be/$VIDEO"))
        assertEquals("https://www.youtube.com/watch?v=$VIDEO", HistoryUrls.canonical("https://m.youtube.com/shorts/$VIDEO?si=abc"))
    }

    @Test
    fun channelLinksHandOffToTheYouTubeApp() {
        val channel = "https://www.youtube.com/@pane"
        assertEquals(PaneEntry.ProfileHandoff, paneEntry(channel))
        assertEquals(listOf(YOUTUBE_PACKAGE), profileLink(channel)?.packages)
        assertEquals(PaneEntry.Viewer, paneEntry("https://www.youtube.com/watch?v=$VIDEO"))
        assertEquals(YOUTUBE_PACKAGE, openInAppTargets("https://youtu.be/$VIDEO").single().packageName)
    }

    @Test
    fun theGateMakesNoNetworkCallBeforeTheTap() = runBlocking {
        val transport = RecordingTransport()
        val store = MapYouTubeConsentStore()
        val api = YouTubeDataApiClient("test-key", transport)
        val session = YouTubeSession(store, api, NoSiteData)
        val repository = YouTubeLinkContentRepository(store, api)

        repository.resolve("https://www.youtube.com/watch?v=$VIDEO")
        YouTubeEntry.entries.forEach { entry ->
            assertTrue(session.open(VIDEO, entry) is app.pane.android.domain.youtube.YouTubeSurface.Poster)
        }
        assertTrue(transport.urls.isEmpty())
        assertNull(
            YouTubePlayback.iframeHtml(VIDEO, 0, YouTubeLinkKind.Watch, consented = false, playShorts = false),
        )

        session.accept(VIDEO, 50L)
        assertTrue(transport.urls.any { "youtube/v3/videos" in it })
        assertTrue(transport.urls.none { "oembed" in it || "iframe_api" in it })
        repository.resolve("https://www.youtube.com/watch?v=$VIDEO")
        assertTrue(transport.urls.any { "order=relevance" in it })
        val player = YouTubePlayback.iframeHtml(VIDEO, 12, YouTubeLinkKind.Watch, consented = true, playShorts = false)
        assertTrue(player!!.contains("rel: 0"))
        assertTrue(player.contains("controls: 1"))
        assertFalse(player.contains("googlevideo"))
        assertTrue(YouTubePlayer.embedUrl(VIDEO).startsWith("https://www.youtube-nocookie.com/embed/"))
    }

    @Test
    fun missingApiKeyUsesOEmbedAndLeavesCommentsUnavailable() = runBlocking {
        val transport = RecordingTransport()
        val store = MapYouTubeConsentStore()
        val api = YouTubeDataApiClient(apiKey = null, transport = transport)
        val session = YouTubeSession(store, api, NoSiteData)
        session.accept(VIDEO, 50L)
        val content = YouTubeLinkContentRepository(store, api).resolve("https://youtu.be/$VIDEO").getOrThrow()

        assertTrue(transport.urls.all { "oembed" in it })
        assertTrue(transport.urls.none { "googleapis.com" in it })
        assertEquals("Played", content.title)
        assertEquals("Channel", content.author.name)
        assertEquals(
            app.pane.android.domain.model.YouTubeCommentsState.Unavailable,
            (content.sourceMetadata as app.pane.android.domain.model.YouTubeMetadata).commentsState,
        )
    }

    @Test
    fun shortsSwitchPlaysOrBlocks() = runBlocking {
        assertEquals(YouTubeShorts.Path.Block, YouTubeShorts.path())
        assertEquals(YouTubeShorts.Path.Play, YouTubeShorts.path(playInPane = true))
        assertEquals(
            YouTubeSlot.Blocked,
            YouTubePlayback.slot(YouTubeLinkKind.Short, consented = true, playShorts = false),
        )
        assertEquals(
            YouTubeSlot.Player,
            YouTubePlayback.slot(YouTubeLinkKind.Short, consented = true, playShorts = true),
        )
        assertNull(YouTubePlayback.iframeHtml(VIDEO, 0, YouTubeLinkKind.Short, consented = true, playShorts = false))
        val played = YouTubePlayback.iframeHtml(VIDEO, 0, YouTubeLinkKind.Short, consented = true, playShorts = true)
        assertTrue(played!!.contains("rel: 0"))

        val transport = RecordingTransport()
        val store = MapYouTubeConsentStore()
        store.write(app.pane.android.domain.youtube.YouTubeConsent("2026-10-09", 50L))
        val api = YouTubeDataApiClient("test-key", transport)
        val blocked = YouTubeLinkContentRepository(store, api, playShorts = false)
        blocked.resolve("https://www.youtube.com/shorts/$VIDEO")
        assertTrue(transport.urls.isEmpty())

        val playing = YouTubeLinkContentRepository(store, api, playShorts = true)
        playing.resolve("https://www.youtube.com/shorts/$VIDEO")
        assertTrue(transport.urls.any { "youtube/v3/videos" in it })
    }

    @Test
    fun commentPagesFollowRelevanceAndStopAtTheGuard() {
        val first = YouTubeComments.query(VIDEO, null, 0, "key") as YouTubeComments.Query.Page
        assertTrue(first.url.contains("order=relevance"))
        assertFalse(first.url.contains("pageToken="))
        val next = YouTubeComments.query(VIDEO, "NEXT", 1, "key") as YouTubeComments.Query.Page
        assertTrue(next.url.contains("pageToken=NEXT"))
        assertTrue(YouTubeComments.query(VIDEO, "NEXT", YouTubeComments.MAX_PAGES, "key") is YouTubeComments.Query.HardWall)
        assertTrue(YouTubeComments.query(VIDEO, null, 0, "") is YouTubeComments.Query.Unavailable)

        val page = YouTubeJson.comments(COMMENT_JSON, pagesLoaded = 0)
        assertEquals("TOK", page.nextPageToken)
        assertEquals("Hello", page.comments.single().body)
        assertEquals(1, page.pagesLoaded)
        assertFalse(YouTubeCopyExpiry.stale(1_000L, 1_000L + YouTubeCopyExpiry.WINDOW_MILLIS - 1))
        assertTrue(YouTubeCopyExpiry.stale(1_000L, 1_000L + YouTubeCopyExpiry.WINDOW_MILLIS))
    }

    private class RecordingTransport : YouTubeTransport {
        val urls = mutableListOf<String>()
        override fun get(url: String): String {
            urls += url
            return when {
                "commentThreads" in url -> COMMENT_JSON
                "/channels" in url -> CHANNEL_JSON
                "/videos" in url -> VIDEO_JSON
                "oembed" in url -> OEMBED_JSON
                else -> "{}"
            }
        }
    }

    private object NoSiteData : YouTubeSiteData {
        override fun clear() = Unit
    }

    private companion object {
        const val VIDEO = "dQw4w9WgXcQ"
        const val VIDEO_JSON = """
            {"items":[{"snippet":{"title":"Played","description":"About","channelId":"UC1","channelTitle":"Channel","publishedAt":"2020-01-02T00:00:00Z"},"statistics":{"viewCount":"12000"},"status":{"embeddable":true}}]}
        """
        const val CHANNEL_JSON = """
            {"items":[{"snippet":{"title":"Channel","customUrl":"@channel","thumbnails":{"default":{"url":"https://example.com/a.jpg"}}}}]}
        """
        const val OEMBED_JSON = """
            {"title":"Played","author_name":"Channel","author_url":"https://www.youtube.com/@channel","thumbnail_url":"https://example.com/t.jpg"}
        """
        const val COMMENT_JSON = """
            {"nextPageToken":"TOK","items":[{"id":"c1","snippet":{"topLevelComment":{"snippet":{"authorDisplayName":"Ada","textDisplay":"Hello","authorProfileImageUrl":"https://example.com/p.jpg","publishedAt":"2020-01-02T00:00:00Z"}}}}]}
        """
    }
}
