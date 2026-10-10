package app.pane.android.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onChildren
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import app.pane.android.domain.model.HistoryGesture
import app.pane.android.domain.model.HistoryQuery
import app.pane.android.domain.model.HistoryScope
import app.pane.android.domain.model.StarImageBytes
import app.pane.android.testing.NOW
import app.pane.android.testing.RecordingHaptics
import app.pane.android.testing.Seed
import app.pane.android.testing.dragRow
import app.pane.android.testing.historyRepo
import app.pane.android.testing.historyVm
import app.pane.android.testing.prop
import app.pane.android.testing.redditUrl
import app.pane.android.testing.rowsOf
import app.pane.android.testing.seed
import app.pane.android.testing.xUrl
import app.pane.android.ui.components.LedgerSwipe
import app.pane.android.ui.components.LedgerSwipeFraction
import app.pane.android.ui.components.LedgerSwipeKind
import app.pane.android.ui.components.LedgerSwipePast
import app.pane.android.ui.history.HistoryNotice as UiNotice
import app.pane.android.ui.history.HistoryRoute
import app.pane.android.ui.theme.PaneTheme
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.math.abs

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class HistoryGestureUiTest {
    @get:Rule val composeRule = createComposeRule()

    private lateinit var root: File
    private val haptics = RecordingHaptics()
    private val opened = mutableListOf<String>()
    private val notices = mutableListOf<UiNotice>()

    @Before fun setUp() {
        root = Files.createTempDirectory("pane-gesture").toFile()
    }

    @After fun tearDown() {
        root.deleteRecursively()
    }

    @Test
    fun slowLeftDrag_pastCommit_onRelease_removesRow() {
        val repo = fresh()
        val a = xUrl(1)
        val b = redditUrl(2)
        runBlocking {
            seed(
                repo,
                Seed(a, "Alpha", NOW - 3_600_000),
                Seed(b, "Beta", NOW - 7_200_000),
            )
        }
        show(repo)
        val row = composeRule.onNodeWithTag(PaneTestTags.row(a))
        composeRule.mainClock.autoAdvance = false
        row.dragRow(LedgerSwipe.COMMIT_FRACTION + 0.02f, -1, 1000, release = false)
        pumpFrames()
        assertEquals(true, row.fetchSemanticsNode().prop(LedgerSwipePast))
        assertEquals(HistoryGesture.Remove, row.fetchSemanticsNode().prop(LedgerSwipeKind))
        composeRule.onNodeWithText("Remove", useUnmergedTree = true).assertIsDisplayed()
        row.performTouchInput { up() }
        composeRule.mainClock.autoAdvance = true
        composeRule.waitForIdle()
        awaitRows(repo) { rows -> rows.none { it.url == a } }
        composeRule.onNodeWithTag(PaneTestTags.row(a)).assertDoesNotExist()
        composeRule.onNodeWithText("Removed from History").assertIsDisplayed()
        composeRule.onNodeWithText("Undo").assertIsDisplayed()
        runBlocking {
            assertEquals(listOf(b), rowsOf(repo).map { it.url })
        }
    }

    @Test
    fun slowLeftDrag_belowCommit_snapsBack_noWrite() {
        val repo = fresh()
        val a = xUrl(1)
        runBlocking {
            seed(repo, Seed(a, "Alpha", NOW - 3_600_000), Seed(redditUrl(2), "Beta", NOW - 7_200_000))
        }
        val before = runBlocking { rowsOf(repo).map { it.url to it.lastViewedAt to it.viewCount } }
        show(repo)
        composeRule.onNodeWithTag(PaneTestTags.row(a))
            .dragRow(LedgerSwipe.COMMIT_FRACTION - 0.05f, -1, 1000)
        composeRule.mainClock.advanceTimeBy(1_000)
        composeRule.waitForIdle()
        val fraction = composeRule.onNodeWithTag(PaneTestTags.row(a)).fetchSemanticsNode().prop(LedgerSwipeFraction)
        assertTrue(abs(fraction) < 0.001f)
        composeRule.onNodeWithTag(PaneTestTags.row(a)).assertExists()
        composeRule.onNodeWithTag(PaneTestTags.SNACKBAR).onChildren().assertCountEquals(0)
        val after = runBlocking { rowsOf(repo).map { it.url to it.lastViewedAt to it.viewCount } }
        assertEquals(before, after)
    }

    @Test
    fun commitBoundary_isCOMMIT_FRACTION() {
        val below = LedgerSwipe.COMMIT_FRACTION - 0.02f
        val above = LedgerSwipe.COMMIT_FRACTION + 0.02f
        val repo = fresh()
        val a = xUrl(1)
        runBlocking { seed(repo, Seed(a, "Alpha", NOW - 3_600_000), Seed(redditUrl(2), "Beta", NOW - 7_200_000)) }
        show(repo)
        composeRule.onNodeWithTag(PaneTestTags.row(a)).dragRow(below, -1, 1000)
        composeRule.mainClock.advanceTimeBy(1_000)
        composeRule.waitForIdle()
        assertEquals(2, runBlocking { rowsOf(repo).size })
        composeRule.onNodeWithTag(PaneTestTags.row(a)).dragRow(above, -1, 1000)
        awaitRows(repo) { rows -> rows.none { it.url == a } }
    }

    @Test
    fun fastLeftFling_pastFlingFraction_commitsBelowCommitFraction() {
        val repo = fresh()
        val a = xUrl(1)
        runBlocking { seed(repo, Seed(a, "Alpha", NOW - 3_600_000), Seed(redditUrl(2), "Beta", NOW - 7_200_000)) }
        show(repo)
        composeRule.mainClock.autoAdvance = false
        // Event injection is quantized to frames, so a 20-step drag is too slow to fling.
        composeRule.onNodeWithTag(PaneTestTags.row(a)).dragRow(0.30f, -1, 32, release = false, steps = 3)
        val fraction = abs(composeRule.onNodeWithTag(PaneTestTags.row(a)).fetchSemanticsNode().prop(LedgerSwipeFraction))
        assertTrue(fraction >= LedgerSwipe.FLING_FRACTION)
        assertTrue(fraction < LedgerSwipe.COMMIT_FRACTION)
        composeRule.onNodeWithTag(PaneTestTags.row(a)).performTouchInput { up() }
        composeRule.mainClock.autoAdvance = true
        awaitRows(repo) { rows -> rows.none { it.url == a } }
    }

    @Test
    fun fastFling_belowFlingFraction_snapsBack() {
        val repo = fresh()
        val a = xUrl(1)
        runBlocking { seed(repo, Seed(a, "Alpha", NOW - 3_600_000), Seed(redditUrl(2), "Beta", NOW - 7_200_000)) }
        show(repo)
        composeRule.onNodeWithTag(PaneTestTags.row(a)).dragRow(0.18f, -1, 40)
        composeRule.mainClock.advanceTimeBy(1_000)
        composeRule.waitForIdle()
        assertEquals(2, runBlocking { rowsOf(repo).size })
        val fraction = composeRule.onNodeWithTag(PaneTestTags.row(a)).fetchSemanticsNode().prop(LedgerSwipeFraction)
        assertTrue(abs(fraction) < 0.001f)
    }

    @Test
    fun fling_thenHold_beforeRelease_doesNotCommit() {
        val repo = fresh()
        val a = xUrl(1)
        runBlocking { seed(repo, Seed(a, "Alpha", NOW - 3_600_000), Seed(redditUrl(2), "Beta", NOW - 7_200_000)) }
        show(repo)
        composeRule.onNodeWithTag(PaneTestTags.row(a)).performTouchInput {
            val startX = width * 0.85f
            val total = 0.30f * width + viewConfiguration.touchSlop + 2f
            down(androidx.compose.ui.geometry.Offset(startX, centerY))
            repeat(8) {
                advanceEventTime(10)
                moveBy(androidx.compose.ui.geometry.Offset(-total / 8f, 0f))
            }
            repeat(6) {
                advanceEventTime(40)
                moveBy(androidx.compose.ui.geometry.Offset.Zero)
            }
            up()
        }
        composeRule.mainClock.advanceTimeBy(1_000)
        composeRule.waitForIdle()
        assertEquals(2, runBlocking { rowsOf(repo).size })
    }

    @Test
    fun fling_reversedAtRelease_doesNotCommit() {
        val repo = fresh()
        val a = xUrl(1)
        runBlocking { seed(repo, Seed(a, "Alpha", NOW - 3_600_000), Seed(redditUrl(2), "Beta", NOW - 7_200_000)) }
        show(repo)
        composeRule.onNodeWithTag(PaneTestTags.row(a)).performTouchInput {
            val total = 0.30f * width + viewConfiguration.touchSlop + 2f
            down(androidx.compose.ui.geometry.Offset(width * 0.85f, centerY))
            repeat(20) {
                advanceEventTime(50)
                moveBy(androidx.compose.ui.geometry.Offset(-total / 20f, 0f))
            }
            advanceEventTime(16)
            moveBy(androidx.compose.ui.geometry.Offset(0.03f * width, 0f))
            up()
        }
        composeRule.waitForIdle()
        assertEquals(2, runBlocking { rowsOf(repo).size })
    }

    @Test
    fun haptic_firesOnceOnCrossing_andOnceOnCrossingBack() {
        val repo = fresh()
        val a = xUrl(1)
        runBlocking { seed(repo, Seed(a, "Alpha", NOW - 3_600_000)) }
        show(repo)
        composeRule.mainClock.autoAdvance = false
        val row = composeRule.onNodeWithTag(PaneTestTags.row(a))
        row.performTouchInput {
            val slop = viewConfiguration.touchSlop + 2f
            down(androidx.compose.ui.geometry.Offset(width * 0.85f, centerY))
            val past = 0.50f * width + slop
            repeat(10) {
                advanceEventTime(40)
                moveBy(androidx.compose.ui.geometry.Offset(-past / 10f, 0f))
            }
        }
        assertEquals(listOf(HapticFeedbackType.LongPress), haptics.events.toList())
        row.performTouchInput {
            val back = 0.20f * width
            repeat(5) {
                advanceEventTime(40)
                moveBy(androidx.compose.ui.geometry.Offset(back / 5f, 0f))
            }
            up()
        }
        assertEquals(2, haptics.events.size)
        composeRule.mainClock.autoAdvance = true
        composeRule.waitForIdle()
        assertEquals(1, runBlocking { rowsOf(repo).size })

        haptics.events.clear()
        composeRule.onNodeWithTag(PaneTestTags.row(a)).dragRow(0.40f, -1, 1000)
        composeRule.waitForIdle()
        assertTrue(haptics.events.isEmpty())
    }

    @Test
    fun verticalDrag_scrollsList_doesNotSwipe() {
        val repo = fresh()
        val urls = (1..20).map { xUrl(it) }
        runBlocking {
            seed(repo, *urls.mapIndexed { index, url -> Seed(url, "Row $index", NOW - index * 60_000L) }.toTypedArray())
        }
        show(repo)
        val first = composeRule.onNodeWithTag(PaneTestTags.row(urls.first()))
        val top = first.fetchSemanticsNode().boundsInRoot.top
        first.performTouchInput { swipeUp() }
        composeRule.waitForIdle()
        val fraction = composeRule.onNodeWithTag(PaneTestTags.row(urls.first())).fetchSemanticsNode().prop(LedgerSwipeFraction)
        assertEquals(0f, fraction)
        val moved = composeRule.onNodeWithTag(PaneTestTags.row(urls.first())).fetchSemanticsNode().boundsInRoot.top
        assertTrue(moved != top)
        assertEquals(20, runBlocking { rowsOf(repo).size })
    }

    @Test
    fun diagonalDrag_horizontalDominant_swipes() {
        val repo = fresh()
        val a = xUrl(1)
        runBlocking { seed(repo, Seed(a, "Alpha", NOW - 3_600_000), Seed(redditUrl(2), "Beta", NOW - 7_200_000)) }
        show(repo)
        composeRule.onNodeWithTag(PaneTestTags.row(a)).performTouchInput {
            val dx = -(0.50f * width + viewConfiguration.touchSlop + 2f)
            val dy = 0.20f * width
            down(androidx.compose.ui.geometry.Offset(width * 0.85f, centerY))
            repeat(20) {
                advanceEventTime(50)
                moveBy(androidx.compose.ui.geometry.Offset(dx / 20f, dy / 20f))
            }
            up()
        }
        awaitRows(repo) { rows -> rows.none { it.url == a } }
    }

    @Test
    fun rightSwipe_unstarred_pastCommit_starsRow() {
        val repo = fresh()
        val a = xUrl(1)
        runBlocking { seed(repo, Seed(a, "Alpha", NOW - 3_600_000)) }
        show(repo)
        composeRule.onNodeWithTag(PaneTestTags.row(a))
            .dragRow(LedgerSwipe.COMMIT_FRACTION + 0.02f, +1, 1000)
        awaitRows(repo) { rows -> rows.single().starredAt != null }
        composeRule.onNodeWithTag(PaneTestTags.rowBody(a)).assertExists()
        val description = composeRule.onNodeWithTag(PaneTestTags.rowBody(a))
            .fetchSemanticsNode().prop(SemanticsProperties.StateDescription)
        assertEquals("Starred", description)
        composeRule.onNodeWithText("Starred. It'll be in History when you want it.").assertIsDisplayed()
        composeRule.onNodeWithTag(PaneTestTags.SNACKBAR).onChildren().assertCountEquals(1)
    }

    @Test
    fun rowTap_afterSnapBack_opensRow_once() {
        val repo = fresh()
        val a = xUrl(1)
        runBlocking { seed(repo, Seed(a, "Alpha", NOW - 3_600_000)) }
        show(repo)
        composeRule.onNodeWithTag(PaneTestTags.row(a)).dragRow(0.40f, -1, 1000)
        composeRule.mainClock.advanceTimeBy(1_000)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(PaneTestTags.rowBody(a)).performClick()
        composeRule.waitForIdle()
        assertEquals(listOf(a), opened)
    }

    @Test
    fun all_leftSwipeOnStarred_unstarsOnly_rowStays() {
        val repo = fresh()
        val s = xUrl(1)
        val images = StarImageBytes(ByteArray(32) { 3 }, ByteArray(16) { 4 })
        runBlocking {
            seed(
                repo,
                Seed(s, "Starred", NOW - 3_600_000, starredAt = NOW, images = images),
                Seed(redditUrl(2), "Plain", NOW - 7_200_000),
            )
        }
        val before = runBlocking { rowsOf(repo).first { it.url == s } }
        val pinnedThumb = before.pinnedThumbPath!!
        assertTrue(File(root, pinnedThumb).exists())
        show(repo)
        composeRule.mainClock.autoAdvance = false
        val row = composeRule.onNodeWithTag(PaneTestTags.row(s))
        row.dragRow(LedgerSwipe.COMMIT_FRACTION + 0.02f, -1, 1000, release = false)
        pumpFrames()
        assertEquals(HistoryGesture.RemoveStar, row.fetchSemanticsNode().prop(LedgerSwipeKind))
        composeRule.onNodeWithText("Remove star", useUnmergedTree = true).assertIsDisplayed()
        row.performTouchInput { up() }
        composeRule.mainClock.autoAdvance = true
        awaitRows(repo) { rows -> rows.first { it.url == s }.starredAt == null }
        val after = runBlocking { rowsOf(repo).first { it.url == s } }
        assertEquals("Not starred", composeRule.onNodeWithTag(PaneTestTags.rowBody(s)).fetchSemanticsNode().prop(SemanticsProperties.StateDescription))
        assertEquals(before.viewCount, after.viewCount)
        assertEquals(before.firstViewedAt, after.firstViewedAt)
        assertEquals(before.lastViewedAt, after.lastViewedAt)
        assertTrue(!File(root, pinnedThumb).exists())
        composeRule.onNodeWithText("Star removed").assertIsDisplayed()
        composeRule.onNodeWithText("Undo").assertIsDisplayed()
    }

    @Test
    fun all_secondLeftSwipe_removes() {
        val repo = fresh()
        val s = xUrl(1)
        runBlocking { seed(repo, Seed(s, "Starred", NOW - 3_600_000, starredAt = NOW)) }
        show(repo)
        composeRule.onNodeWithTag(PaneTestTags.row(s)).dragRow(LedgerSwipe.COMMIT_FRACTION + 0.02f, -1, 1000)
        awaitRows(repo) { rows -> rows.single().starredAt == null }
        composeRule.mainClock.advanceTimeBy(5_100)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(PaneTestTags.row(s)).dragRow(LedgerSwipe.COMMIT_FRACTION + 0.02f, -1, 1000)
        awaitRows(repo) { it.isEmpty() }
        composeRule.onNodeWithText("Removed from History").assertIsDisplayed()
    }

    @Test
    fun all_twoQuickLeftSwipes_doNotSkipTheStar() {
        val repo = fresh()
        val s = xUrl(1)
        runBlocking { seed(repo, Seed(s, "Starred", NOW - 3_600_000, starredAt = NOW)) }
        show(repo)
        val row = composeRule.onNodeWithTag(PaneTestTags.row(s))
        row.dragRow(LedgerSwipe.COMMIT_FRACTION + 0.02f, -1, 400)
        composeRule.waitForIdle()
        if (runBlocking { rowsOf(repo).any { it.url == s } }) {
            composeRule.onNodeWithTag(PaneTestTags.row(s)).dragRow(LedgerSwipe.COMMIT_FRACTION + 0.02f, -1, 400)
        }
        composeRule.waitForIdle()
        awaitRows(repo) { rows ->
            val row = rows.find { it.url == s }
            row == null || row.starredAt == null
        }
        assertTrue(notices.first() is UiNotice.Unstarred)
        notices.filterIsInstance<UiNotice.Removed>().forEach { removed ->
            assertNull(removed.undo.row.starredAt)
        }
    }

    @Test
    fun starredChip_leftSwipe_unstars_rowLeavesList_staysInHistory() {
        val repo = fresh()
        val s = xUrl(1)
        runBlocking {
            seed(
                repo,
                Seed(s, "Starred", NOW - 3_600_000, starredAt = NOW),
                Seed(redditUrl(2), "Plain", NOW - 7_200_000),
            )
        }
        show(repo, HistoryQuery(scope = HistoryScope.Starred))
        composeRule.onNodeWithTag(PaneTestTags.row(s)).dragRow(LedgerSwipe.COMMIT_FRACTION + 0.02f, -1, 1000)
        awaitRows(repo) { rows -> rows.first { it.url == s }.starredAt == null }
        composeRule.onNodeWithTag(PaneTestTags.row(s)).assertDoesNotExist()
        composeRule.onNodeWithTag(PaneTestTags.CHIP_ALL).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(PaneTestTags.row(s)).assertExists()
        composeRule.onNodeWithText("Star removed").assertIsDisplayed()
    }

    @Test
    fun rightSwipe_onStarred_unstars_withUndo() {
        val repo = fresh()
        val s = xUrl(1)
        runBlocking { seed(repo, Seed(s, "Starred", NOW - 3_600_000, starredAt = NOW)) }
        show(repo)
        composeRule.onNodeWithTag(PaneTestTags.row(s)).dragRow(LedgerSwipe.COMMIT_FRACTION + 0.02f, +1, 1000)
        awaitRows(repo) { rows -> rows.single().starredAt == null }
        composeRule.onNodeWithTag(PaneTestTags.row(s)).assertExists()
        composeRule.onNodeWithText("Star removed").assertIsDisplayed()
        composeRule.onNodeWithText("Undo").assertIsDisplayed()
    }

    @Test
    fun menuRemove_onStarred_deletesRowAndStar_undoRestoresBoth() {
        val repo = fresh()
        val s = xUrl(1)
        val images = StarImageBytes(ByteArray(32) { 3 }, ByteArray(16) { 4 })
        runBlocking { seed(repo, Seed(s, "Starred", NOW - 3_600_000, starredAt = NOW, images = images)) }
        val before = runBlocking { rowsOf(repo).single() }
        show(repo)
        composeRule.onNodeWithTag(PaneTestTags.rowBody(s)).performTouchInput { longClick() }
        composeRule.onNodeWithText("Remove from History").performClick()
        awaitRows(repo) { it.isEmpty() }
        composeRule.onNodeWithText("Removed from History").assertIsDisplayed()
        composeRule.onNodeWithText("Undo").performClick()
        awaitRows(repo) { it.size == 1 }
        val restored = runBlocking { rowsOf(repo).single() }
        assertEquals(before.starredAt, restored.starredAt)
        assertEquals(32, File(root, restored.pinnedThumbPath!!).length())
        assertEquals(16, File(root, restored.pinnedPfpPath!!).length())
    }

    @Test
    fun talkBack_customActions_matchSpec() {
        val repo = fresh()
        val s = xUrl(1)
        val u = redditUrl(2)
        runBlocking {
            seed(
                repo,
                Seed(s, "Starred", NOW - 3_600_000, starredAt = NOW),
                Seed(u, "Plain", NOW - 7_200_000),
            )
        }
        show(repo)
        val starredActions = composeRule.onNodeWithTag(PaneTestTags.rowBody(s))
            .fetchSemanticsNode().prop(SemanticsActions.CustomActions)
        assertEquals(listOf("Remove star", "Remove from History"), starredActions.map { it.label })
        val plainActions = composeRule.onNodeWithTag(PaneTestTags.rowBody(u))
            .fetchSemanticsNode().prop(SemanticsActions.CustomActions)
        assertEquals(listOf("Star", "Remove from History"), plainActions.map { it.label })
        starredActions.first { it.label == "Remove star" }.action()
        awaitRows(repo) { rows -> rows.first { it.url == s }.starredAt == null && rows.size == 2 }
        val remove = composeRule.onNodeWithTag(PaneTestTags.rowBody(s))
            .fetchSemanticsNode().prop(SemanticsActions.CustomActions)
            .first { it.label == "Remove from History" }
        remove.action()
        awaitRows(repo) { rows -> rows.none { it.url == s } }
    }

    /** Semantics can see a drag one frame before the revealed label is composed. */
    private fun pumpFrames() {
        val was = composeRule.mainClock.autoAdvance
        composeRule.mainClock.autoAdvance = true
        composeRule.waitForIdle()
        composeRule.mainClock.autoAdvance = was
    }

    private fun fresh() = historyRepo(root)

    private fun show(repo: app.pane.android.data.history.SqliteHistoryRepository, initial: HistoryQuery = HistoryQuery()) {
        val vm = historyVm(repo, initial)
        notices.clear()
        opened.clear()
        composeRule.setContent {
            PaneTheme {
                CompositionLocalProvider(LocalHapticFeedback provides haptics) {
                    androidx.compose.runtime.LaunchedEffect(vm) {
                        vm.notice.collect { notices += it }
                    }
                    HistoryRoute(vm, onBack = {}, onOpen = { opened += it })
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun awaitRows(
        repo: app.pane.android.data.history.SqliteHistoryRepository,
        predicate: (List<app.pane.android.domain.model.HistoryEntry>) -> Boolean,
    ) {
        composeRule.waitUntil(timeoutMillis = 3_000) {
            runBlocking { predicate(rowsOf(repo)) }
        }
    }
}
