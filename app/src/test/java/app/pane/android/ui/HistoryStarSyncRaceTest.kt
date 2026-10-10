package app.pane.android.ui

import app.pane.android.MainDispatcherRule
import app.pane.android.data.fixture.FixtureCatalog
import app.pane.android.data.fixture.FixtureLinkContentRepository
import app.pane.android.data.history.HistoryUrls
import app.pane.android.domain.model.Clock
import app.pane.android.domain.model.RecentLink
import app.pane.android.domain.model.StarCopy
import app.pane.android.domain.usecase.LoadMoreCommentsUseCase
import app.pane.android.domain.usecase.OpenLinkUseCase
import app.pane.android.domain.usecase.RefreshLinkUseCase
import app.pane.android.testing.FakeRecentLinksRepository
import app.pane.android.testing.NOW
import app.pane.android.testing.historyRepo
import app.pane.android.testing.historyVm
import app.pane.android.ui.mapper.UiImageMapper
import app.pane.android.ui.mapper.ViewerUiMapper
import app.pane.android.ui.viewer.ViewerViewModel
import java.nio.file.Files
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class HistoryStarSyncRaceTest {
    @get:Rule val main = MainDispatcherRule()

    @Test
    fun concurrentToggle_neverRemovesRow() = runTest(main.dispatcher) {
        val root = Files.createTempDirectory("pane-sy5").toFile()
        val repo = historyRepo(root, io = main.dispatcher)
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
        val history = historyVm(repo)
        advanceUntilIdle()
        repo.star(canon, StarCopy("Kyoto", "Ada", "ada", "Kyoto"))
        advanceUntilIdle()
        assertNotNull(repo.observeHistory().first().single().starredAt)
        viewer.onToggleStar()
        history.swipe(canon, left = true)
        advanceUntilIdle()
        val rows = repo.observeHistory().first()
        assertEquals(1, rows.size)
        assertNull(rows.single().starredAt)
        root.deleteRecursively()
    }
}
