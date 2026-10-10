package app.pane.android.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.pane.android.domain.model.HistoryGesture
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
import app.pane.android.ui.components.LedgerSwipeKind
import app.pane.android.ui.history.HistoryRoute
import app.pane.android.ui.theme.PaneTheme
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "iw-rIL-ldrtl-w411dp-h891dp-xxhdpi")
class HistoryRtlUiTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun rtl_rowContentFollowsFinger() {
        val row = showOne()
        composeRule.mainClock.autoAdvance = false
        composeRule.onNodeWithTag(PaneTestTags.row(row)).dragRow(0.30f, +1, 1000, release = false)
        val left = composeRule.onNodeWithTag(PaneTestTags.rowBody(row)).fetchSemanticsNode().boundsInRoot.left
        assertTrue("content should move right with the finger", left > 1f)
    }

    @Test
    fun rtl_actionIconOnRevealedEdge() {
        val row = showOne()
        composeRule.mainClock.autoAdvance = false
        composeRule.onNodeWithTag(PaneTestTags.row(row)).dragRow(0.30f, +1, 1000, release = false)
        val icon = composeRule.onAllNodes(androidx.compose.ui.test.hasTestTag(PaneTestTags.SWIPE_ICON), useUnmergedTree = true)[0]
            .fetchSemanticsNode().boundsInRoot
        val host = composeRule.onNodeWithTag(PaneTestTags.row(row)).fetchSemanticsNode().boundsInRoot
        assertTrue(icon.center.x < host.center.x)
    }

    @Test
    fun rtl_directionToAction_mapping() {
        val removeDir = if (LedgerSwipe.removeIsPhysicalLeft(LayoutDirection.Rtl)) -1 else 1
        val starDir = -removeDir
        val root = Files.createTempDirectory("pane-rtl-map").toFile()
        val repo = historyRepo(root)
        val a = xUrl(1)
        val b = xUrl(2)
        runBlocking {
            seed(repo, Seed(a, "Alpha", NOW - 3_600_000), Seed(b, "Beta", NOW - 7_200_000))
        }
        composeRule.setContent {
            PaneTheme {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    HistoryRoute(historyVm(repo), onBack = {}, onOpen = {})
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(PaneTestTags.row(a)).dragRow(LedgerSwipe.COMMIT_FRACTION + 0.02f, removeDir, 800)
        composeRule.waitUntil(3_000) { runBlocking { rowsOf(repo).none { it.url == a } } }
        composeRule.onNodeWithTag(PaneTestTags.row(b)).dragRow(LedgerSwipe.COMMIT_FRACTION + 0.02f, starDir, 800)
        composeRule.waitUntil(3_000) { runBlocking { rowsOf(repo).first { it.url == b }.starredAt != null } }
        assertNotNull(runBlocking { rowsOf(repo).first { it.url == b }.starredAt })
        root.deleteRecursively()
    }

    @Test
    fun rtl_chipsOrder_mirrored() {
        val root = Files.createTempDirectory("pane-rtl-chips").toFile()
        val repo = historyRepo(root)
        runBlocking { seed(repo, Seed(xUrl(1), "Alpha", NOW - 3_600_000)) }
        composeRule.setContent {
            PaneTheme {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    HistoryRoute(historyVm(repo), onBack = {}, onOpen = {})
                }
            }
        }
        composeRule.waitForIdle()
        val all = composeRule.onNodeWithTag(PaneTestTags.CHIP_ALL).fetchSemanticsNode().boundsInRoot.left
        val starred = composeRule.onNodeWithTag(PaneTestTags.CHIP_STARRED).fetchSemanticsNode().boundsInRoot.left
        val filters = composeRule.onNodeWithTag(PaneTestTags.CHIP_FILTERS).fetchSemanticsNode().boundsInRoot.left
        assertTrue(all > starred)
        assertTrue(starred > filters)
        root.deleteRecursively()
    }

    @Test
    fun ltr_hebrewTitle_alignsLeft() {
        val url = xUrl(8)
        showTitle(url, "שלום עולם @user", LayoutDirection.Ltr, rtlConfig = false)
        val title = composeRule.onNode(hasText("שלום עולם @user", substring = true)).fetchSemanticsNode().boundsInRoot
        assertTrue(title.left < 120f)
    }

    @Test
    fun rtl_latinTitle_alignsRight() {
        val url = xUrl(9)
        showTitle(url, "Hello world", LayoutDirection.Rtl, rtlConfig = true)
        val title = composeRule.onNodeWithText("Hello world", substring = true).fetchSemanticsNode().boundsInRoot
        val row = composeRule.onNodeWithTag(PaneTestTags.row(url)).fetchSemanticsNode().boundsInRoot
        assertTrue(title.right > row.right - 400f)
        assertTrue(title.right <= row.right)
    }

    @Test
    fun rtl_metaLine_isolatesHandleAndTime() {
        val url = xUrl(10)
        val root = Files.createTempDirectory("pane-rtl-meta").toFile()
        val repo = historyRepo(root)
        runBlocking { seed(repo, Seed(url, "Hello", NOW - 2 * 3_600_000, handle = "maya")) }
        composeRule.setContent {
            PaneTheme {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    HistoryRoute(historyVm(repo), onBack = {}, onOpen = {})
                }
            }
        }
        composeRule.waitForIdle()
        val body = composeRule.onNodeWithTag(PaneTestTags.rowBody(url)).fetchSemanticsNode()
        val description = body.prop(SemanticsProperties.ContentDescription).joinToString()
        assertTrue(description.contains("Hello"))
        assertTrue(description.contains("@maya"))
        assertTrue(description.contains("2h"))
        composeRule.onNode(hasText("\u2068@maya\u2069", substring = true)).assertIsDisplayed()
        root.deleteRecursively()
    }

    @Test
    fun rtl_midGesture_kindFollowsConstant() {
        val row = showOne()
        val removeDir = if (LedgerSwipe.removeIsPhysicalLeft(LayoutDirection.Rtl)) -1 else 1
        composeRule.mainClock.autoAdvance = false
        composeRule.onNodeWithTag(PaneTestTags.row(row)).dragRow(0.50f, removeDir, 1000, release = false)
        assertEquals(
            HistoryGesture.Remove,
            composeRule.onNodeWithTag(PaneTestTags.row(row)).fetchSemanticsNode().prop(LedgerSwipeKind),
        )
    }

    private fun showOne(): String {
        val root = Files.createTempDirectory("pane-rtl").toFile()
        val repo = historyRepo(root)
        val a = xUrl(1)
        runBlocking { seed(repo, Seed(a, "Alpha", NOW - 3_600_000), Seed(xUrl(2), "Beta", NOW - 7_200_000)) }
        composeRule.setContent {
            PaneTheme {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    HistoryRoute(historyVm(repo), onBack = {}, onOpen = {})
                }
            }
        }
        composeRule.waitForIdle()
        return a
    }

    private fun showTitle(url: String, title: String, direction: LayoutDirection, rtlConfig: Boolean) {
        val root = Files.createTempDirectory("pane-rtl-title").toFile()
        val repo = historyRepo(root)
        runBlocking { seed(repo, Seed(url, title, NOW - 3_600_000)) }
        composeRule.setContent {
            PaneTheme {
                CompositionLocalProvider(LocalLayoutDirection provides direction) {
                    HistoryRoute(historyVm(repo), onBack = {}, onOpen = {})
                }
            }
        }
        composeRule.waitForIdle()
        assertEquals(rtlConfig, direction == LayoutDirection.Rtl)
    }
}
