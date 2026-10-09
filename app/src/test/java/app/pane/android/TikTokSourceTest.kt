package app.pane.android

import app.pane.android.data.history.JdbcHistorySql
import app.pane.android.data.history.SqliteHistoryRepository
import app.pane.android.data.history.StarImageStore
import app.pane.android.data.history.PinnedStarFiles
import app.pane.android.data.links.TIKTOK_PACKAGE
import app.pane.android.data.links.TIKTOK_TRILL_PACKAGE
import app.pane.android.data.links.profileLink
import app.pane.android.data.tiktok.FileTikTokOEmbedDisk
import app.pane.android.data.tiktok.TikTokHttpResponse
import app.pane.android.data.tiktok.TikTokLinkContentRepository
import app.pane.android.data.tiktok.TikTokOEmbedClient
import app.pane.android.data.tiktok.TikTokTransport
import app.pane.android.data.tiktok.TikTokUrls
import app.pane.android.domain.tiktok.TikTokWebOrigins
import app.pane.android.domain.model.Clock
import app.pane.android.domain.model.HistoryView
import app.pane.android.domain.model.StarCopy
import app.pane.android.domain.model.StarImageBytes
import app.pane.android.domain.tiktok.MapTikTokConsentStore
import app.pane.android.domain.tiktok.TikTokBackoff
import app.pane.android.domain.tiktok.TikTokCaption
import app.pane.android.domain.tiktok.TikTokConsent
import app.pane.android.domain.tiktok.TikTokConsentPolicy
import app.pane.android.domain.tiktok.TikTokCopyRetention
import app.pane.android.domain.tiktok.TikTokIds
import app.pane.android.domain.tiktok.TikTokLinkKind
import app.pane.android.domain.tiktok.TikTokOEmbedResult
import app.pane.android.domain.tiktok.TikTokPlayback
import app.pane.android.domain.tiktok.TikTokPlayer
import app.pane.android.domain.tiktok.TikTokRedirects
import app.pane.android.domain.tiktok.TikTokScrollRefresh
import app.pane.android.domain.tiktok.TikTokSession
import app.pane.android.domain.tiktok.TikTokSiteData
import app.pane.android.domain.tiktok.TikTokThumbs
import app.pane.android.domain.tiktok.TikTokVisibleRow
import app.pane.android.ui.actions.openInAppTargets
import java.io.File
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TikTokSourceTest {
    private val ID = "6718335390845095173"

    @Test
    fun urlsClassifyVideoPhotoShortLiveAndProfile() {
        val video = TikTokUrls.parse("https://www.tiktok.com/@scout/video/$ID?_r=1")
        assertEquals(TikTokLinkKind.Video, video?.kind)
        assertEquals(ID, video?.videoId)
        assertEquals("scout", video?.handle)
        assertEquals("https://www.tiktok.com/@scout/video/$ID", video?.canonicalUrl)

        val photo = TikTokUrls.parse("https://www.tiktok.com/@scout/photo/$ID")
        assertEquals(TikTokLinkKind.Photo, photo?.kind)
        assertEquals("https://www.tiktok.com/@scout/photo/$ID", photo?.canonicalUrl)

        assertEquals(TikTokLinkKind.Short, TikTokUrls.parse("https://vm.tiktok.com/ZMabc")?.kind)
        assertEquals(TikTokLinkKind.Short, TikTokUrls.parse("https://vt.tiktok.com/ZMabc")?.kind)
        assertEquals(TikTokLinkKind.Short, TikTokUrls.parse("https://www.tiktok.com/t/ZMabc")?.kind)
        assertEquals(ID, TikTokUrls.parse("https://m.tiktok.com/v/$ID.html")?.videoId)
        assertEquals(ID, TikTokUrls.parse("https://www.tiktok.com/embed/v2/$ID")?.videoId)
        assertEquals(TikTokLinkKind.Live, TikTokUrls.parse("https://www.tiktok.com/@scout/live")?.kind)
        assertEquals(TikTokLinkKind.Profile, TikTokUrls.parse("https://www.tiktok.com/@scout")?.kind)
        assertEquals(TikTokLinkKind.Other, TikTokUrls.parse("https://www.tiktok.com/tag/cat")?.kind)
        assertTrue(TikTokUrls.supports("https://vm.tiktok.com/ZMabc"))
        assertFalse(TikTokUrls.supports("https://www.tiktok.com/@scout"))
        assertEquals(
            listOf(TIKTOK_PACKAGE, TIKTOK_TRILL_PACKAGE),
            profileLink("https://www.tiktok.com/@scout")?.packages,
        )
        assertEquals(
            listOf(TIKTOK_PACKAGE, TIKTOK_TRILL_PACKAGE),
            openInAppTargets("https://www.tiktok.com/@scout/video/$ID").map { it.packageName },
        )
    }

    @Test
    fun snowflakeDateMatchesTheDocumentedExample() {
        assertEquals(1_564_234_353L, TikTokIds.postedAtEpochSeconds(ID, 2_000_000_000L))
        assertEquals(
            "Jul 27, 2019",
            TikTokIds.label(1_564_234_353L, Instant.parse("2019-08-10T00:00:00Z").toEpochMilli()),
        )
        assertNull(TikTokIds.postedAtEpochSeconds("1", 2_000_000_000L))
        assertNull(TikTokIds.postedAtEpochSeconds(ID, 1_564_234_352L))
    }

    @Test
    fun nothingContactsTikTokBeforeTheTap() = runBlocking {
        val transport = RecordingTikTokTransport()
        val client = client(transport)
        val store = MapTikTokConsentStore()
        val repository = TikTokLinkContentRepository(store, client, TikTokRedirects { error("redirect") })
        repository.resolve("https://www.tiktok.com/@scout/video/$ID")
        repository.resolve("https://vm.tiktok.com/ZMabc")
        assertTrue(transport.urls.isEmpty())
        assertNull(TikTokPlayback.iframeHtml(ID, consented = false))
        val html = TikTokPlayer.iframeHtml(ID)
        assertTrue(html.contains("rel=0"))
        assertTrue(html.contains("loop=0"))
        assertTrue(html.contains("autoplay=0"))
        assertFalse(html.contains("muted=1"))
        assertTrue(html.contains("seekTo"))
        assertTrue(html.contains("x-tiktok-player"))
    }

    @Test
    fun acceptFetchesOnceAndTheCacheHoldsForADay() {
        val transport = RecordingTikTokTransport()
        var now = 10_000L
        val disk = memoryDisk()
        val client = TikTokOEmbedClient(transport, clock = { now }, sleep = {}, disk = disk)
        val store = MapTikTokConsentStore()
        val session = TikTokSession(store, client, NoTikTokSiteData)
        session.accept(ID, 50L)
        assertEquals(1, transport.urls.size)
        now += TikTokBackoff.CACHE_TTL_MILLIS - 1
        session.open(ID, app.pane.android.domain.tiktok.TikTokEntry.View)
        assertEquals(1, transport.urls.size)
        now += 2
        session.open(ID, app.pane.android.domain.tiktok.TikTokEntry.View)
        assertEquals(2, transport.urls.size)

        val file = File.createTempFile("tt-oembed", ".json")
        val first = TikTokOEmbedClient(transport, clock = { 20_000L }, sleep = {}, disk = FileTikTokOEmbedDisk(file))
        first.fetch(ID, "https://www.tiktok.com/embed/v2/$ID")
        val secondTransport = RecordingTikTokTransport()
        val second = TikTokOEmbedClient(secondTransport, clock = { 20_000L }, sleep = {}, disk = FileTikTokOEmbedDisk(file))
        val cached = second.fetch(ID, "https://www.tiktok.com/embed/v2/$ID")
        assertTrue(cached is TikTokOEmbedResult.Ready)
        assertTrue(secondTransport.urls.isEmpty())
    }

    @Test
    fun backoffSleepsThenStopsAndTheBucketWaitsOneSecond() {
        val transport = RecordingTikTokTransport(code = 429)
        val sleeps = mutableListOf<Long>()
        var now = 5_000L
        val client = TikTokOEmbedClient(
            transport,
            clock = { now },
            sleep = { delay ->
                sleeps += delay
                now += delay
            },
        )
        assertTrue(client.fetch(ID, page()) is TikTokOEmbedResult.Failed)
        assertEquals(listOf(1_000L, 2_000L, 4_000L), sleeps)
        assertEquals(4, transport.urls.size)

        val paced = mutableListOf<Long>()
        var paceNow = 9_000L
        val bucket = TikTokOEmbedClient(
            RecordingTikTokTransport(),
            clock = { paceNow },
            sleep = { delay ->
                paced += delay
                paceNow += delay
            },
        )
        bucket.fetch("111111", page())
        bucket.fetch("222222", page())
        assertEquals(listOf(TikTokBackoff.MIN_INTERVAL_MILLIS), paced)
    }

    @Test
    fun goneStripsAndRateLimitDoesNot() = runBlocking {
        assertTrue(TikTokCopyRetention.stripOnHttpStatus(404))
        assertTrue(TikTokCopyRetention.stripOnHttpStatus(400))
        assertTrue(TikTokCopyRetention.stripOnHttpStatus(410))
        assertTrue(TikTokCopyRetention.keepOnHttpStatus(429))
        assertTrue(TikTokCopyRetention.keepOnHttpStatus(503))
        assertFalse(TikTokCopyRetention.stripOnHttpStatus(429))
        assertFalse(TikTokCopyRetention.stripOnHttpStatus(500))

        val store = MapTikTokConsentStore()
        store.write(TikTokConsent(TikTokConsentPolicy.VERSION, 50L))
        val removed = TikTokOEmbedClient(RecordingTikTokTransport(code = 404), sleep = {})
        val content = TikTokLinkContentRepository(store, removed).resolve(videoUrl()).getOrThrow()
        val meta = content.sourceMetadata as app.pane.android.domain.model.TikTokMetadata
        assertTrue(meta.removed)
        assertEquals("", content.title)

        val limited = TikTokOEmbedClient(RecordingTikTokTransport(code = 429), sleep = {})
        val failed = TikTokLinkContentRepository(store, limited).resolve(videoUrl()).getOrThrow()
        val failedMeta = failed.sourceMetadata as app.pane.android.domain.model.TikTokMetadata
        assertFalse(failedMeta.removed)
        assertTrue(failedMeta.detailsFailed)
        assertEquals("scout", failedMeta.handle)
    }

    @Test
    fun displayCacheStripsAfterThirtyDaysAndAStarDoesNotPinBytes() = runBlocking {
        val clock = MovingClock(1_000L)
        val stars = CountingStars()
        val repository = SqliteHistoryRepository(
            sql = JdbcHistorySql.open(),
            imageStore = stars,
            clock = clock,
        )
        val url = videoUrl()
        repository.recordSuccessfulView(tiktokView(url, at = 1_000L))
        repository.star(
            url,
            StarCopy(title = "Caption", authorName = "Scout", handle = "scout", caption = "Caption", thumbUrl = "https://cdn.example/t"),
            StarImageBytes(thumbnail = byteArrayOf(1, 2, 3)),
        )
        assertEquals(0, stars.pins)
        assertNull(repository.observeHistory().first().single().pinnedThumbPath)
        assertEquals("Caption", repository.observeHistory().first().single().title)

        clock.now = 1_000L + TikTokCopyRetention.WINDOW_MILLIS + 1
        repository.setNote(url, "keep")
        val row = repository.observeHistory().first().single()
        assertEquals(url, row.url)
        assertEquals("", row.title)
        assertEquals("", row.authorName)
        assertEquals("", row.handle)
        assertEquals("keep", row.note)
        assertEquals(1_000L, row.starredAt)
    }

    @Test
    fun scrollRefreshPicksOneExpiredThumbAndBackupExcludesHistory() {
        val fresh = "https://cdn.example/a?x-expires=5000"
        val stale = "https://cdn.example/b?x-expires=10"
        val next = TikTokScrollRefresh.next(
            listOf(
                TikTokVisibleRow("1", videoUrl(), fresh),
                TikTokVisibleRow("2", videoUrl(), stale),
                TikTokVisibleRow("3", videoUrl(), null),
            ),
            nowEpochSeconds = 100,
        )
        assertEquals("2", next?.id)
        assertFalse(TikTokThumbs.expired(fresh, 100))
        assertTrue(TikTokThumbs.expired(null, 100))
        assertTrue(TikTokCopyRetention.ENFORCED)
        assertTrue(TikTokCopyRetention.stale(1L, 1L + TikTokCopyRetention.WINDOW_MILLIS))
        assertFalse(TikTokWebOrigins.CLEARS_ALL_WEBVIEW_STORAGE)
        assertTrue(TikTokWebOrigins.PAGES.any { it.contains("tiktok.com") })
        assertEquals("scout", TikTokCaption.handleFromAuthorUrl("https://www.tiktok.com/@scout"))

        val backup = File("src/main/res/xml/backup_rules.xml").readText()
        val transfer = File("src/main/res/xml/data_extraction_rules.xml").readText()
        val manifest = File("src/main/AndroidManifest.xml").readText()
        assertTrue(backup.contains("history.db"))
        assertTrue(transfer.contains("history.db"))
        assertTrue(transfer.contains("device-transfer"))
        assertTrue(manifest.contains("android:fullBackupContent"))
        assertTrue(manifest.contains("android:dataExtractionRules"))
    }

    private fun client(transport: TikTokTransport) = TikTokOEmbedClient(transport, clock = { 1_000L }, sleep = {})

    private fun memoryDisk() = app.pane.android.data.tiktok.MemoryTikTokOEmbedDisk()

    private fun page() = "https://www.tiktok.com/embed/v2/$ID"

    private fun videoUrl() = "https://www.tiktok.com/@scout/video/$ID"

    private fun tiktokView(url: String, at: Long) = HistoryView(
        url = url,
        source = "TikTok",
        title = "Caption",
        authorName = "Scout",
        handle = "scout",
        caption = "Caption",
        thumbUrl = "https://cdn.example/t?x-expires=1",
        pfpUrl = "https://cdn.example/p",
        mediaType = "video",
        viewedAtEpochMillis = at,
    )
}

private class RecordingTikTokTransport(private val code: Int = 200) : TikTokTransport {
    val urls = mutableListOf<String>()

    override fun exchange(url: String, method: String, followRedirects: Boolean): TikTokHttpResponse {
        urls += url
        val body = """{"title":"Played","author_name":"Scout","author_url":"https://www.tiktok.com/@scout","thumbnail_url":"https://cdn.example/t"}"""
        return TikTokHttpResponse(code, body, null)
    }
}

private object NoTikTokSiteData : TikTokSiteData {
    override fun clear() = Unit
}

private class MovingClock(var now: Long) : Clock {
    override fun nowEpochMillis(): Long = now
}

private class CountingStars : StarImageStore {
    var pins = 0
    override fun pin(canonicalUrl: String, thumbnail: ByteArray?, profile: ByteArray?): PinnedStarFiles {
        pins += 1
        return PinnedStarFiles("thumb", "profile", 1)
    }

    override fun unpin(thumbPath: String?, profilePath: String?) = Unit
}
