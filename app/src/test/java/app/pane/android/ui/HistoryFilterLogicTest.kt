package app.pane.android.ui

import app.pane.android.MainDispatcherRule
import app.pane.android.data.history.HistoryPreferenceCodec
import app.pane.android.data.history.MemoryHistoryPreferences
import app.pane.android.domain.model.HistoryEmptyKind
import app.pane.android.domain.model.HistoryFilterCatalog
import app.pane.android.domain.model.HistoryLedger
import app.pane.android.domain.model.HistoryQuery
import app.pane.android.domain.model.HistoryScope
import app.pane.android.domain.model.SourceApp
import app.pane.android.testing.MutableClock
import app.pane.android.testing.NOW
import app.pane.android.testing.Seed
import app.pane.android.testing.historyRepo
import app.pane.android.testing.historyVm
import app.pane.android.testing.redditUrl
import app.pane.android.testing.seedOn
import app.pane.android.testing.tiktokUrl
import app.pane.android.testing.xUrl
import app.pane.android.testing.youtubeUrl
import app.pane.android.ui.history.HistoryViewModel
import java.nio.file.Files
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class HistoryFilterLogicTest {
    @get:Rule val main = MainDispatcherRule()

    @Test
    fun tapAll_clearsEverything() = runTest(main.dispatcher) {
        val vm = vm(HistoryQuery(HistoryScope.Starred, setOf(SourceApp.X)))
        watch(vm)
        vm.showAll()
        advanceUntilIdle()
        assertEquals(HistoryQuery(), vm.uiState.value.let { HistoryQuery(if (it.scopeStarred) HistoryScope.Starred else HistoryScope.All, it.selectedApps) })
        assertTrue(!vm.uiState.value.scopeStarred)
        assertNull(vm.uiState.value.selectedApp)
    }

    @Test
    fun tapAll_whenAllOn_isNoOp_andDoesNotPersist() = runTest(main.dispatcher) {
        val persisted = mutableListOf<HistoryQuery>()
        val vm = vm(HistoryQuery(), persisted)
        watch(vm)
        val before = persisted.size
        vm.showAll()
        advanceUntilIdle()
        assertEquals(before, persisted.size)
        assertNull(vm.uiState.value.selectedApp)
    }

    @Test
    fun tapStarred_toggles_keepsApp() = runTest(main.dispatcher) {
        val vm = vm(HistoryQuery(apps = setOf(SourceApp.X)))
        watch(vm)
        vm.toggleStarred()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.scopeStarred)
        assertEquals(SourceApp.X, vm.uiState.value.selectedApp)
        vm.toggleStarred()
        advanceUntilIdle()
        assertTrue(!vm.uiState.value.scopeStarred)
        assertEquals(SourceApp.X, vm.uiState.value.selectedApp)
    }

    @Test
    fun sheet_stageThenApply_setsSingleApp() = runTest(main.dispatcher) {
        val vm = vm()
        watch(vm)
        vm.selectApp(SourceApp.X)
        vm.selectApp(SourceApp.Reddit)
        advanceUntilIdle()
        assertEquals(SourceApp.Reddit, vm.uiState.value.selectedApp)
        assertEquals(setOf(SourceApp.Reddit), vm.uiState.value.selectedApps)
    }

    @Test
    fun sheet_tileTapAgain_unstages() = runTest(main.dispatcher) {
        val vm = vm(HistoryQuery(apps = setOf(SourceApp.X)))
        watch(vm)
        vm.selectApp(null)
        advanceUntilIdle()
        assertNull(vm.uiState.value.selectedApp)
    }

    @Test
    fun sheet_dismiss_discardsStaged() {
        val persisted = mutableListOf<HistoryQuery>()
        val before = persisted.toList()
        assertEquals(before, persisted)
    }

    @Test
    fun clearAppChip_clearsApp_allWhenStarredOff() = runTest(main.dispatcher) {
        val vm = vm(HistoryQuery(apps = setOf(SourceApp.X)))
        watch(vm)
        vm.selectApp(null)
        advanceUntilIdle()
        assertNull(vm.uiState.value.selectedApp)
        assertTrue(!vm.uiState.value.scopeStarred)
        val starred = vm(HistoryQuery(HistoryScope.Starred, setOf(SourceApp.X)))
        watch(starred)
        starred.selectApp(null)
        advanceUntilIdle()
        assertTrue(starred.uiState.value.scopeStarred)
        assertNull(starred.uiState.value.selectedApp)
    }

    @Test
    fun restore_rememberedAppWithNoRows_fallsBack() = runTest(main.dispatcher) {
        val root = Files.createTempDirectory("pane-fc0").toFile()
        val clock = MutableClock()
        val repo = historyRepo(root, clock)
        seedOn(repo, clock, Seed(xUrl(1), "X", NOW - 1_000))
        val persisted = mutableListOf<HistoryQuery>()
        val starred = historyVm(repo, HistoryQuery(HistoryScope.Starred, setOf(SourceApp.TikTok)), persisted)
        watch(starred)
        assertTrue(starred.uiState.value.scopeStarred)
        assertNull(starred.uiState.value.selectedApp)
        val all = historyVm(repo, HistoryQuery(apps = setOf(SourceApp.TikTok)), persisted)
        watch(all)
        assertTrue(!all.uiState.value.scopeStarred)
        assertNull(all.uiState.value.selectedApp)
        root.deleteRecursively()
    }

    @Test
    fun legacyPrefs_multiApp_migrates() {
        val prefs = MemoryHistoryPreferences()
        prefs.seedLegacy("all", "X,Reddit")
        val query = prefs.readFilter()
        assertTrue(query.apps.isEmpty())
        assertEquals(HistoryQuery(), HistoryPreferenceCodec.migrateLegacy("all"))
    }

    @Test
    fun sheetCounts_followStarred() = runTest(main.dispatcher) {
        val root = Files.createTempDirectory("pane-counts").toFile()
        val clock = MutableClock()
        val repo = historyRepo(root, clock)
        seedOn(
            repo,
            clock,
            Seed(xUrl(1), "X1", NOW - 3_000, starredAt = NOW),
            Seed(xUrl(2), "X2", NOW - 2_000),
            Seed(xUrl(3), "X3", NOW - 1_000),
            Seed(redditUrl(1), "R1", NOW - 4_000),
            Seed(redditUrl(2), "R2", NOW - 5_000),
        )
        val rows = repo.observeHistory().first()
        val all = HistoryFilterCatalog.counts(rows, HistoryScope.All).associate { it.app to it.count }
        val starred = HistoryFilterCatalog.counts(rows, HistoryScope.Starred).associate { it.app to it.count }
        assertEquals(3, all[SourceApp.X])
        assertEquals(2, all[SourceApp.Reddit])
        assertEquals(1, starred[SourceApp.X])
        assertEquals(0, starred[SourceApp.Reddit])
        root.deleteRecursively()
    }

    @Test
    fun showCount_matchesStagedSelection() = runTest(main.dispatcher) {
        val root = Files.createTempDirectory("pane-show").toFile()
        val clock = MutableClock()
        val repo = historyRepo(root, clock)
        seedOn(
            repo,
            clock,
            Seed(xUrl(1), "X", NOW, starredAt = NOW),
            Seed(redditUrl(1), "R", NOW - 1_000),
        )
        val rows = repo.observeHistory().first()
        assertEquals(1, HistoryFilterCatalog.counts(rows, HistoryScope.Starred).first { it.app == SourceApp.X }.count)
        assertEquals(1, HistoryFilterCatalog.scopeCount(rows, HistoryScope.Starred))
        root.deleteRecursively()
    }

    @Test
    fun starredAfterApp_keepsAppEvenWithZeroStarred() = runTest(main.dispatcher) {
        val root = Files.createTempDirectory("pane-keep").toFile()
        val clock = MutableClock()
        val repo = historyRepo(root, clock)
        seedOn(repo, clock, Seed(redditUrl(1), "R", NOW - 1_000))
        val vm = historyVm(repo, HistoryQuery(apps = setOf(SourceApp.Reddit)))
        watch(vm)
        vm.toggleStarred()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.scopeStarred)
        assertEquals(SourceApp.Reddit, vm.uiState.value.selectedApp)
        assertEquals(HistoryEmptyKind.StarredApp, vm.uiState.value.empty)
        root.deleteRecursively()
    }

    private fun vm(
        initial: HistoryQuery = HistoryQuery(),
        persisted: MutableList<HistoryQuery> = mutableListOf(),
    ): HistoryViewModel {
        val root = Files.createTempDirectory("pane-logic").toFile()
        val repo = historyRepo(root)
        return historyVm(repo, initial, persisted)
    }

    private fun TestScope.watch(vm: HistoryViewModel) {
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()
    }
}
