package app.pane.android

import app.pane.android.data.history.FileStarImageStore
import app.pane.android.testing.BudgetCompressor
import app.pane.android.data.history.HistorySchema
import app.pane.android.data.history.JdbcHistorySql
import app.pane.android.data.history.SqliteHistoryRepository
import app.pane.android.domain.model.Clock
import app.pane.android.domain.model.SourceApp
import app.pane.android.domain.model.HistoryView
import app.pane.android.domain.model.RecentLink
import java.nio.file.Files
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryMigrationTest {
    @Test
    fun versionOneCreatesHistoryStarsTagsAndFts() {
        val sql = JdbcHistorySql.open()
        HistorySchema.apply(sql)

        val names = sql.query("SELECT name, type FROM sqlite_master").map { row ->
            row.text("name").orEmpty() to row.text("type").orEmpty()
        }.toMap()

        assertEquals(2, HistorySchema.VERSION)
        assertEquals("table", names["history"])
        assertEquals("table", names["tag"])
        assertEquals("table", names["history_tag"])
        assertEquals("table", names["history_fts"])
        assertEquals("index", names["index_history_last_viewed"])
        assertEquals("index", names["index_history_starred_at"])
        assertEquals("index", names["index_history_prune"])
        assertEquals("index", names["index_history_source_app"])
        assertEquals("index", names["index_history_source_starred_viewed"])
        assertEquals("index", names["index_history_tag_tag_id"])
        assertEquals("trigger", names["history_ai"])
        assertEquals("trigger", names["history_au"])
        assertEquals("trigger", names["history_bd"])
        assertEquals("trigger", names["history_bu"])

        val columns = sql.query("PRAGMA table_info(history)").map { it.text("name") }
        listOf(
            "url", "source", "source_app", "title", "author_name", "handle", "caption",
            "thumb_url", "pfp_url", "pinned_thumb_path", "pinned_pfp_path",
            "media_type", "note", "first_viewed_at", "last_viewed_at", "view_count", "starred_at",
            "cache_fetched_at",
        ).forEach { column -> assertTrue(column, columns.contains(column)) }
    }

    @Test
    fun seedingFromRecentsIsIdempotentAndLeavesLaterViewsAlone() = runTest {
        val sql = JdbcHistorySql.open()
        val repository = SqliteHistoryRepository(
            sql = sql,
            imageStore = FileStarImageStore(Files.createTempDirectory("pane-stars").toFile(), BudgetCompressor()),
            clock = Clock { 1L },
        )
        val recents = listOf(
            RecentLink("https://twitter.com/ada/status/20", openedAtEpochMillis = 10),
            RecentLink("https://x.com/i/status/20", openedAtEpochMillis = 30),
            RecentLink("https://www.reddit.com/r/pics/comments/abc123/title/", openedAtEpochMillis = 15),
            RecentLink("https://x.com/jack", openedAtEpochMillis = 40),
        )

        repository.seedFromRecents(recents)
        repository.seedFromRecents(recents)

        val seeded = repository.observeHistory().first().associateBy { it.url }
        assertEquals(setOf("https://x.com/i/status/20", "https://www.reddit.com/comments/abc123/"), seeded.keys)
        assertEquals(SourceApp.X, seeded.getValue("https://x.com/i/status/20").sourceApp)
        assertEquals(SourceApp.Reddit, seeded.getValue("https://www.reddit.com/comments/abc123/").sourceApp)
        assertEquals(1, seeded.getValue("https://x.com/i/status/20").viewCount)
        assertEquals(30L, seeded.getValue("https://x.com/i/status/20").lastViewedAt)
        assertNull(seeded.getValue("https://x.com/i/status/20").starredAt)

        repository.recordSuccessfulView(
            HistoryView(
                url = "https://x.com/i/status/20",
                source = "X",
                title = "Opened",
                authorName = "Ada",
                handle = "ada",
                caption = "Opened",
                thumbUrl = null,
                pfpUrl = null,
                mediaType = "post",
                viewedAtEpochMillis = 90,
            ),
        )
        repository.seedFromRecents(recents)

        val after = repository.observeHistory().first().first { it.url == "https://x.com/i/status/20" }
        assertEquals(2, after.viewCount)
        assertEquals(90L, after.lastViewedAt)
        assertEquals("Opened", after.title)

        val hits = sql.query(
            "SELECT docid FROM history_fts WHERE history_fts MATCH ?",
            listOf("Opened"),
        )
        assertEquals(1, hits.size)
    }
}
