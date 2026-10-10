package app.pane.android.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.pane.android.data.fixture.FixtureCatalog
import app.pane.android.data.fixture.FixtureLinkContentRepository
import app.pane.android.data.history.HistoryUrls
import app.pane.android.domain.model.Clock
import app.pane.android.domain.model.RecentLink
import app.pane.android.domain.model.StarCopy
import app.pane.android.domain.usecase.LoadMoreCommentsUseCase
import app.pane.android.domain.usecase.ObserveRecentContentUseCase
import app.pane.android.domain.usecase.OpenLinkUseCase
import app.pane.android.domain.usecase.RefreshLinkUseCase
import app.pane.android.testing.FakeRecentLinksRepository
import app.pane.android.testing.NOW
import app.pane.android.testing.dragRow
import app.pane.android.testing.historyRepo
import app.pane.android.testing.historyVm
import app.pane.android.testing.prop
import app.pane.android.testing.rowsOf
import app.pane.android.ui.components.LedgerSwipe
import app.pane.android.ui.components.PaneSnackbarHost
import app.pane.android.ui.components.showForFiveSeconds
import app.pane.android.ui.history.HistoryRoute
import app.pane.android.ui.home.HomeView
import app.pane.android.ui.home.HomeViewModel
import app.pane.android.ui.home.HubNotice
import app.pane.android.ui.mapper.HomeUiMapper
import app.pane.android.ui.mapper.UiImageMapper
import app.pane.android.ui.mapper.ViewerUiMapper
import app.pane.android.ui.model.ViewerUiState
import app.pane.android.ui.theme.PaneTheme
import app.pane.android.ui.viewer.ViewerTopBar
import app.pane.android.ui.viewer.ViewerViewModel
import java.nio.file.Files
import java.time.ZoneOffset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class HistoryStarSyncUiTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun viewerStar_reflectsInHistoryAndHub() {
        val harness = harness()
        composeRule.waitUntil(5_000) {
            harness.viewer.uiState.value is ViewerUiState.Content
        }
        composeRule.onNodeWithTag(PaneTestTags.VIEWER_STAR).performClick()
        composeRule.waitUntil(3_000) {
            runBlocking { rowsOf(harness.repo).single().starredAt != null }
        }
        assertEquals("Starred", stateOf(PaneTestTags.VIEWER_STAR))
        assertEquals("Starred", stateOf(PaneTestTags.rowBody(harness.canon)))
        composeRule.onNodeWithText("Starred. It'll be in History when you want it.").assertExists()
        composeRule.onNodeWithTag(PaneTestTags.CHIP_STARRED).performClick()
        surfaceRow(harness.canon, hub = false).assertExists()
        harness.root.deleteRecursively()
    }

    @Test
    fun historyUnstar_reflectsInViewerAndHub() {
        val harness = harness()
        composeRule.waitUntil(5_000) { harness.viewer.uiState.value is ViewerUiState.Content }
        composeRule.onNodeWithTag(PaneTestTags.VIEWER_STAR).performClick()
        composeRule.waitUntil(3_000) { runBlocking { rowsOf(harness.repo).single().starredAt != null } }
        surfaceRow(harness.canon, hub = false).dragRow(LedgerSwipe.COMMIT_FRACTION + 0.02f, -1, 800)
        composeRule.waitUntil(3_000) { runBlocking { rowsOf(harness.repo).single().starredAt == null } }
        assertEquals("Not starred", stateOf(PaneTestTags.VIEWER_STAR))
        assertEquals("Not starred", stateOf(PaneTestTags.rowBody(harness.canon)))
        harness.root.deleteRecursively()
    }

    @Test
    fun hubStar_reflectsInViewerAndHistory() {
        val harness = harness()
        composeRule.waitUntil(5_000) { harness.viewer.uiState.value is ViewerUiState.Content }
        surfaceRow(harness.canon, hub = true).dragRow(LedgerSwipe.COMMIT_FRACTION + 0.02f, +1, 800)
        composeRule.waitUntil(3_000) { runBlocking { rowsOf(harness.repo).single().starredAt != null } }
        assertEquals("Starred", stateOf(PaneTestTags.VIEWER_STAR))
        assertEquals("Starred", stateOf(PaneTestTags.rowBody(harness.canon)))
        harness.root.deleteRecursively()
    }

    @Test
    fun undoOnOneSurface_propagates() {
        val harness = harness()
        composeRule.waitUntil(5_000) { harness.viewer.uiState.value is ViewerUiState.Content }
        composeRule.onNodeWithTag(PaneTestTags.VIEWER_STAR).performClick()
        composeRule.waitUntil(3_000) { runBlocking { rowsOf(harness.repo).single().starredAt != null } }
        surfaceRow(harness.canon, hub = false).dragRow(LedgerSwipe.COMMIT_FRACTION + 0.02f, -1, 800)
        composeRule.waitUntil(3_000) { runBlocking { rowsOf(harness.repo).single().starredAt == null } }
        composeRule.onNodeWithText("Undo").performClick()
        composeRule.waitUntil(3_000) { runBlocking { rowsOf(harness.repo).single().starredAt != null } }
        assertEquals("Starred", stateOf(PaneTestTags.VIEWER_STAR))
        harness.root.deleteRecursively()
    }

    @Test
    fun canonicalUrl_variant_staysInSync() {
        val root = Files.createTempDirectory("pane-sy7").toFile()
        val repo = historyRepo(root)
        val canon = HistoryUrls.canonical(FixtureCatalog.KYOTO_URL)
        val variant = "https://m.instagram.com/reel/peek-kyoto/?utm_source=pane"
        val recents = FakeRecentLinksRepository(listOf(RecentLink(canon, NOW - 60_000)))
        val content = object : app.pane.android.domain.repository.LinkContentRepository {
            private val inner = FixtureLinkContentRepository()
            override suspend fun resolve(url: String) = inner.resolve(FixtureCatalog.KYOTO_URL)
            override suspend fun peekCached(url: String) = inner.peekCached(FixtureCatalog.KYOTO_URL)
            override suspend fun refresh(url: String) = inner.refresh(FixtureCatalog.KYOTO_URL)
        }
        val viewer = ViewerViewModel(
            url = variant,
            openLink = OpenLinkUseCase(content, recents, repo, Clock { NOW }),
            refreshLink = RefreshLinkUseCase(content, recents),
            loadMoreComments = LoadMoreCommentsUseCase(content),
            mapper = ViewerUiMapper(UiImageMapper()),
            historyRepository = repo,
        )
        val home = homeVm(repo, recents, content)
        val history = historyVm(repo)
        composeRule.setContent {
            val viewerState by viewer.uiState.collectAsStateWithLifecycle()
            val homeState by home.uiState.collectAsStateWithLifecycle()
            val historyState by history.uiState.collectAsStateWithLifecycle()
            PaneTheme {
                Column {
                    ViewerTopBar(
                        onDone = {},
                        onRefresh = {},
                        overMedia = false,
                        starred = (viewerState as? ViewerUiState.Content)?.starred == true,
                        onStar = viewer::onToggleStar,
                    )
                    HomeView(homeState, {}, {}, onSwipeRecent = home::swipe, modifier = Modifier.height(280.dp))
                    app.pane.android.ui.history.HistoryView(
                        ui = historyState,
                        onBack = {},
                        onOpen = {},
                        onShowAll = history::showAll,
                        onToggleStarred = history::toggleStarred,
                        onOpenFilters = {},
                        onDismissFilters = {},
                        onApplyApp = {},
                        onClearApp = {},
                        onSwipe = history::swipe,
                        onRemove = {},
                        onLongPress = {},
                        modifier = Modifier.height(360.dp),
                    )
                }
            }
        }
        composeRule.waitUntil(5_000) { viewer.uiState.value is ViewerUiState.Content }
        composeRule.onNodeWithTag(PaneTestTags.VIEWER_STAR).performClick()
        composeRule.waitUntil(3_000) { runBlocking { rowsOf(repo).any { it.starredAt != null } } }
        assertEquals(canon, runBlocking { rowsOf(repo).single().url })
        assertEquals("Starred", stateOf(PaneTestTags.rowBody(canon)))
        root.deleteRecursively()
    }

    @Test
    fun hubRecents_leftSwipeOnStarred_removesFromRecentsOnly() {
        val root = Files.createTempDirectory("pane-sf8").toFile()
        val repo = historyRepo(root)
        val url = HistoryUrls.canonical(FixtureCatalog.KYOTO_URL)
        val recents = FakeRecentLinksRepository(listOf(RecentLink(url, NOW - 60_000)))
        val content = FixtureLinkContentRepository()
        runBlocking {
            OpenLinkUseCase(content, recents, repo, Clock { NOW })(url)
            repo.star(url, StarCopy("Kyoto", "Ada", "ada", "Kyoto"))
        }
        val home = homeVm(repo, recents, content)
        val snackbar = SnackbarHostState()
        composeRule.setContent {
            val state by home.uiState.collectAsStateWithLifecycle()
            PaneTheme {
                Column {
                    HomeView(state, {}, {}, onSwipeRecent = home::swipe, modifier = Modifier.height(520.dp))
                    PaneSnackbarHost(snackbar)
                }
            }
            LaunchedEffect(home) {
                home.notice.collect { notice ->
                    if (notice is HubNotice.Removed) {
                        snackbar.showForFiveSeconds("Removed from Recents", "Undo") {}
                    }
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(PaneTestTags.row(url)).dragRow(LedgerSwipe.COMMIT_FRACTION + 0.02f, -1, 800)
        composeRule.waitUntil(3_000) { runBlocking { recents.observeRecents().first().none { it.url == url } } }
        composeRule.onNodeWithText("Removed from Recents").assertExists()
        assertNotNull(runBlocking { rowsOf(repo).single().starredAt })
        root.deleteRecursively()
    }

    @Test
    fun viewerUnstarUndo_updatesCapsule() {
        val harness = harness()
        composeRule.waitUntil(5_000) { harness.viewer.uiState.value is ViewerUiState.Content }
        runBlocking { harness.repo.star(harness.canon, StarCopy("Kyoto", "Ada", "ada", "Kyoto")) }
        composeRule.waitUntil(3_000) { (harness.viewer.uiState.value as? ViewerUiState.Content)?.starred == true }
        composeRule.onNodeWithTag(PaneTestTags.VIEWER_STAR).performClick()
        composeRule.waitUntil(3_000) { (harness.viewer.uiState.value as? ViewerUiState.Content)?.starred == false }
        composeRule.onNodeWithText("Undo").performClick()
        composeRule.waitUntil(3_000) { (harness.viewer.uiState.value as? ViewerUiState.Content)?.starred == true }
        assertEquals("Starred", stateOf(PaneTestTags.VIEWER_STAR))
        harness.root.deleteRecursively()
    }

    private fun stateOf(tag: String): String {
        val nodes = composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes()
        assertTrue(nodes.isNotEmpty())
        val states = nodes.map { it.prop(SemanticsProperties.StateDescription) }.toSet()
        assertEquals(setOf(states.first()), states)
        return states.first()
    }

    /** Hub and History both tag the same URL. Pick the one under that surface. */
    private fun surfaceRow(url: String, hub: Boolean): SemanticsNodeInteraction {
        val ancestor = if (hub) PaneTestTags.RECENTS else PaneTestTags.HISTORY_LIST
        return composeRule.onNode(
            hasTestTag(PaneTestTags.row(url)) and hasAnyAncestor(hasTestTag(ancestor)),
            useUnmergedTree = true,
        )
    }

    private fun homeVm(
        repo: app.pane.android.data.history.SqliteHistoryRepository,
        recents: FakeRecentLinksRepository,
        content: app.pane.android.domain.repository.LinkContentRepository,
    ) = HomeViewModel(
        ObserveRecentContentUseCase(content, recents),
        HomeUiMapper(UiImageMapper(), Clock { NOW }, ZoneOffset.UTC),
        recents,
        repo,
    )

    private fun harness(): Harness {
        val root = Files.createTempDirectory("pane-sync").toFile()
        val repo = historyRepo(root)
        val canon = HistoryUrls.canonical(FixtureCatalog.KYOTO_URL)
        val recents = FakeRecentLinksRepository(listOf(RecentLink(canon, NOW - 60_000)))
        val content = FixtureLinkContentRepository()
        val viewer = ViewerViewModel(
            url = canon,
            openLink = OpenLinkUseCase(content, recents, repo, Clock { NOW }),
            refreshLink = RefreshLinkUseCase(content, recents),
            loadMoreComments = LoadMoreCommentsUseCase(content),
            mapper = ViewerUiMapper(UiImageMapper()),
            historyRepository = repo,
        )
        val home = homeVm(repo, recents, content)
        val history = historyVm(repo)
        val snackbar = SnackbarHostState()
        composeRule.setContent {
            val viewerState by viewer.uiState.collectAsStateWithLifecycle()
            val homeState by home.uiState.collectAsStateWithLifecycle()
            val historyState by history.uiState.collectAsStateWithLifecycle()
            PaneTheme {
                Column {
                    ViewerTopBar(
                        onDone = {},
                        onRefresh = {},
                        overMedia = false,
                        starred = (viewerState as? ViewerUiState.Content)?.starred == true,
                        onStar = viewer::onToggleStar,
                    )
                    PaneSnackbarHost(snackbar)
                    HomeView(homeState, {}, {}, onSwipeRecent = home::swipe, modifier = Modifier.height(240.dp))
                    app.pane.android.ui.history.HistoryView(
                        ui = historyState,
                        onBack = {},
                        onOpen = {},
                        onShowAll = history::showAll,
                        onToggleStarred = history::toggleStarred,
                        onOpenFilters = {},
                        onDismissFilters = {},
                        onApplyApp = history::selectApp,
                        onClearApp = { history.selectApp(null) },
                        onSwipe = history::swipe,
                        onRemove = {},
                        onLongPress = {},
                        modifier = Modifier.height(420.dp),
                    )
                }
            }
            LaunchedEffect(viewer) {
                viewer.notice.collect { notice ->
                    when (notice) {
                        app.pane.android.ui.viewer.ViewerNotice.Starred ->
                            snackbar.showForFiveSeconds("Starred. It'll be in History when you want it.")
                        is app.pane.android.ui.viewer.ViewerNotice.Unstarred ->
                            snackbar.showForFiveSeconds("Star removed", "Undo") { viewer.undoStar(notice.undo) }
                    }
                }
            }
            LaunchedEffect(history) {
                history.notice.collect { notice ->
                    when (notice) {
                        app.pane.android.ui.history.HistoryNotice.Starred ->
                            snackbar.showForFiveSeconds("Starred. It'll be in History when you want it.")
                        is app.pane.android.ui.history.HistoryNotice.Unstarred ->
                            snackbar.showForFiveSeconds("Star removed", "Undo") { history.undo(notice.undo) }
                        is app.pane.android.ui.history.HistoryNotice.Removed ->
                            snackbar.showForFiveSeconds("Removed from History", "Undo") { history.undo(notice.undo) }
                    }
                }
            }
        }
        return Harness(root, repo, canon, viewer)
    }

    private class Harness(
        val root: java.io.File,
        val repo: app.pane.android.data.history.SqliteHistoryRepository,
        val canon: String,
        val viewer: ViewerViewModel,
    )
}
