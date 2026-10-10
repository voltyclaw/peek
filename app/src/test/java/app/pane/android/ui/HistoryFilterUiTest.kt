package app.pane.android.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.pane.android.domain.model.HistoryQuery
import app.pane.android.domain.model.HistoryScope
import app.pane.android.domain.model.SourceApp
import app.pane.android.testing.MutableClock
import app.pane.android.testing.NOW
import app.pane.android.testing.Seed
import app.pane.android.testing.blueskyUrl
import app.pane.android.testing.dragRow
import app.pane.android.testing.historyRepo
import app.pane.android.testing.historyVm
import app.pane.android.testing.prop
import app.pane.android.testing.redditUrl
import app.pane.android.testing.rowsOf
import app.pane.android.testing.seedOn
import app.pane.android.testing.xUrl
import app.pane.android.testing.youtubeUrl
import app.pane.android.ui.components.LedgerSwipe
import app.pane.android.ui.history.HistoryRoute
import app.pane.android.ui.theme.PaneTheme
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class HistoryFilterUiTest {
    @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun row_initial_allSelected() {
        val (repo, root) = seeded()
        show(repo)
        composeRule.onNodeWithTag(PaneTestTags.CHIP_ALL).assertIsSelected()
        composeRule.onNodeWithTag(PaneTestTags.CHIP_STARRED).assertIsNotSelected()
        composeRule.onNodeWithTag(PaneTestTags.CHIP_FILTERS).assertTextEquals("Filters")
        composeRule.onNodeWithTag(PaneTestTags.CHIP_APP).assertDoesNotExist()
        composeRule.onNodeWithTag(PaneTestTags.FILTER_HAIRLINE).assertDoesNotExist()
        assertEquals(6, runBlocking { rowsOf(repo).size })
        listOf(xUrl(1), xUrl(2), xUrl(3), redditUrl(1), redditUrl(2), youtubeUrl("abcdefghijk")).forEach {
            composeRule.onNodeWithTag(PaneTestTags.row(it)).assertExists()
        }
        root.deleteRecursively()
    }

    @Test
    fun starred_tapOnThenOff_ownRule() {
        val (repo, root) = seeded()
        show(repo)
        composeRule.onNodeWithTag(PaneTestTags.CHIP_STARRED).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(PaneTestTags.row(xUrl(1))).assertExists()
        composeRule.onNodeWithTag(PaneTestTags.row(youtubeUrl("abcdefghijk"))).assertExists()
        composeRule.onNodeWithTag(PaneTestTags.row(redditUrl(1))).assertDoesNotExist()
        composeRule.onNodeWithTag(PaneTestTags.CHIP_ALL).assertIsNotSelected()
        composeRule.onNodeWithTag(PaneTestTags.FILTER_HAIRLINE).assertIsDisplayed()
        composeRule.onNodeWithTag(PaneTestTags.CHIP_STARRED).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(PaneTestTags.row(redditUrl(1))).assertExists()
        composeRule.onNodeWithTag(PaneTestTags.CHIP_ALL).assertIsSelected()
        composeRule.onNodeWithTag(PaneTestTags.FILTER_HAIRLINE).assertDoesNotExist()
        root.deleteRecursively()
    }

    @Test
    fun filtersSheet_showsEverySupportedApp_disablesEmpty() {
        val (repo, root) = seeded()
        show(repo)
        composeRule.onNodeWithTag(PaneTestTags.CHIP_FILTERS).performClick()
        composeRule.onNodeWithTag(PaneTestTags.FILTER_SHEET).assertIsDisplayed()
        listOf(SourceApp.X, SourceApp.Reddit, SourceApp.YouTube).forEach {
            composeRule.onNodeWithTag(PaneTestTags.appTile(it)).assertIsEnabled()
        }
        listOf(SourceApp.Facebook, SourceApp.Instagram, SourceApp.TikTok, SourceApp.Bluesky, SourceApp.Other).forEach {
            composeRule.onNodeWithTag(PaneTestTags.appTile(it)).assertIsNotEnabled()
        }
        composeRule.onNodeWithTag(PaneTestTags.appTile(SourceApp.Threads)).assertDoesNotExist()
        composeRule.onNodeWithText("3").assertIsDisplayed()
        composeRule.onNodeWithText("2").assertIsDisplayed()
        composeRule.onNodeWithTag(PaneTestTags.appTile(SourceApp.Facebook)).performClick()
        composeRule.onNodeWithText("Show 6 posts").assertIsDisplayed()
        root.deleteRecursively()
    }

    @Test
    fun sheet_stagedChoice_appliesOnlyOnShow() {
        val (repo, root) = seeded()
        show(repo)
        composeRule.onNodeWithTag(PaneTestTags.CHIP_FILTERS).performClick()
        composeRule.onNodeWithTag(PaneTestTags.appTile(SourceApp.Reddit)).performClick()
        composeRule.onNodeWithTag(PaneTestTags.row(xUrl(1))).assertExists()
        composeRule.onNodeWithText("Show 2 posts").assertIsDisplayed()
        composeRule.onNodeWithTag(PaneTestTags.SHEET_SHOW).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(PaneTestTags.FILTER_SHEET).assertDoesNotExist()
        composeRule.onNodeWithTag(PaneTestTags.row(redditUrl(1))).assertExists()
        composeRule.onNodeWithTag(PaneTestTags.row(xUrl(1))).assertDoesNotExist()
        composeRule.onNodeWithTag(PaneTestTags.CHIP_FILTERS).assertTextEquals("Filters · 1")
        val chip = composeRule.onNodeWithTag(PaneTestTags.CHIP_APP).fetchSemanticsNode()
        assertTrue(chip.prop(SemanticsProperties.ContentDescription).any { it.contains("Reddit") })
        composeRule.onNodeWithTag(PaneTestTags.CHIP_APP_CLEAR).assertExists()
        composeRule.onNodeWithTag(PaneTestTags.FILTER_HAIRLINE).assertIsDisplayed()
        root.deleteRecursively()
    }

    @Test
    fun sheet_backAndScrim_discard() {
        val persisted = mutableListOf<HistoryQuery>()
        val (repo, root) = seeded()
        composeRule.setContent {
            PaneTheme { HistoryRoute(historyVm(repo, persisted = persisted), onBack = {}, onOpen = {}) }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(PaneTestTags.CHIP_FILTERS).performClick()
        composeRule.onNodeWithTag(PaneTestTags.appTile(SourceApp.Reddit)).performClick()
        composeRule.activity.onBackPressedDispatcher.onBackPressed()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(PaneTestTags.CHIP_APP).assertDoesNotExist()
        composeRule.onNodeWithTag(PaneTestTags.row(xUrl(1))).assertExists()
        assertTrue(persisted.isEmpty())
        composeRule.onNodeWithTag(PaneTestTags.CHIP_FILTERS).performClick()
        composeRule.onNodeWithTag(PaneTestTags.appTile(SourceApp.Reddit)).performClick()
        composeRule.onNodeWithTag(PaneTestTags.FILTER_SHEET).assertIsDisplayed()
        composeRule.activity.onBackPressedDispatcher.onBackPressed()
        composeRule.waitForIdle()
        assertTrue(persisted.isEmpty())
        root.deleteRecursively()
    }

    @Test
    fun sheet_tileToggle_and_switch() {
        val (repo, root) = seeded()
        show(repo)
        composeRule.onNodeWithTag(PaneTestTags.CHIP_FILTERS).performClick()
        composeRule.onNodeWithTag(PaneTestTags.appTile(SourceApp.X)).performClick()
        composeRule.onNodeWithTag(PaneTestTags.appTile(SourceApp.Reddit)).performClick()
        composeRule.onNodeWithTag(PaneTestTags.appTile(SourceApp.Reddit)).performClick()
        composeRule.onNodeWithText("Show 6 posts").assertIsDisplayed()
        root.deleteRecursively()
    }

    @Test
    fun appChip_tapOrX_clears() {
        val (repo, root) = seeded()
        show(repo)
        applyReddit()
        composeRule.onNodeWithTag(PaneTestTags.CHIP_APP).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(PaneTestTags.CHIP_ALL).assertIsSelected()
        composeRule.onNodeWithTag(PaneTestTags.row(xUrl(1))).assertExists()
        applyReddit()
        composeRule.onNodeWithTag(PaneTestTags.CHIP_APP_CLEAR).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(PaneTestTags.row(xUrl(2))).assertExists()
        root.deleteRecursively()
    }

    @Test
    fun starredAndApp_AND() {
        val (repo, root) = seeded()
        show(repo)
        applyX()
        composeRule.onNodeWithTag(PaneTestTags.CHIP_STARRED).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(PaneTestTags.row(xUrl(1))).assertExists()
        composeRule.onNodeWithTag(PaneTestTags.row(xUrl(2))).assertDoesNotExist()
        composeRule.onNodeWithTag(PaneTestTags.CHIP_STARRED).assertIsSelected()
        composeRule.onNodeWithTag(PaneTestTags.CHIP_ALL).assertIsNotSelected()
        root.deleteRecursively()
    }

    @Test
    fun starredOn_sheetDimsAppsWithNoStars() {
        val (repo, root) = seeded()
        show(repo)
        composeRule.onNodeWithTag(PaneTestTags.CHIP_STARRED).performClick()
        composeRule.onNodeWithTag(PaneTestTags.CHIP_FILTERS).performClick()
        composeRule.onNodeWithTag(PaneTestTags.appTile(SourceApp.Reddit)).assertIsNotEnabled()
        val desc = composeRule.onNodeWithTag(PaneTestTags.appTile(SourceApp.Reddit))
            .fetchSemanticsNode().prop(SemanticsProperties.ContentDescription)
        assertTrue(desc.any { it.contains("Reddit, no starred posts") })
        composeRule.onNodeWithTag(PaneTestTags.appTile(SourceApp.X)).assertIsEnabled()
        composeRule.onNodeWithTag(PaneTestTags.appTile(SourceApp.YouTube)).assertIsEnabled()
        root.deleteRecursively()
    }

    @Test
    fun starredAfterApp_emptyCopy() {
        val (repo, root) = seeded()
        show(repo)
        applyReddit()
        composeRule.onNodeWithTag(PaneTestTags.CHIP_STARRED).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(PaneTestTags.CHIP_APP).assertExists()
        composeRule.onNodeWithText("No starred Reddit posts yet").assertIsDisplayed()
        composeRule.onNodeWithText("Tap All to see everything.").assertIsDisplayed()
        composeRule.onNodeWithTag(PaneTestTags.CHIP_ALL).assertIsDisplayed()
        root.deleteRecursively()
    }

    @Test
    fun filterMemory_restoresOnReopen() {
        val (repo, root) = seeded()
        val persisted = mutableListOf<HistoryQuery>()
        val shown = mutableStateOf(historyVm(repo, persisted = persisted))
        composeRule.setContent { PaneTheme { HistoryRoute(shown.value, onBack = {}, onOpen = {}) } }
        composeRule.waitForIdle()
        applyYouTube()
        composeRule.onNodeWithTag(PaneTestTags.CHIP_STARRED).performClick()
        composeRule.waitForIdle()
        val saved = persisted.last()
        composeRule.runOnIdle { shown.value = historyVm(repo, saved) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(PaneTestTags.CHIP_STARRED).assertIsSelected()
        val chip = composeRule.onNodeWithTag(PaneTestTags.CHIP_APP).fetchSemanticsNode()
        assertTrue(chip.prop(SemanticsProperties.ContentDescription).any { it.contains("YouTube") })
        root.deleteRecursively()
    }

    @Test
    fun rememberedAppRemoved_fallsBackToAll() {
        val (repo, root) = seeded()
        runBlocking { repo.remove(youtubeUrl("abcdefghijk")) }
        composeRule.setContent {
            PaneTheme {
                HistoryRoute(
                    historyVm(repo, HistoryQuery(apps = setOf(SourceApp.YouTube))),
                    onBack = {},
                    onOpen = {},
                )
            }
        }
        composeRule.waitUntil(3_000) {
            composeRule.onAllNodes(hasClickAction()).fetchSemanticsNodes().isNotEmpty() &&
                runCatching { composeRule.onNodeWithTag(PaneTestTags.CHIP_ALL).assertIsSelected() }.isSuccess
        }
        composeRule.onNodeWithTag(PaneTestTags.CHIP_APP).assertDoesNotExist()
        root.deleteRecursively()
    }

    @Test
    fun ordering_starredUsesStarredAt() {
        val (repo, root) = seeded()
        show(repo)
        composeRule.onNodeWithTag(PaneTestTags.CHIP_STARRED).performClick()
        composeRule.waitForIdle()
        val x = composeRule.onNodeWithTag(PaneTestTags.row(xUrl(1))).fetchSemanticsNode().boundsInRoot.top
        val yt = composeRule.onNodeWithTag(PaneTestTags.row(youtubeUrl("abcdefghijk"))).fetchSemanticsNode().boundsInRoot.top
        assertTrue(x < yt)
        root.deleteRecursively()
    }

    @Test
    fun a11y_chipsAreToggles_andCountAnnounced() {
        val (repo, root) = seeded()
        show(repo)
        val all = composeRule.onNodeWithTag(PaneTestTags.CHIP_ALL).fetchSemanticsNode()
        assertEquals(Role.RadioButton, all.prop(SemanticsProperties.Role))
        assertEquals("Double-tap to show all", all.prop(SemanticsActions.OnClick).label)
        val starred = composeRule.onNodeWithTag(PaneTestTags.CHIP_STARRED).fetchSemanticsNode()
        assertEquals(Role.Checkbox, starred.prop(SemanticsProperties.Role))
        composeRule.onNode(isHeading()).assertIsDisplayed()
        applyReddit()
        composeRule.onNodeWithTag(PaneTestTags.RESULTS_ANNOUNCER).assertTextEquals("2 posts")
        val announcer = composeRule.onNodeWithTag(PaneTestTags.RESULTS_ANNOUNCER).fetchSemanticsNode()
        assertEquals(
            androidx.compose.ui.semantics.LiveRegionMode.Polite,
            announcer.prop(SemanticsProperties.LiveRegion),
        )
        root.deleteRecursively()
    }

    @Test
    fun swipeWhileFiltered_rowLeavesFilteredList() {
        val (repo, root) = seeded()
        show(repo)
        composeRule.onNodeWithTag(PaneTestTags.CHIP_STARRED).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(PaneTestTags.row(xUrl(1))).dragRow(LedgerSwipe.COMMIT_FRACTION + 0.02f, -1, 800)
        composeRule.waitUntil(3_000) {
            runBlocking { rowsOf(repo).first { it.url == xUrl(1) }.starredAt == null }
        }
        composeRule.onNodeWithTag(PaneTestTags.row(xUrl(1))).assertDoesNotExist()
        composeRule.onNodeWithText("Star removed").assertIsDisplayed()
        composeRule.onNodeWithText("Undo").performClick()
        composeRule.waitUntil(3_000) {
            runBlocking { rowsOf(repo).first { it.url == xUrl(1) }.starredAt != null }
        }
        composeRule.onNodeWithTag(PaneTestTags.row(xUrl(1))).assertExists()
        root.deleteRecursively()
    }

    @Test
    @Config(qualifiers = "w320dp-h891dp-xxhdpi")
    fun overflow_selectedAppChipVisible() {
        val root = Files.createTempDirectory("pane-narrow").toFile()
        val clock = MutableClock()
        val repo = historyRepo(root, clock)
        runBlocking {
            seedOn(repo, clock, Seed(blueskyUrl(1), "Sky", NOW - 1_000, starredAt = NOW))
        }
        show(repo)
        composeRule.onNodeWithTag(PaneTestTags.CHIP_FILTERS).performClick()
        composeRule.onNodeWithTag(PaneTestTags.appTile(SourceApp.Bluesky)).performClick()
        composeRule.onNodeWithTag(PaneTestTags.SHEET_SHOW).performClick()
        composeRule.onNodeWithTag(PaneTestTags.CHIP_STARRED).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(PaneTestTags.CHIP_APP).assertIsDisplayed()
        root.deleteRecursively()
    }

    private fun seeded(): Pair<app.pane.android.data.history.SqliteHistoryRepository, java.io.File> {
        val root = Files.createTempDirectory("pane-filter-ui").toFile()
        val clock = MutableClock()
        val repo = historyRepo(root, clock)
        runBlocking {
            seedOn(
                repo,
                clock,
                Seed(youtubeUrl("abcdefghijk"), "Watch", NOW - 8_000, starredAt = NOW),
                Seed(xUrl(1), "X1", NOW - 3_000, starredAt = NOW + 5_000),
                Seed(xUrl(2), "X2", NOW - 2_000),
                Seed(xUrl(3), "X3", NOW - 1_000),
                Seed(redditUrl(1), "R1", NOW - 4_000),
                Seed(redditUrl(2), "R2", NOW - 5_000),
            )
        }
        return repo to root
    }

    private fun show(repo: app.pane.android.data.history.SqliteHistoryRepository) {
        composeRule.setContent { PaneTheme { HistoryRoute(historyVm(repo), onBack = {}, onOpen = {}) } }
        composeRule.waitForIdle()
    }

    private fun applyReddit() {
        composeRule.onNodeWithTag(PaneTestTags.CHIP_FILTERS).performClick()
        composeRule.onNodeWithTag(PaneTestTags.appTile(SourceApp.Reddit)).performClick()
        composeRule.onNodeWithTag(PaneTestTags.SHEET_SHOW).performClick()
        composeRule.waitForIdle()
    }

    private fun applyX() {
        composeRule.onNodeWithTag(PaneTestTags.CHIP_FILTERS).performClick()
        composeRule.onNodeWithTag(PaneTestTags.appTile(SourceApp.X)).performClick()
        composeRule.onNodeWithTag(PaneTestTags.SHEET_SHOW).performClick()
        composeRule.waitForIdle()
    }

    private fun applyYouTube() {
        composeRule.onNodeWithTag(PaneTestTags.CHIP_FILTERS).performClick()
        composeRule.onNodeWithTag(PaneTestTags.appTile(SourceApp.YouTube)).performClick()
        composeRule.onNodeWithTag(PaneTestTags.SHEET_SHOW).performClick()
        composeRule.waitForIdle()
    }
}
