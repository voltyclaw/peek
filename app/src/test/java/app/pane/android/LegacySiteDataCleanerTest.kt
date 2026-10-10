package app.pane.android

import app.pane.android.domain.tiktok.TikTokWebOrigins
import app.pane.android.data.webview.EmbedWebViewLiveness
import app.pane.android.data.webview.FilePendingOriginStore
import app.pane.android.data.webview.LegacyClearResult
import app.pane.android.data.webview.LegacySiteDataCleaner
import app.pane.android.data.webview.PendingOriginStore
import app.pane.android.data.webview.expectedIndexedDbNames
import app.pane.android.data.webview.indexedDbEntryBelongsToHost
import app.pane.android.data.webview.originHost
import app.pane.android.data.youtube.YouTubeWebOrigins
import java.io.File
import java.nio.file.Files
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class LegacySiteDataCleanerTest {
    private lateinit var root: File

    @Before
    fun setUp() {
        root = Files.createTempDirectory("pane-idb").toFile()
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    @Test
    fun deleteOriginRunsBeforeCacheAndOnlyTheSourceFoldersGo() {
        val level = indexed("https_www.youtube.com_0.indexeddb.leveldb")
        val blob = indexed("https_www.youtube.com_0.indexeddb.blob")
        File(level, "CURRENT").writeText("x")
        val tiktok = indexed("https_www.tiktok.com_0.indexeddb.leveldb")
        val events = mutableListOf<String>()
        val cleaner = cleaner(
            events = events,
            live = { 0 },
            onCache = { assertTrue(level.exists()) },
        )

        val result = cleaner.clear(YouTubeWebOrigins.PAGES)

        assertEquals(LegacyClearResult.FilesCleared, result)
        assertEquals(YouTubeWebOrigins.PAGES.map { "origin:$it" } + "cache", events)
        assertFalse(level.exists())
        assertFalse(blob.exists())
        assertTrue(tiktok.exists())
    }

    @Test
    fun unexpectedPortLayoutDeletesNothing() {
        val expected = indexed("https_www.youtube.com_0.indexeddb.leveldb")
        val strayPort = indexed("https_www.youtube.com_443.indexeddb.leveldb")
        val tiktok = indexed("https_www.tiktok.com_0.indexeddb.leveldb")

        val result = cleaner(live = { 0 }).clear(listOf("https://www.youtube.com"))

        assertEquals(LegacyClearResult.UnexpectedLayout, result)
        assertTrue(expected.exists())
        assertTrue(strayPort.exists())
        assertTrue(tiktok.exists())
    }

    @Test
    fun aStrayIndexedDbDirectoryIsUnexpected() {
        val stray = File(root, "IndexedDB/https_www.youtube.com_0.indexeddb.leveldb").apply { mkdirs() }

        val result = cleaner(live = { 0 }).clear(listOf("https://www.youtube.com"))

        assertEquals(LegacyClearResult.UnexpectedLayout, result)
        assertTrue(stray.exists())
    }

    @Test
    fun aLiveWebViewDefersFileDeletionUntilTheNextIdlePass() {
        var live = 1
        var caches = 0
        var originSpikes = 0
        val level = indexed("https_www.youtube.com_0.indexeddb.leveldb")
        val pending = MemoryPending()
        val cleaner = cleaner(
            live = { live },
            pending = pending,
            onOrigin = { originSpikes += 1 },
            onCache = { caches += 1 },
        )

        assertEquals(LegacyClearResult.Deferred, cleaner.clear(listOf("https://www.youtube.com")))
        assertTrue(level.exists())
        assertEquals(setOf("https://www.youtube.com"), pending.read())
        assertEquals(1, originSpikes)
        assertEquals(1, caches)

        assertEquals(LegacyClearResult.Deferred, cleaner.resumeDeferred())
        assertTrue(level.exists())
        assertEquals(1, caches)
        assertEquals(1, originSpikes)

        live = 0
        assertEquals(LegacyClearResult.FilesCleared, cleaner.resumeDeferred())
        assertFalse(level.exists())
        assertTrue(pending.read().isEmpty())
        assertEquals(1, caches)
        assertEquals(1, originSpikes)
    }

    @Test
    fun deferredOriginsSurviveANewCleanerAndLeaveTheOtherSource() {
        val file = File(root, "pending.txt")
        val youtube = indexed("https_www.youtube.com_0.indexeddb.leveldb")
        val tiktok = indexed("https_www.tiktok.com_0.indexeddb.leveldb")
        val reddit = indexed("https_www.reddit.com_0.indexeddb.leveldb")
        var live = 1
        val first = cleaner(live = { live }, pending = FilePendingOriginStore(file))
        first.clear(listOf("https://www.youtube.com"))
        live = 0
        first.clear(TikTokWebOrigins.PAGES)

        val second = cleaner(live = { 0 }, pending = FilePendingOriginStore(file))
        assertEquals(LegacyClearResult.FilesCleared, second.resumeDeferred())

        assertFalse(youtube.exists())
        assertFalse(tiktok.exists())
        assertTrue(reddit.exists())
        assertFalse(file.exists())
    }

    @Test
    fun anUnexpectedLayoutOnResumeDropsTheQueueWithoutDeleting() {
        val level = indexed("https_www.youtube.com_0.indexeddb.leveldb")
        indexed("https_www.youtube.com_443.indexeddb.leveldb")
        val pending = MemoryPending()
        var live = 1
        val cleaner = cleaner(live = { live }, pending = pending)
        cleaner.clear(listOf("https://www.youtube.com"))
        live = 0

        assertEquals(LegacyClearResult.UnexpectedLayout, cleaner.resumeDeferred())
        assertTrue(level.exists())
        assertTrue(pending.read().isEmpty())
    }

    @Test
    fun aBareHostDoesNotTakeTheWwwFolders() {
        val apex = indexed("https_youtube.com_0.indexeddb.leveldb")
        val www = indexed("https_www.youtube.com_0.indexeddb.leveldb")

        assertEquals(LegacyClearResult.FilesCleared, cleaner(live = { 0 }).clear(listOf("https://youtube.com")))

        assertFalse(apex.exists())
        assertTrue(www.exists())
    }

    @Test
    fun missingIndexedDbIsACleanResult() {
        assertEquals(
            LegacyClearResult.FilesCleared,
            cleaner(live = { 0 }).clear(listOf("https://www.youtube.com")),
        )
    }

    @Test
    fun youtubeAndTikTokIndexedDbNamesDoNotOverlap() {
        val youtube = YouTubeWebOrigins.PAGES.mapNotNull(::originHost).toSet()
        val tiktok = TikTokWebOrigins.PAGES.mapNotNull(::originHost).toSet()
        assertTrue(youtube.intersect(tiktok).isEmpty())
        youtube.forEach { host ->
            expectedIndexedDbNames(host).forEach { name ->
                assertTrue(indexedDbEntryBelongsToHost(name, host))
                tiktok.forEach { other -> assertFalse(indexedDbEntryBelongsToHost(name, other)) }
            }
        }
    }

    @Test
    fun liveCountDropsOnlyAfterRelease() {
        val before = EmbedWebViewLiveness.liveCount()
        EmbedWebViewLiveness.acquire()
        assertEquals(before + 1, EmbedWebViewLiveness.liveCount())
        EmbedWebViewLiveness.release()
        assertEquals(before, EmbedWebViewLiveness.liveCount())
    }

    @Test
    fun surfacesDestroyTheWebViewBeforeTheyReleaseTheLiveCount() {
        val youtube = source("src/main/java/app/pane/android/ui/youtube/YouTubeSurface.kt")
        val youtubeRelease = youtube.substringAfter("onRelease")
        assertTrue(youtubeRelease.indexOf("view.destroy()") < youtubeRelease.indexOf("EmbedWebViewLiveness.release()"))
        val tiktok = source("src/main/java/app/pane/android/ui/tiktok/TikTokSurface.kt")
        val tiktokDispose = tiktok.substringAfter("onDispose")
        assertTrue(tiktokDispose.indexOf("destroy()") < tiktokDispose.indexOf("EmbedWebViewLiveness.release()"))
    }

    @Test
    fun withdrawPathsDoNotCallBannedStorageApisAndCacheClearStaysInTheCleaner() {
        val banned = Regex("""\.(deleteAllData|removeAllCookies)\s*\(""")
        val cache = Regex("""\.clearCache\s*\(\s*true\s*\)""")
        val main = sourceRoot().resolve("src/main/java")
        val kotlin = main.walk().filter { it.extension == "kt" }.toList()
        kotlin.forEach { file -> assertFalse(file.path, banned.containsMatchIn(file.readText())) }
        val cacheHits = kotlin.filter { cache.containsMatchIn(it.readText()) }
        assertEquals(listOf("LegacySiteDataCleaner.kt"), cacheHits.map { it.name })
    }

    private fun indexed(name: String): File =
        File(root, "Default/IndexedDB/$name").apply { mkdirs() }

    private fun cleaner(
        events: MutableList<String> = mutableListOf(),
        live: () -> Int,
        pending: PendingOriginStore = MemoryPending(),
        onOrigin: () -> Unit = {},
        onCache: () -> Unit = {},
    ): LegacySiteDataCleaner = LegacySiteDataCleaner(
        webViewRoot = root,
        pending = pending,
        liveWebViews = live,
        deleteOrigin = { origin ->
            onOrigin()
            events += "origin:$origin"
        },
        clearHttpCache = {
            onCache()
            events += "cache"
        },
    )

    private class MemoryPending : PendingOriginStore {
        private var origins: Set<String> = emptySet()
        override fun read(): Set<String> = origins
        override fun write(origins: Set<String>) {
            this.origins = origins
        }
    }

    private fun source(relative: String): String = sourceFile(relative).readText()

    private fun sourceFile(relative: String): File {
        val direct = sourceRoot().resolve(relative)
        if (direct.isFile) return direct
        return File(relative)
    }

    private fun sourceRoot(): File {
        val app = File("src/main")
        if (app.isDirectory) return File(".")
        val nested = File("app/src/main")
        if (nested.isDirectory) return File("app")
        return File(".")
    }
}
