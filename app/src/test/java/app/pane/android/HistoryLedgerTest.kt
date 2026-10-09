package app.pane.android

import app.pane.android.domain.model.HistoryBucket
import app.pane.android.domain.model.HistoryEmptyKind
import app.pane.android.domain.model.HistoryEntry
import app.pane.android.domain.model.HistoryGesture
import app.pane.android.domain.model.HistoryLedger
import app.pane.android.domain.model.HistoryQuery
import app.pane.android.domain.model.HistoryScope
import app.pane.android.domain.model.SourceApp
import app.pane.android.ui.history.HistoryPresenter
import java.time.Instant
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryLedgerTest {
    private val zone = ZoneOffset.UTC
    private val now = Instant.parse("2026-10-09T12:00:00Z").toEpochMilli()

    @Test
    fun starredGroupsByStarredAtAndAllGroupsByLastViewedAt() {
        val viewedTodayStarredEarlier = entry(
            url = "https://x.com/i/status/1",
            viewed = now - ChronoUnit.HOURS.duration.toMillis() * 2,
            starred = now - ChronoUnit.DAYS.duration.toMillis() * 3,
        )
        val viewedEarlierStarredToday = entry(
            url = "https://x.com/i/status/2",
            viewed = now - ChronoUnit.DAYS.duration.toMillis() * 5,
            starred = now - ChronoUnit.HOURS.duration.toMillis(),
        )
        val rows = listOf(viewedTodayStarredEarlier, viewedEarlierStarredToday)

        val all = HistoryLedger.group(rows, HistoryScope.All, now, zone)
        assertEquals(listOf(HistoryBucket.Today, HistoryBucket.ThisWeek), all.map { it.bucket })
        assertEquals("https://x.com/i/status/1", all[0].rows.single().url)
        assertEquals("https://x.com/i/status/2", all[1].rows.single().url)

        val starred = HistoryLedger.group(rows, HistoryScope.Starred, now, zone)
        assertEquals(listOf(HistoryBucket.Today, HistoryBucket.ThisWeek), starred.map { it.bucket })
        assertEquals("https://x.com/i/status/2", starred[0].rows.single().url)
        assertEquals("https://x.com/i/status/1", starred[1].rows.single().url)

        val allUi = HistoryPresenter.present(rows, HistoryQuery(HistoryScope.All), now, zone)
        val starredUi = HistoryPresenter.present(rows, HistoryQuery(HistoryScope.Starred), now, zone)
        assertEquals(listOf("TODAY", "THIS WEEK"), allUi.sections.map { it.title })
        assertEquals(listOf("TODAY", "THIS WEEK"), starredUi.sections.map { it.title })
        assertEquals("2h", allUi.sections[0].rows.single().timeLabel)
        assertEquals("1h", starredUi.sections[0].rows.single().timeLabel)
        assertEquals("https://x.com/i/status/1", allUi.sections[0].rows.single().url)
        assertEquals("https://x.com/i/status/2", starredUi.sections[0].rows.single().url)
    }

    @Test
    fun swipesUnstarFirstAndHubLeftAlwaysRemovesFromRecents() {
        assertEquals(HistoryGesture.RemoveStar, HistoryLedger.gesture(left = true, starred = true))
        assertEquals(HistoryGesture.RemoveStar, HistoryLedger.gesture(left = false, starred = true))
        assertEquals(HistoryGesture.Remove, HistoryLedger.gesture(left = true, starred = false))
        assertEquals(HistoryGesture.Star, HistoryLedger.gesture(left = false, starred = false))
        assertEquals(HistoryGesture.Remove, HistoryLedger.hubGesture(left = true, starred = true))
        assertEquals(HistoryGesture.RemoveStar, HistoryLedger.hubGesture(left = false, starred = true))
        assertEquals(HistoryGesture.Star, HistoryLedger.hubGesture(left = false, starred = false))
    }

    @Test
    fun emptySearchCopyIsReadyWhenAFilterMatchesNothing() {
        assertEquals(
            HistoryEmptyKind.Search,
            HistoryLedger.emptyKind(
                totalRows = 2,
                starredRows = 1,
                filteredRows = 0,
                scope = HistoryScope.All,
                apps = setOf(SourceApp.Reddit),
                query = "",
            ),
        )
        assertEquals(
            HistoryEmptyKind.History,
            HistoryLedger.emptyKind(0, 0, 0, HistoryScope.All, emptySet(), ""),
        )
        assertEquals(
            HistoryEmptyKind.Starred,
            HistoryLedger.emptyKind(3, 0, 0, HistoryScope.Starred, emptySet(), ""),
        )
        assertTrue(HistoryPresenter.present(emptyList(), HistoryQuery(), now, zone).sections.isEmpty())
    }

    private fun entry(url: String, viewed: Long, starred: Long?) = HistoryEntry(
        url = url,
        source = "X",
        sourceApp = SourceApp.X,
        title = "Post",
        authorName = "Ada",
        handle = "ada",
        caption = "Post",
        thumbUrl = null,
        pfpUrl = null,
        pinnedThumbPath = null,
        pinnedPfpPath = null,
        mediaType = HistoryLedger.NONE,
        note = null,
        firstViewedAt = viewed,
        lastViewedAt = viewed,
        viewCount = 1,
        starredAt = starred,
    )
}
