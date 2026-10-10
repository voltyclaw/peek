package app.pane.android.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.pane.android.domain.model.HistoryEntry
import app.pane.android.domain.model.HistoryLedger
import app.pane.android.domain.model.HistoryQuery
import app.pane.android.domain.model.HistoryScope
import app.pane.android.domain.model.SourceApp
import app.pane.android.testing.NOW
import app.pane.android.ui.components.LedgerSwipeRow
import app.pane.android.ui.history.HistoryPresenter
import app.pane.android.ui.history.HistoryView
import app.pane.android.ui.model.HomeUiState
import app.pane.android.ui.model.LedgerRowUi
import app.pane.android.ui.model.RecentLinkUiModel
import app.pane.android.ui.theme.PaneTheme
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import java.util.Locale
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@OptIn(ExperimentalRoborazziApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w393dp-h852dp-xxhdpi")
class HistoryScreenshotTest {
    @get:Rule val composeRule = createComposeRule()

    private val options = RoborazziOptions(
        compareOptions = RoborazziOptions.CompareOptions(changeThreshold = 0.005f),
    )

    @Test
    @Config(qualifiers = "iw-rIL-ldrtl-w393dp-h852dp-xxhdpi")
    fun history_rtl_mixedRows() = shot("history/history_rtl_mixedRows.png", LayoutDirection.Rtl) {
        history(sampleRows(), HistoryQuery(), rtl = true)
    }

    @Test
    fun history_ltr_hebrewTitles() = shot("history/history_ltr_hebrewTitles.png", LayoutDirection.Ltr) {
        history(sampleRows(), HistoryQuery(), rtl = false)
    }

    @Test
    fun swipe_ltr_left_pastThreshold() = swipeShot("history/swipe_ltr_left_pastThreshold.png", -0.5f, LayoutDirection.Ltr, starred = false)

    @Test
    fun swipe_ltr_right_pastThreshold() = swipeShot("history/swipe_ltr_right_pastThreshold.png", 0.5f, LayoutDirection.Ltr, starred = true)

    @Test
    @Config(qualifiers = "iw-rIL-ldrtl-w393dp-h852dp-xxhdpi")
    fun swipe_rtl_left_pastThreshold() = swipeShot("history/swipe_rtl_left_pastThreshold.png", -0.5f, LayoutDirection.Rtl, starred = false)

    @Test
    @Config(qualifiers = "iw-rIL-ldrtl-w393dp-h852dp-xxhdpi")
    fun swipe_rtl_right_pastThreshold() = swipeShot("history/swipe_rtl_right_pastThreshold.png", 0.5f, LayoutDirection.Rtl, starred = true)

    @Test
    @Config(qualifiers = "iw-rIL-ldrtl-w393dp-h852dp-xxhdpi")
    fun swipe_rtl_belowThreshold() = swipeShot("history/swipe_rtl_belowThreshold.png", 0.30f, LayoutDirection.Rtl, starred = false)

    @Test
    @Config(qualifiers = "iw-rIL-ldrtl-w393dp-h852dp-xxhdpi")
    fun filters_row_rtl_states() = shot("history/filters_row_rtl_states.png", LayoutDirection.Rtl) {
        history(sampleRows(), HistoryQuery(HistoryScope.Starred, setOf(SourceApp.YouTube)), rtl = true)
    }

    @Test
    @Config(qualifiers = "iw-rIL-ldrtl-w393dp-h852dp-xxhdpi")
    fun filters_sheet_rtl() = shot("history/filters_sheet_rtl.png", LayoutDirection.Rtl) {
        history(sampleRows(), HistoryQuery(scope = HistoryScope.Starred), rtl = true, filtersOpen = true)
    }

    @Test
    @Config(qualifiers = "iw-rIL-ldrtl-w360dp-h852dp-xxhdpi", fontScale = 1.3f)
    fun history_rtl_fontScale130() = shot("history/history_rtl_fontScale130.png", LayoutDirection.Rtl) {
        history(sampleRows(), HistoryQuery(), rtl = true)
    }

    @Test
    @Config(qualifiers = "iw-rIL-ldrtl-w393dp-h852dp-xxhdpi")
    fun hub_recents_rtl() = shot("history/hub_recents_rtl.png", LayoutDirection.Rtl) {
        app.pane.android.ui.home.HomeView(
            uiState = HomeUiState.Content(
                listOf(
                    recent("https://x.com/i/status/1", "בוקר שקט", "@maya", starred = true),
                    recent("https://www.reddit.com/r/pics/comments/abc1/title/", "Analog", "r/AnalogCommunity", starred = false),
                    recent("https://www.instagram.com/p/abc1/", "Film", "@studio", starred = false),
                    recent("https://www.youtube.com/watch?v=abcdefghijk", "Study", "@channel", starred = false),
                ),
            ),
            onPasteClick = {},
            onRecentLink = {},
            modifier = Modifier.height(640.dp),
        )
    }

    @Test
    @Config(qualifiers = "iw-rIL-ldrtl-w393dp-h852dp-xxhdpi")
    fun history_empty_starredApp_rtl() = shot("history/history_empty_starredApp_rtl.png", LayoutDirection.Rtl) {
        history(sampleRows().filter { it.sourceApp != SourceApp.Reddit }, HistoryQuery(HistoryScope.Starred, setOf(SourceApp.Reddit)), rtl = true)
    }

    private fun swipeShot(path: String, fraction: Float, direction: LayoutDirection, starred: Boolean) {
        shot(path, direction) {
            LedgerSwipeRow(
                row = LedgerRowUi(
                    url = "https://x.com/i/status/1",
                    title = "A quiet row",
                    identity = "@maya",
                    timeLabel = "2h",
                    pfp = null,
                    sourceMark = null,
                    thumb = null,
                    video = false,
                    starred = starred,
                ),
                onOpen = {},
                onSwipe = {},
                onLongPress = {},
                onActionStar = {},
                onActionRemove = {},
                previewOffsetFraction = fraction,
            )
        }
    }

    private fun shot(path: String, direction: LayoutDirection, content: @androidx.compose.runtime.Composable () -> Unit) {
        composeRule.setContent {
            PaneTheme {
                CompositionLocalProvider(LocalLayoutDirection provides direction) {
                    Box(Modifier.fillMaxSize()) { content() }
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage(
            filePath = "src/test/screenshots/$path",
            roborazziOptions = options,
        )
    }

    @androidx.compose.runtime.Composable
    private fun history(
        rows: List<HistoryEntry>,
        query: HistoryQuery,
        rtl: Boolean,
        filtersOpen: Boolean = false,
    ) {
        val ui = HistoryPresenter.present(rows, query, NOW, java.time.ZoneOffset.UTC, if (rtl) Locale("iw") else Locale.US)
        HistoryView(
            ui = ui,
            onBack = {},
            onOpen = {},
            onShowAll = {},
            onToggleStarred = {},
            onOpenFilters = {},
            onDismissFilters = {},
            onApplyApp = {},
            onClearApp = {},
            onSwipe = { _, _ -> },
            onRemove = {},
            onLongPress = {},
            filtersOpen = filtersOpen,
        )
    }

    private fun sampleRows(): List<HistoryEntry> = listOf(
        entry(SourceApp.X, "https://x.com/i/status/1", "בוקר שקט בירושלים", "@maya", NOW - 3_600_000, NOW, "video"),
        entry(SourceApp.Reddit, "https://www.reddit.com/r/pics/comments/abc1/title/", "Morning light", "r/AnalogCommunity", NOW - 7_200_000, null, "none"),
        entry(SourceApp.Instagram, "https://www.instagram.com/p/abc1/", "Frame", "studio", NOW - 10_000_000, null, "image"),
        entry(SourceApp.Facebook, "https://www.facebook.com/story.php?id=1", "שלום", "Maya Cohen", NOW - 30 * 3_600_000, null, "none"),
        entry(SourceApp.YouTube, "https://www.youtube.com/watch?v=abcdefghijk", "Study", "Channel", NOW - 26 * 3_600_000, NOW - 1_000, "video"),
    )

    private fun entry(
        app: SourceApp,
        url: String,
        title: String,
        handle: String,
        viewed: Long,
        starred: Long?,
        media: String,
    ) = HistoryEntry(
        url = url,
        source = app.name,
        sourceApp = app,
        title = title,
        authorName = handle,
        handle = handle,
        caption = title,
        thumbUrl = null,
        pfpUrl = null,
        pinnedThumbPath = null,
        pinnedPfpPath = null,
        mediaType = if (media == "video") HistoryLedger.VIDEO else if (media == "image") HistoryLedger.IMAGE else HistoryLedger.NONE,
        note = null,
        firstViewedAt = viewed,
        lastViewedAt = viewed,
        viewCount = 1,
        starredAt = starred,
    )

    private fun recent(url: String, title: String, identity: String, starred: Boolean) = RecentLinkUiModel(
        url = url,
        title = title,
        sourceLabel = identity,
        ageLabel = "2h",
        thumbnail = null,
        thumbnailDescription = "",
        isCached = true,
        identity = identity,
        starred = starred,
    )
}
