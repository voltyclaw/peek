package app.pane.android

import app.pane.android.data.fixture.FixtureCatalog
import app.pane.android.data.fixture.FixtureLinkContentRepository
import app.pane.android.domain.model.RecentLink
import app.pane.android.domain.repository.RecentLinksRepository
import app.pane.android.domain.usecase.OpenLinkUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class OpenLinkUseCaseTest {
    @Test
    fun successfulResolutionRecordsTheOpenedUrl() = runTest {
        val recents = RecordingRecentLinksRepository()
        val useCase = OpenLinkUseCase(FixtureLinkContentRepository(), recents)

        useCase(FixtureCatalog.KYOTO_URL).getOrThrow()

        assertEquals(FixtureCatalog.KYOTO_URL, recents.lastOpened)
    }
}

private class RecordingRecentLinksRepository : RecentLinksRepository {
    private val items = MutableStateFlow<List<RecentLink>>(emptyList())
    var lastOpened: String? = null
    override fun observeRecents(): Flow<List<RecentLink>> = items
    override suspend fun markOpened(url: String) { lastOpened = url }
    override suspend fun remove(url: String) = Unit
    override suspend fun clear() = Unit
}
