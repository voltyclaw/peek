package app.pane.android

import app.pane.android.data.history.FileStarImageStore
import app.pane.android.data.history.HistoryPreferenceCodec
import app.pane.android.data.history.HistoryPreferenceKeys
import app.pane.android.data.history.HistoryQueries
import app.pane.android.data.history.JdbcHistorySql
import app.pane.android.data.history.MemoryHistoryPreferences
import app.pane.android.data.history.SqliteHistoryRepository
import app.pane.android.domain.model.Clock
import app.pane.android.domain.model.HistoryFilterCatalog
import app.pane.android.domain.model.HistoryQuery
import app.pane.android.domain.model.HistoryScope
import app.pane.android.domain.model.HistoryView
import app.pane.android.domain.model.SourceApp
import app.pane.android.ui.history.HistoryPresenter
import app.pane.android.domain.model.StarCopy
import java.nio.file.Files
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryFilterTest {
    @Test
    fun starredAndAppsAndTogether() = runTest {
        val repository = repository()
        repository.recordSuccessfulView(view("https://x.com/i/status/1"))
        repository.recordSuccessfulView(view("https://x.com/i/status/2"))
        repository.recordSuccessfulView(view("https://www.reddit.com/r/pics/comments/abc123/title/"))
        repository.recordSuccessfulView(view("https://www.instagram.com/p/abc123/"))
        repository.star("https://x.com/i/status/1", StarCopy("Star", "Ada", "ada", "Star"))

        val starredX = repository.observeHistory(
            HistoryQuery(scope = HistoryScope.Starred, apps = setOf(SourceApp.X)),
        ).first()
        assertEquals(listOf("https://x.com/i/status/1"), starredX.map { it.url })

        val apps = repository.observeHistory(
            HistoryQuery(apps = setOf(SourceApp.X, SourceApp.Reddit)),
        ).first().map { it.sourceApp }.toSet()
        assertEquals(setOf(SourceApp.X, SourceApp.Reddit), apps)

        val all = repository.observeHistory(HistoryQuery()).first()
        assertEquals(4, all.size)

        val where = HistoryQueries.where(
            HistoryQuery(scope = HistoryScope.Starred, apps = setOf(SourceApp.X, SourceApp.Instagram)),
        )
        assertTrue(where.contains("starred_at IS NOT NULL"))
        assertTrue(where.contains("source_app IN"))
        assertTrue(where.contains("AND"))
    }

    @Test
    fun distinctAppsListsOnlyAppsThatArePresent() = runTest {
        val repository = repository()
        repository.recordSuccessfulView(view("https://x.com/i/status/1"))
        repository.recordSuccessfulView(view("https://www.instagram.com/reel/abc123/"))
        repository.recordSuccessfulView(view("https://example.com/story"))

        assertEquals(
            listOf(SourceApp.X, SourceApp.Instagram, SourceApp.Other),
            repository.distinctSourceApps(),
        )
    }

    @Test
    fun theLastFilterSelectionRoundTripsAndDefaultsToAll() {
        val prefs = MemoryHistoryPreferences()
        assertEquals(HistoryScope.All, prefs.readFilter().scope)
        assertTrue(prefs.readFilter().apps.isEmpty())
        assertEquals(HistoryPreferenceKeys.DEFAULT_SWIPE, prefs.readSwipeAction())

        val saved = HistoryQuery(scope = HistoryScope.Starred, apps = setOf(SourceApp.Reddit))
        prefs.writeFilter(saved)
        val again = MemoryHistoryPreferences()
        val encoded = HistoryPreferenceCodec.encode(saved)
        assertEquals(saved.scope, HistoryPreferenceCodec.decode(encoded.first, encoded.second).scope)
        assertEquals(saved.apps, HistoryPreferenceCodec.decode(encoded.first, encoded.second).apps)
        assertEquals(saved, prefs.readFilter())
        assertEquals(HistoryScope.All, again.readFilter().scope)
    }

    @Test
    fun legacyMultiAppPrefResetsToAllAndKeepsStarred() {
        val prefs = MemoryHistoryPreferences()
        prefs.seedLegacy(scope = "starred", apps = "X,Reddit,Instagram")
        val migrated = prefs.readFilter()
        assertEquals(HistoryScope.Starred, migrated.scope)
        assertTrue(migrated.apps.isEmpty())
        assertEquals(migrated, prefs.readFilter())

        prefs.writeFilter(HistoryQuery(scope = HistoryScope.Starred, apps = setOf(SourceApp.X, SourceApp.Reddit)))
        assertEquals(setOf(SourceApp.X), prefs.readFilter().apps)
    }

    @Test
    fun filterTilesStayInStudioOrderAndZeroCountsStayPut() = runTest {
        val repository = repository()
        repository.recordSuccessfulView(view("https://x.com/i/status/1"))
        repository.recordSuccessfulView(view("https://x.com/i/status/2"))
        repository.recordSuccessfulView(view("https://www.reddit.com/r/pics/comments/abc123/title/"))
        repository.star("https://x.com/i/status/1", StarCopy("Star", "Ada", "ada", "Star"))
        val rows = repository.observeHistory(HistoryQuery()).first()

        val all = HistoryPresenter.present(rows, HistoryQuery(), now = 5L, zone = java.time.ZoneId.of("UTC"))
        assertEquals(HistoryFilterCatalog.order, all.appCounts.map { it.app })
        assertEquals(2, all.appCounts.first { it.app == SourceApp.X }.count)
        assertEquals(0, all.appCounts.first { it.app == SourceApp.Facebook }.count)
        assertEquals(0, all.appCounts.first { it.app == SourceApp.Other }.count)
        assertEquals(false, all.hairline)

        val starred = HistoryPresenter.present(
            rows,
            HistoryQuery(scope = HistoryScope.Starred),
            now = 5L,
            zone = java.time.ZoneId.of("UTC"),
        )
        assertEquals(HistoryFilterCatalog.order, starred.appCounts.map { it.app })
        assertEquals(1, starred.appCounts.first { it.app == SourceApp.X }.count)
        assertEquals(0, starred.appCounts.first { it.app == SourceApp.Reddit }.count)
        assertEquals(true, starred.hairline)
        assertEquals(1, starred.scopeCount)
    }

    private fun repository() = SqliteHistoryRepository(
        sql = JdbcHistorySql.open(),
        imageStore = FileStarImageStore(Files.createTempDirectory("pane-stars").toFile(), BudgetCompressor()),
        clock = Clock { 5L },
    )

    private fun view(url: String) = HistoryView(
        url = url,
        source = "",
        title = "Post",
        authorName = "Ada",
        handle = "ada",
        caption = "Post",
        thumbUrl = null,
        pfpUrl = null,
        mediaType = "post",
        viewedAtEpochMillis = 1,
    )
}
