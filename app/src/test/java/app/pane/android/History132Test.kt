package app.pane.android

import app.pane.android.data.history.FileStarImageStore
import app.pane.android.data.history.JdbcHistorySql
import app.pane.android.data.history.SqliteHistoryRepository
import app.pane.android.domain.model.Author
import app.pane.android.domain.model.Clock
import app.pane.android.domain.model.ExternalPostMetadata
import app.pane.android.domain.model.HistoryEntry
import app.pane.android.domain.model.HistoryLedger
import app.pane.android.domain.model.HistoryQuery
import app.pane.android.domain.model.LinkContent
import app.pane.android.domain.model.LinkKind
import app.pane.android.domain.model.LinkSource
import app.pane.android.domain.model.Media
import app.pane.android.domain.model.MediaLocation
import app.pane.android.domain.model.RowTitles
import app.pane.android.domain.model.SourceApp
import app.pane.android.domain.model.StoredTextRepair
import app.pane.android.domain.text.UnicodeEscapes
import app.pane.android.domain.text.firstGrapheme
import app.pane.android.ui.history.HistoryPresenter
import java.io.File
import java.nio.file.Files
import java.time.ZoneOffset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class History132Test {
    @Test
    fun unicodeEscapesDecodeBackslashBareAndSurrogatePairs() {
        assertEquals("ק", UnicodeEscapes.decode("\\u05e7"))
        assertEquals("&", UnicodeEscapes.decode("\\u0026"))
        assertEquals("ק", UnicodeEscapes.decode("\\\\u05e7"))
        assertEquals("קונה", UnicodeEscapes.decode("u05e7u05d5u05e0u05d4"))
        assertEquals("u0041", UnicodeEscapes.decode("u0041"))
        assertEquals("au05e7", UnicodeEscapes.decode("au05e7"))
        val emoji = UnicodeEscapes.decode("uD83DuDE00")
        assertEquals(1, emoji.codePointCount(0, emoji.length))
        assertEquals(0x1F600, emoji.codePointAt(0))
        assertEquals(emoji, UnicodeEscapes.decode("\\uD83D\\uDE00"))
        assertEquals("קונה", UnicodeEscapes.decode(UnicodeEscapes.decode("u05e7u05d5u05e0u05d4")))
    }

    @Test
    fun storedHistoryRewritesEscapesAndDomainOnlyXTitlesOnce() = runTest {
        val sql = JdbcHistorySql.open()
        sql.exec(
            """
            INSERT INTO history (
              url, source, source_app, title, author_name, handle, caption, thumb_url, pfp_url,
              pinned_thumb_path, pinned_pfp_path, media_type, note,
              first_viewed_at, last_viewed_at, view_count, starred_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            listOf(
                "https://www.facebook.com/page/posts/1",
                "Facebook",
                "Facebook",
                "u05e7u05d5u05e0u05d4",
                "u05e7u05d5u05e0u05d4",
                "",
                "u05e7u05d5u05e0u05d4",
                null,
                null,
                null,
                null,
                "text",
                null,
                1L,
                1L,
                1,
                null,
            ),
        )
        sql.exec(
            """
            INSERT INTO history (
              url, source, source_app, title, author_name, handle, caption, thumb_url, pfp_url,
              pinned_thumb_path, pinned_pfp_path, media_type, note,
              first_viewed_at, last_viewed_at, view_count, starred_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            listOf(
                "https://x.com/ada/status/20",
                "X",
                "X",
                "x.com",
                "Ada",
                "ada",
                "",
                null,
                null,
                null,
                null,
                "text",
                null,
                2L,
                2L,
                1,
                null,
            ),
        )
        val root = Files.createTempDirectory("pane-132").toFile()
        val repository = SqliteHistoryRepository(
            sql = sql,
            imageStore = FileStarImageStore(root, BudgetCompressor()),
            clock = Clock { 50L },
        )
        val rows = repository.observeHistory().first().associateBy { it.url }
        assertEquals("קונה", rows.getValue("https://www.facebook.com/page/posts/1").title)
        assertEquals("קונה", rows.getValue("https://www.facebook.com/page/posts/1").authorName)
        assertEquals("Post by @ada", rows.getValue("https://x.com/ada/status/20").title)

        val again = SqliteHistoryRepository(
            sql = sql,
            imageStore = FileStarImageStore(File(root, "again"), BudgetCompressor()),
            clock = Clock { 50L },
        )
        val second = again.observeHistory().first().associateBy { it.url }
        assertEquals("קונה", second.getValue("https://www.facebook.com/page/posts/1").title)
        assertEquals("Post by @ada", second.getValue("https://x.com/ada/status/20").title)
    }

    @Test
    fun xTitlesPreferTextThenCardThenHandleThenDomain() {
        val url = "https://x.com/ada/status/20"
        assertEquals("hello", RowTitles.xTitle("hello", "x.com", url, "card", "ada"))
        assertEquals("card", RowTitles.xTitle("https://t.co/abc", "x.com", url, "card", "ada"))
        assertEquals("Post by @ada", RowTitles.xTitle("x.com", "", url, "", "ada"))
        assertEquals("x.com", RowTitles.xTitle("x.com", "", url, "", ""))
        assertEquals("kept", RowTitles.display("kept", "", url))

        val entry = historyEntry(title = "x.com", caption = "on the timeline", handle = "ada")
        val row = HistoryPresenter.present(listOf(entry), HistoryQuery(), 1L, ZoneOffset.UTC)
            .sections.single().rows.single()
        assertEquals("on the timeline", row.title)

        val cached = StoredTextRepair.linkContent(
            LinkContent(
                url = url,
                title = "x.com",
                source = LinkSource.X,
                kind = LinkKind.Post,
                thumbnail = MediaLocation.Remote(""),
                media = Media(MediaLocation.Remote(""), "", badge = "TEXT"),
                author = Author("Ada", "@ada"),
                commentCount = 0,
                comments = emptyList(),
                sourceMetadata = ExternalPostMetadata(postId = "20", articleTitle = "A real card"),
            ),
        )
        assertEquals("A real card", cached?.title)
        assertNull(
            StoredTextRepair.linkContent(
                LinkContent(
                    url = url,
                    title = "A real card",
                    source = LinkSource.X,
                    kind = LinkKind.Post,
                    thumbnail = MediaLocation.Remote(""),
                    media = Media(MediaLocation.Remote(""), "", badge = "TEXT"),
                    author = Author("Ada", "@ada"),
                    commentCount = 0,
                    comments = emptyList(),
                    sourceMetadata = ExternalPostMetadata(postId = "20", articleTitle = "A real card"),
                ),
            ),
        )
    }

    @Test
    fun letterAvatarUsesTheFirstGrapheme() {
        assertEquals("ק", firstGrapheme("קונה"))
        assertEquals("A", firstGrapheme("@ada"))
        assertEquals("N", firstGrapheme("r/nasa"))
        assertEquals("", firstGrapheme("  "))
    }

    private fun historyEntry(title: String, caption: String, handle: String) = HistoryEntry(
        url = "https://x.com/ada/status/20",
        source = "X",
        sourceApp = SourceApp.X,
        title = title,
        authorName = "Ada",
        handle = handle,
        caption = caption,
        thumbUrl = null,
        pfpUrl = null,
        pinnedThumbPath = null,
        pinnedPfpPath = null,
        mediaType = HistoryLedger.NONE,
        note = null,
        firstViewedAt = 1L,
        lastViewedAt = 1L,
        viewCount = 1,
        starredAt = null,
    )
}
