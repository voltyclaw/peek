package app.pane.android.ui

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onChildren
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.semantics.SemanticsProperties
import app.pane.android.domain.model.HistoryQuery
import app.pane.android.domain.model.HistoryScope
import app.pane.android.domain.model.StarImageBytes
import app.pane.android.testing.NOW
import app.pane.android.testing.Seed
import app.pane.android.testing.dragRow
import app.pane.android.testing.historyRepo
import app.pane.android.testing.historyVm
import app.pane.android.testing.prop
import app.pane.android.testing.rowsOf
import app.pane.android.testing.seed
import app.pane.android.testing.xUrl
import app.pane.android.ui.components.LedgerSwipe
import app.pane.android.ui.history.HistoryRoute
import app.pane.android.ui.theme.PaneTheme
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class HistoryUndoUiTest {
    @get:Rule val composeRule = createComposeRule()
    private lateinit var root: File

    @Before fun setUp() { root = Files.createTempDirectory("pane-undo").toFile() }
    @After fun tearDown() { root.deleteRecursively() }

    @Test
    fun undoRemove_restoresRowInPlace() {
        val repo = historyRepo(root)
        val a = xUrl(1)
        val b = xUrl(2)
        val c = xUrl(3)
        val hour = 3_600_000L
        runBlocking {
            seed(
                repo,
                Seed(a, "A", NOW - hour),
                Seed(b, "B", NOW - 2 * hour),
                Seed(c, "C", NOW - 26 * hour),
            )
        }
        val before = runBlocking { rowsOf(repo).first { it.url == b } }
        show(repo)
        composeRule.onNodeWithTag(PaneTestTags.row(b)).dragRow(LedgerSwipe.COMMIT_FRACTION + 0.02f, -1, 800)
        await(repo) { rows -> rows.none { it.url == b } }
        composeRule.onNodeWithText("Undo").performClick()
        await(repo) { rows -> rows.any { it.url == b } }
        val urls = runBlocking { rowsOf(repo).sortedByDescending { it.lastViewedAt }.map { it.url } }
        assertEquals(listOf(a, b, c), urls)
        val restored = runBlocking { rowsOf(repo).first { it.url == b } }
        assertEquals(before.lastViewedAt, restored.lastViewedAt)
        assertEquals(before.viewCount, restored.viewCount)
        assertEquals(before.firstViewedAt, restored.firstViewedAt)
        composeRule.onNodeWithTag(PaneTestTags.SNACKBAR).onChildren().assertCountEquals(0)
    }

    @Test
    fun undoUnstar_restoresStarAndPinnedImages() {
        val repo = historyRepo(root)
        val s = xUrl(1)
        val images = StarImageBytes(ByteArray(32) { 3 }, ByteArray(16) { 4 })
        runBlocking { seed(repo, Seed(s, "S", NOW - 3_600_000, starredAt = NOW, images = images)) }
        val before = runBlocking { rowsOf(repo).single() }
        show(repo)
        composeRule.onNodeWithTag(PaneTestTags.row(s)).dragRow(LedgerSwipe.COMMIT_FRACTION + 0.02f, -1, 800)
        await(repo) { rows -> rows.single().starredAt == null }
        composeRule.onNodeWithText("Undo").performClick()
        await(repo) { rows -> rows.single().starredAt != null }
        val restored = runBlocking { rowsOf(repo).single() }
        assertEquals(before.starredAt, restored.starredAt)
        assertEquals(32, File(root, restored.pinnedThumbPath!!).length())
        assertEquals(16, File(root, restored.pinnedPfpPath!!).length())
        assertEquals(
            "Starred",
            composeRule.onNodeWithTag(PaneTestTags.rowBody(s)).fetchSemanticsNode().prop(SemanticsProperties.StateDescription),
        )
    }

    @Test
    fun undoWindow_isFiveSeconds() {
        val repo = historyRepo(root)
        val a = xUrl(1)
        runBlocking { seed(repo, Seed(a, "A", NOW - 3_600_000), Seed(xUrl(2), "B", NOW - 7_200_000)) }
        show(repo)
        composeRule.mainClock.autoAdvance = false
        composeRule.onNodeWithTag(PaneTestTags.row(a)).dragRow(LedgerSwipe.COMMIT_FRACTION + 0.02f, -1, 800)
        // Pointer-up starts the 5s window. 4.9s later Undo is still up; shortly after, it is gone.
        composeRule.mainClock.advanceTimeBy(4_900)
        composeRule.waitForIdle()
        await(repo) { rows -> rows.none { it.url == a } }
        composeRule.onNodeWithText("Undo").assertIsDisplayed()
        composeRule.mainClock.advanceTimeBy(400)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(PaneTestTags.SNACKBAR).onChildren().assertCountEquals(0)
        assertEquals(1, runBlocking { rowsOf(repo).size })
    }

    @Test
    fun rapidRemovals_allCommitImmediately_andUndoTargetsShownRow() {
        val repo = historyRepo(root)
        val urls = listOf(xUrl(1), xUrl(2), xUrl(3), xUrl(4))
        runBlocking {
            seed(repo, *urls.mapIndexed { index, url -> Seed(url, "R$index", NOW - index * 60_000L) }.toTypedArray())
        }
        show(repo)
        urls.forEach { url ->
            composeRule.onNodeWithTag(PaneTestTags.row(url)).dragRow(LedgerSwipe.COMMIT_FRACTION + 0.02f, -1, 200)
            composeRule.waitForIdle()
        }
        composeRule.waitUntil(timeoutMillis = 500) {
            runBlocking { rowsOf(repo).none { it.url in urls } }
        }
        composeRule.onNodeWithText("Undo").performClick()
        await(repo) { rows -> rows.size == 1 }
        assertEquals(urls.first(), runBlocking { rowsOf(repo).single().url })
    }

    @Test
    fun undoAfterLeavingScreen_isNotOffered() {
        val repo = historyRepo(root)
        val a = xUrl(1)
        var back = false
        runBlocking { seed(repo, Seed(a, "A", NOW - 3_600_000)) }
        val shown = mutableStateOf(historyVm(repo))
        composeRule.setContent {
            PaneTheme { HistoryRoute(shown.value, onBack = { back = true }, onOpen = {}) }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(PaneTestTags.row(a)).dragRow(LedgerSwipe.COMMIT_FRACTION + 0.02f, -1, 600)
        await(repo) { it.isEmpty() }
        composeRule.onNodeWithContentDescription("Back").performClick()
        assertEquals(true, back)
        composeRule.runOnIdle { shown.value = historyVm(repo) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(PaneTestTags.SNACKBAR).onChildren().assertCountEquals(0)
        assertNull(runBlocking { rowsOf(repo).find { it.url == a } })
    }

    @Test
    fun undoUnstar_onStarredChip_reappears() {
        val repo = historyRepo(root)
        val s = xUrl(1)
        runBlocking { seed(repo, Seed(s, "S", NOW - 3_600_000, starredAt = NOW)) }
        show(repo, HistoryQuery(scope = HistoryScope.Starred))
        composeRule.onNodeWithTag(PaneTestTags.row(s)).dragRow(LedgerSwipe.COMMIT_FRACTION + 0.02f, -1, 800)
        await(repo) { rows -> rows.single().starredAt == null }
        composeRule.onNodeWithTag(PaneTestTags.row(s)).assertDoesNotExist()
        composeRule.onNodeWithText("Undo").performClick()
        await(repo) { rows -> rows.single().starredAt != null }
        composeRule.onNodeWithTag(PaneTestTags.row(s)).assertExists()
    }

    private fun show(
        repo: app.pane.android.data.history.SqliteHistoryRepository,
        initial: HistoryQuery = HistoryQuery(),
    ) {
        composeRule.setContent {
            PaneTheme { HistoryRoute(historyVm(repo, initial), onBack = {}, onOpen = {}) }
        }
        composeRule.waitForIdle()
    }

    private fun await(
        repo: app.pane.android.data.history.SqliteHistoryRepository,
        predicate: (List<app.pane.android.domain.model.HistoryEntry>) -> Boolean,
    ) {
        composeRule.waitUntil(3_000) { runBlocking { predicate(rowsOf(repo)) } }
    }
}
