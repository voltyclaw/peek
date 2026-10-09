package app.pane.android

import app.pane.android.data.history.FileStarImageStore
import app.pane.android.data.history.JdbcHistorySql
import app.pane.android.data.history.SqliteHistoryRepository
import app.pane.android.domain.model.Clock
import app.pane.android.domain.model.HistoryRowAction
import app.pane.android.domain.model.HistoryRowActions
import app.pane.android.domain.model.HistorySwipeEffect
import app.pane.android.domain.model.HistorySwipeResolver
import app.pane.android.domain.model.HistoryView
import app.pane.android.domain.model.RecentLink
import app.pane.android.domain.repository.RecentLinksRepository
import app.pane.android.domain.model.StarCopy
import app.pane.android.domain.model.StarImageBytes
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HistorySwipeTest {
    @Test
    fun aStarredSwipeUnstarsAndTheNextSwipeRemoves() = runTest {
        val root = Files.createTempDirectory("pane-stars").toFile()
        val repository = repository(root)
        repository.recordSuccessfulView(view("https://x.com/i/status/20", at = 10))
        repository.star(
            "https://x.com/i/status/20",
            StarCopy("Saved", "Ada Lovelace", "@ada", "Saved caption", "https://cdn.example/t.jpg", "https://cdn.example/a.jpg"),
            StarImageBytes(ByteArray(32) { 3 }, ByteArray(16) { 4 }),
        )
        val starred = repository.observeHistory().first().single()
        assertEquals(HistorySwipeEffect.Unstar, HistorySwipeResolver.effect(starred.starredAt != null))

        val unstarUndo = repository.applySwipe("https://x.com/i/status/20")!!
        val unstarred = repository.observeHistory().first().single()
        assertNull(unstarred.starredAt)
        assertEquals(1, unstarred.viewCount)
        assertEquals(10L, unstarred.firstViewedAt)
        assertTrue(!File(root, starred.pinnedThumbPath!!).exists())

        repository.undo(unstarUndo)
        val restored = repository.observeHistory().first().single()
        assertEquals(starred.starredAt, restored.starredAt)
        assertEquals("Saved", restored.title)
        assertEquals("Ada Lovelace", restored.authorName)
        assertEquals("ada", restored.handle)
        assertEquals("Saved caption", restored.caption)
        assertEquals(starred.firstViewedAt, restored.firstViewedAt)
        assertEquals(starred.lastViewedAt, restored.lastViewedAt)
        assertEquals(starred.viewCount, restored.viewCount)
        assertEquals(32, File(root, restored.pinnedThumbPath!!).length())
        assertEquals(16, File(root, restored.pinnedPfpPath!!).length())

        repository.applySwipe("https://x.com/i/status/20")
        val removeUndo = repository.applySwipe("https://twitter.com/ada/status/20")!!
        assertTrue(repository.observeHistory().first().isEmpty())
        repository.undo(removeUndo)
        val back = repository.observeHistory().first().single()
        assertNull(back.starredAt)
        assertEquals(restored.viewCount, back.viewCount)
        assertEquals(restored.firstViewedAt, back.firstViewedAt)
        assertEquals("Saved caption", back.caption)
    }

    @Test
    fun menuRemoveOnAStarredRowDeletesTheStarAndUndoRestoresBoth() = runTest {
        val root = Files.createTempDirectory("pane-stars").toFile()
        val repository = repository(root)
        val url = "https://x.com/i/status/20"
        repository.recordSuccessfulView(view(url, at = 10))
        repository.star(
            url,
            StarCopy("Saved", "Ada Lovelace", "@ada", "Saved caption", "https://cdn.example/t.jpg", "https://cdn.example/a.jpg"),
            StarImageBytes(ByteArray(32) { 3 }, ByteArray(16) { 4 }),
        )
        val starred = repository.observeHistory().first().single()
        assertTrue(starred.starredAt != null)

        val undo = repository.remove(url)!!
        assertTrue(repository.observeHistory().first().isEmpty())

        repository.undo(undo)
        val restored = repository.observeHistory().first().single()
        assertEquals(starred.starredAt, restored.starredAt)
        assertEquals("Saved", restored.title)
        assertEquals("Ada Lovelace", restored.authorName)
        assertEquals("ada", restored.handle)
        assertEquals("Saved caption", restored.caption)
        assertEquals(32, File(root, restored.pinnedThumbPath!!).length())
        assertEquals(16, File(root, restored.pinnedPfpPath!!).length())
    }

    @Test
    fun historyRemoveAndRecentsRemoveDoNotCascade() = runTest {
        val repository = repository(Files.createTempDirectory("pane-stars").toFile())
        val recents = MemoryRecents()
        val url = "https://x.com/i/status/20"
        repository.recordSuccessfulView(view(url, at = 4))
        recents.markOpened(url)

        repository.remove(url)
        assertEquals(url, recents.observeRecents().first().single().url)
        assertTrue(repository.observeHistory().first().isEmpty())

        repository.recordSuccessfulView(view(url, at = 8))
        recents.remove(url)
        assertTrue(recents.observeRecents().first().isEmpty())
        assertEquals(url, repository.observeHistory().first().single().url)
    }

    @Test
    fun longPressAndTalkBackLabelsArePlainStrings() {
        assertEquals(HistorySwipeEffect.Remove, HistorySwipeResolver.effect(starred = false))
        assertEquals(
            listOf("Remove star", "Remove from History", "Share", "Copy link", "Note & tags"),
            HistoryRowActions.longPress(starred = true).map(HistoryRowAction::label),
        )
        assertEquals(
            listOf("Star", "Remove from History", "Share", "Copy link", "Note & tags"),
            HistoryRowActions.longPress(starred = false).map(HistoryRowAction::label),
        )
        assertEquals(
            listOf("Star", "Remove from History"),
            HistoryRowActions.talkBack(starred = false).map(HistoryRowAction::label),
        )
        assertEquals(
            listOf("Remove star", "Remove from History"),
            HistoryRowActions.talkBack(starred = true).map(HistoryRowAction::label),
        )
    }

    private fun repository(root: File) = SqliteHistoryRepository(
        sql = JdbcHistorySql.open(),
        imageStore = FileStarImageStore(root, BudgetCompressor()),
        clock = Clock { 70L },
    )

    private fun view(url: String, at: Long) = HistoryView(
        url = url,
        source = "X",
        title = "Live",
        authorName = "Ada",
        handle = "ada",
        caption = "Live",
        thumbUrl = null,
        pfpUrl = null,
        mediaType = "post",
        viewedAtEpochMillis = at,
    )
}

private class MemoryRecents : RecentLinksRepository {
    private val items = MutableStateFlow<List<RecentLink>>(emptyList())
    override fun observeRecents(): Flow<List<RecentLink>> = items
    override suspend fun markOpened(url: String) {
        items.value = listOf(RecentLink(url, 1))
    }
    override suspend fun remove(url: String) {
        items.value = items.value.filterNot { it.url == url }
    }
    override suspend fun clear() {
        items.value = emptyList()
    }
}
