package app.pane.android

import app.pane.android.data.fixture.FixtureCatalog
import app.pane.android.data.fixture.FixtureLinkContentRepository
import app.pane.android.domain.model.Clock
import app.pane.android.domain.model.LinkContent
import app.pane.android.domain.model.LoadProgress
import app.pane.android.domain.model.LoadStage
import app.pane.android.domain.model.RecentLink
import app.pane.android.domain.repository.LinkContentRepository
import app.pane.android.domain.repository.LoadProgressListener
import app.pane.android.domain.repository.RecentLinksRepository
import app.pane.android.testing.FakeRecentLinksRepository
import app.pane.android.domain.usecase.ObserveRecentContentUseCase
import app.pane.android.domain.usecase.OpenLinkUseCase
import app.pane.android.domain.usecase.RefreshLinkUseCase
import app.pane.android.domain.usecase.LoadMoreCommentsUseCase
import app.pane.android.ui.home.HomeViewModel
import app.pane.android.ui.mapper.HomeUiMapper
import app.pane.android.ui.mapper.UiImageMapper
import app.pane.android.ui.mapper.ViewerUiMapper
import app.pane.android.ui.model.HomeUiState
import app.pane.android.ui.model.ViewerUiState
import app.pane.android.ui.viewer.ViewerViewModel
import java.time.ZoneOffset
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun homeViewModelPublishesMappedRepositoryState() = runTest(mainDispatcherRule.dispatcher) {
        val recents = FakeRecentLinksRepository(
            listOf(RecentLink(FixtureCatalog.KYOTO_URL, 1_000L)),
        )
        val viewModel = HomeViewModel(
            observeRecentContent = ObserveRecentContentUseCase(FixtureLinkContentRepository(), recents),
            mapper = HomeUiMapper(UiImageMapper(), Clock { 61_000L }, ZoneOffset.UTC),
            recentLinksRepository = recents,
        )
        val collection = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect { }
        }

        advanceUntilIdle()

        val content = viewModel.uiState.value as HomeUiState.Content
        assertEquals("A quiet morning in Kyoto", content.recentLinks.single().title)
        collection.cancel()
    }

    @Test
    fun viewerViewModelMapsResolvedContentAndMarksItOpened() = runTest(mainDispatcherRule.dispatcher) {
        val recents = FakeRecentLinksRepository(emptyList())
        val viewModel = ViewerViewModel(
            url = FixtureCatalog.MATERIAL_URL,
            openLink = OpenLinkUseCase(FixtureLinkContentRepository(), recents),
            refreshLink = RefreshLinkUseCase(FixtureLinkContentRepository(), recents),
            loadMoreComments = LoadMoreCommentsUseCase(FixtureLinkContentRepository()),
            mapper = ViewerUiMapper(UiImageMapper()),
        )

        advanceUntilIdle()

        val content = viewModel.uiState.value as ViewerUiState.Content
        assertEquals("A24 — Material studies", content.post.title)
        assertEquals(listOf(FixtureCatalog.MATERIAL_URL), recents.openedUrls)
    }

    @Test
    fun viewerViewModelPublishesIntermediateProgressBeforeContent() = runTest(mainDispatcherRule.dispatcher) {
        val recents = FakeRecentLinksRepository(emptyList())
        val content = FixtureCatalog.contents.getValue(FixtureCatalog.MATERIAL_URL)
        val repository = ProgressReportingLinkContentRepository(
            content,
            listOf(
                LoadProgress(0.2f, LoadStage.Connecting),
                LoadProgress(0.7f, LoadStage.FetchingPage),
            ),
        )
        val viewModel = ViewerViewModel(
            url = FixtureCatalog.MATERIAL_URL,
            openLink = OpenLinkUseCase(repository, recents),
            refreshLink = RefreshLinkUseCase(repository, recents),
            loadMoreComments = LoadMoreCommentsUseCase(repository),
            mapper = ViewerUiMapper(UiImageMapper()),
        )
        val seen = mutableListOf<ViewerUiState>()
        val collection = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect { seen += it }
        }

        advanceUntilIdle()

        val loadingStates = seen.filterIsInstance<ViewerUiState.Loading>()
        assertEquals(0.2f, loadingStates[1].progress)
        assertEquals(0.7f, loadingStates[2].progress)
        assertEquals(true, seen.last() is ViewerUiState.Content)
        collection.cancel()
    }

    @Test
    fun viewerKeepsAPreviewOnScreenWhenLaterProgressArrives() = runTest(mainDispatcherRule.dispatcher) {
        val recents = FakeRecentLinksRepository(emptyList())
        val full = FixtureCatalog.contents.getValue(FixtureCatalog.MATERIAL_URL)
        val preview = full.copy(title = "Preview caption")
        val repository = PreviewThenFullRepository(preview, full)
        val viewModel = ViewerViewModel(
            url = FixtureCatalog.MATERIAL_URL,
            openLink = OpenLinkUseCase(repository, recents),
            refreshLink = RefreshLinkUseCase(repository, recents),
            loadMoreComments = LoadMoreCommentsUseCase(repository),
            mapper = ViewerUiMapper(UiImageMapper()),
        )
        val seen = mutableListOf<ViewerUiState>()
        val collection = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect { seen += it }
        }

        advanceUntilIdle()

        val titles = seen.filterIsInstance<ViewerUiState.Content>().map { it.post.title }
        assertEquals(listOf("Preview caption", "A24 — Material studies"), titles)
        val firstContent = seen.indexOfFirst { it is ViewerUiState.Content }
        assertEquals(true, seen.drop(firstContent).none { it is ViewerUiState.Loading })
        collection.cancel()
    }

    @Test
    fun viewerViewModelPublishesARetryableNetworkErrorForAnUnknownFixture() = runTest(mainDispatcherRule.dispatcher) {
        val fakeRecents = FakeRecentLinksRepository(emptyList())
        val viewModel = ViewerViewModel(
            url = "unknown",
            openLink = OpenLinkUseCase(FixtureLinkContentRepository(), fakeRecents),
            refreshLink = RefreshLinkUseCase(FixtureLinkContentRepository(), fakeRecents),
            loadMoreComments = LoadMoreCommentsUseCase(FixtureLinkContentRepository()),
            mapper = ViewerUiMapper(UiImageMapper()),
        )

        advanceUntilIdle()

        assertEquals(
            ViewerUiState.LoadFailed("unknown", app.pane.android.ui.viewer.OpenFailureKind.Network),
            viewModel.uiState.value,
        )
    }
}

