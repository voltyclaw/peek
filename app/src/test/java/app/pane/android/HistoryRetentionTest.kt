package app.pane.android

import app.pane.android.data.history.FileStarImageStore
import app.pane.android.data.history.JdbcHistorySql
import app.pane.android.data.history.SqliteHistoryRepository
import app.pane.android.domain.model.Clock
import app.pane.android.domain.model.HistoryRetention
import app.pane.android.domain.model.HistoryRetentionCandidate
import app.pane.android.domain.model.HistoryView
import app.pane.android.domain.model.StarCopy
import java.nio.file.Files
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryRetentionTest {
    @Test
    fun theSelectorDropsOnlyTheOldestPlainRowsPastTheCap() {
        val rows = listOf(
            candidate("old-plain", at = 1),
            candidate("starred", at = 2, starred = true),
            candidate("noted", at = 3, hasNote = true),
            candidate("tagged", at = 4, hasTags = true),
            candidate("newer-plain", at = 5),
            candidate("newest-plain", at = 6),
        )

        assertEquals(listOf("old-plain"), HistoryRetention.urlsToPrune(rows, cap = 2))
        assertTrue(HistoryRetention.urlsToPrune(List(2_000) { candidate("row-$it", at = it.toLong()) }).isEmpty())
        assertEquals(
            listOf("row-0"),
            HistoryRetention.urlsToPrune(List(2_001) { candidate("row-$it", at = it.toLong()) }),
        )
    }

    @Test
    fun pruningStaysOffUntilTheFlagIsEnabled() = runTest {
        val repository = repository()
        repository.recordSuccessfulView(view("https://x.com/i/status/1", at = 1))
        repository.recordSuccessfulView(view("https://x.com/i/status/2", at = 2))
        repository.recordSuccessfulView(view("https://x.com/i/status/3", at = 3))

        assertTrue(repository.pruneUnstarred().isEmpty())
        assertEquals(3, repository.observeHistory().first().size)
        assertTrue(!HistoryRetention.ENFORCED)

        val removed = repository.pruneUnstarred(enabled = true, cap = 1)
        assertEquals(listOf("https://x.com/i/status/1", "https://x.com/i/status/2"), removed)
        assertEquals("https://x.com/i/status/3", repository.observeHistory().first().single().url)
    }

    @Test
    fun starredNotedAndTaggedRowsSurviveAnEnabledPrune() = runTest {
        val repository = repository()
        repository.recordSuccessfulView(view("https://x.com/i/status/1", at = 1))
        repository.recordSuccessfulView(view("https://x.com/i/status/2", at = 2))
        repository.recordSuccessfulView(view("https://x.com/i/status/3", at = 3))
        repository.recordSuccessfulView(view("https://x.com/i/status/4", at = 4))
        repository.star(
            "https://x.com/i/status/1",
            StarCopy(title = "Star", authorName = "Ada", handle = "ada", caption = "Star"),
        )
        repository.setNote("https://x.com/i/status/2", "keep this")
        repository.addTag("https://x.com/i/status/3", "Later")

        val removed = repository.pruneUnstarred(enabled = true, cap = 0)
        assertEquals(listOf("https://x.com/i/status/4"), removed)
        assertEquals(
            setOf("https://x.com/i/status/1", "https://x.com/i/status/2", "https://x.com/i/status/3"),
            repository.observeHistory().first().map { it.url }.toSet(),
        )
        assertEquals("keep this", repository.observeHistory().first().first { it.url.endsWith("2") }.note)
    }

    private fun repository() = SqliteHistoryRepository(
        sql = JdbcHistorySql.open(),
        imageStore = FileStarImageStore(Files.createTempDirectory("pane-stars").toFile(), BudgetCompressor()),
        clock = Clock { 80L },
    )

    private fun view(url: String, at: Long) = HistoryView(
        url = url,
        source = "X",
        title = "Post",
        authorName = "Ada",
        handle = "ada",
        caption = "Post",
        thumbUrl = null,
        pfpUrl = null,
        mediaType = "post",
        viewedAtEpochMillis = at,
    )

    private fun candidate(
        url: String,
        at: Long,
        starred: Boolean = false,
        hasNote: Boolean = false,
        hasTags: Boolean = false,
    ) = HistoryRetentionCandidate(url, at, starred, hasNote, hasTags)
}
