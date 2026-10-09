package app.pane.android

import app.pane.android.data.history.FileStarImageStore
import app.pane.android.data.history.JdbcHistorySql
import app.pane.android.data.history.SqliteHistoryRepository
import app.pane.android.data.history.StarImageBudget
import app.pane.android.data.history.StarImageCompressor
import app.pane.android.domain.model.Clock
import app.pane.android.domain.model.HistoryView
import app.pane.android.domain.model.RecentLink
import app.pane.android.domain.model.StarCopy
import app.pane.android.domain.model.StarImageBytes
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryStoreTest {
    @Test
    fun repeatViewsShareOneRowAndIncrementTheCount() = runTest {
        val repository = repository()
        repository.recordSuccessfulView(view("https://twitter.com/ada/status/20", at = 10, title = "First"))
        repository.recordSuccessfulView(view("https://x.com/i/status/20", at = 25, title = "Second"))

        val row = repository.observeHistory().first().single()
        assertEquals("https://x.com/i/status/20", row.url)
        assertEquals(2, row.viewCount)
        assertEquals(10L, row.firstViewedAt)
        assertEquals(25L, row.lastViewedAt)
        assertEquals("Second", row.title)
        assertEquals("ada", row.handle)
    }

    @Test
    fun aProfileUrlAndAMentionHandoffAreNotStored() = runTest {
        val repository = repository()
        repository.recordSuccessfulView(view("https://x.com/jack", at = 1))
        repository.recordSuccessfulView(view("https://www.instagram.com/nasa/", at = 2))

        assertTrue(repository.observeHistory().first().isEmpty())
    }

    @Test
    fun starSavesTheCopyAndUnstarKeepsTheRow() = runTest {
        val root = tempRoot()
        val repository = repository(root, BudgetCompressor())
        repository.recordSuccessfulView(view("https://x.com/i/status/20", at = 10, title = "Live"))
        repository.star(
            url = "https://twitter.com/ada/status/20",
            copy = StarCopy(
                title = "Saved title",
                authorName = "Ada Lovelace",
                handle = "@ada",
                caption = "Saved caption",
                thumbUrl = "https://cdn.example/thumb.jpg",
                pfpUrl = "https://cdn.example/ada.jpg",
            ),
            images = StarImageBytes(
                thumbnail = ByteArray(80 * 1024) { 1 },
                profile = ByteArray(40 * 1024) { 2 },
            ),
        )

        val starred = repository.observeHistory().first().single()
        assertEquals("Saved title", starred.title)
        assertEquals("Ada Lovelace", starred.authorName)
        assertEquals("ada", starred.handle)
        assertEquals("Saved caption", starred.caption)
        assertEquals("https://cdn.example/thumb.jpg", starred.thumbUrl)
        assertEquals("https://cdn.example/ada.jpg", starred.pfpUrl)
        assertEquals(50L, starred.starredAt)
        assertEquals(1, starred.viewCount)
        val thumb = File(root, starred.pinnedThumbPath!!)
        val profile = File(root, starred.pinnedPfpPath!!)
        assertTrue(thumb.isFile && profile.isFile)
        assertTrue(thumb.length() <= 45 * 1024)
        assertTrue(profile.length() <= 15 * 1024)
        assertTrue(thumb.length() + profile.length() <= StarImageBudget.MAX_TOTAL_BYTES)

        repository.recordSuccessfulView(view("https://x.com/i/status/20", at = 40, title = "Seen again"))
        repository.unstar("https://x.com/i/status/20")

        val kept = repository.observeHistory().first().single()
        assertNull(kept.starredAt)
        assertNull(kept.pinnedThumbPath)
        assertNull(kept.pinnedPfpPath)
        assertEquals(2, kept.viewCount)
        assertEquals(10L, kept.firstViewedAt)
        assertEquals("Seen again", kept.title)
        assertTrue(!thumb.exists() && !profile.exists())
    }

    @Test
    fun imagesOverTheCapAreDroppedAndTheStarStillSavesText() = runTest {
        val root = tempRoot()
        val repository = repository(root, UncappedCompressor())
        repository.star(
            url = "https://x.com/i/status/99",
            copy = StarCopy(title = "Text only", authorName = "Ada", handle = "ada", caption = "Caption"),
            images = StarImageBytes(
                thumbnail = ByteArray(100 * 1024),
                profile = ByteArray(100 * 1024),
            ),
        )

        val row = repository.observeHistory().first().single()
        assertEquals("Text only", row.title)
        assertEquals(50L, row.starredAt)
        assertNull(row.pinnedThumbPath)
        assertNull(row.pinnedPfpPath)
        assertTrue(root.walkTopDown().none { it.isFile })
    }

    @Test
    fun captionAndTitleStopAtTwoThousandCharacters() = runTest {
        val repository = repository()
        val long = "a".repeat(2_500)
        repository.recordSuccessfulView(view("https://x.com/i/status/20", at = 1, title = long))
        val row = repository.observeHistory().first().single()
        assertEquals(2_000, row.title.length)
        assertEquals(2_000, row.caption.length)
    }

    @Test
    fun imageBudgetSplitsSixtyKilobytesAcrossThumbAndProfile() {
        assertEquals(45 * 1024 to 15 * 1024, StarImageBudget.allowances(hasThumb = true, hasProfile = true))
        assertEquals(StarImageBudget.MAX_TOTAL_BYTES to 0, StarImageBudget.allowances(hasThumb = true, hasProfile = false))
        assertEquals(60 * 1024, StarImageBudget.MAX_TOTAL_BYTES)
    }

    private fun repository(
        root: File = tempRoot(),
        compressor: StarImageCompressor = BudgetCompressor(),
    ) = SqliteHistoryRepository(
        sql = JdbcHistorySql.open(),
        imageStore = FileStarImageStore(root, compressor),
        clock = Clock { 50L },
    )

    private fun tempRoot(): File = Files.createTempDirectory("pane-stars").toFile()

    private fun view(url: String, at: Long, title: String = "Hello") = HistoryView(
        url = url,
        source = "X",
        title = title,
        authorName = "Ada Lovelace",
        handle = "@ada",
        caption = title,
        thumbUrl = "https://cdn.example/thumb.jpg",
        pfpUrl = "https://cdn.example/ada.jpg",
        mediaType = "image",
        viewedAtEpochMillis = at,
    )
}

internal class BudgetCompressor : StarImageCompressor {
    override fun compress(source: ByteArray, maxBytes: Int): ByteArray = when {
        source.isEmpty() -> ByteArray(0)
        source.size <= maxBytes -> source
        else -> ByteArray(maxBytes) { 7 }
    }
}

private class UncappedCompressor : StarImageCompressor {
    override fun compress(source: ByteArray, maxBytes: Int): ByteArray = source
}