private class PreviewThenFullRepository(
    private val preview: LinkContent,
    private val full: LinkContent,
) : LinkContentRepository {
    override suspend fun resolve(url: String): Result<LinkContent> = Result.success(full)

    override suspend fun resolve(
        url: String,
        onProgress: LoadProgressListener,
        onPreview: (LinkContent) -> Unit,
    ): Result<LinkContent> {
        onPreview(preview)
        onProgress.onProgress(LoadProgress(0.9f, LoadStage.ExtractingContent))
        return Result.success(full)
    }

    override suspend fun peekCached(url: String): LinkContent? = null

    override suspend fun refresh(url: String): Result<LinkContent> = resolve(url)
}

private class ProgressReportingLinkContentRepository(
    private val content: LinkContent,
    private val progress: List<LoadProgress>,
) : LinkContentRepository {
    override suspend fun resolve(url: String): Result<LinkContent> = Result.success(content)

    override suspend fun resolve(url: String, onProgress: LoadProgressListener): Result<LinkContent> {
        progress.forEach(onProgress::onProgress)
        return Result.success(content)
    }

    override suspend fun peekCached(url: String): LinkContent? = content

    override suspend fun refresh(url: String): Result<LinkContent> = resolve(url)

    override suspend fun refresh(url: String, onProgress: LoadProgressListener): Result<LinkContent> =
        resolve(url, onProgress)
}
