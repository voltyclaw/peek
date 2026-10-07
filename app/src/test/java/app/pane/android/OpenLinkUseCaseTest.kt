package app.pane.android

import app.pane.android.data.fixture.FixtureCatalog
import app.pane.android.data.fixture.FixtureLinkContentRepository
import app.pane.android.domain.model.RecentLink
import app.pane.android.domain.repository.RecentLinksRepository
import app.pane.android.domain.repository.LinkContentRepository
import app.pane.android.domain.usecase.OpenLinkUseCase
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenLinkUseCaseTest {
    @Test
    fun successfulResolutionRecordsTheOpenedUrl() = runTest {
        val recents = RecordingRecentLinksRepository()
        val useCase = OpenLinkUseCase(FixtureLinkContentRepository(), recents)

        useCase(FixtureCatalog.KYOTO_URL).getOrThrow()

        assertEquals(FixtureCatalog.KYOTO_URL, recents.lastOpened)
    }

    @Test
    fun aFailedOpenDoesNotWriteRecents() = runTest {
        val recents = RecordingRecentLinksRepository()
        val useCase = OpenLinkUseCase(
            object : LinkContentRepository {
                override suspend fun resolve(url: String) = Result.failure<app.pane.android.domain.model.LinkContent>(
                    IOException("Unable to resolve host"),
                )
                override suspend fun refresh(url: String) = resolve(url)
                override suspend fun peekCached(url: String) = null
            },
            recents,
        )

        assertTrue(useCase("pane://sample/error").isFailure)
        assertNull(recents.lastOpened)
    }

    @Test
    fun aSuccessfulSampleRecordsTheCanonicalUrl() = runTest {
        val recents = RecordingRecentLinksRepository()
        val canonical = "https://www.reddit.com/r/hiking/comments/samplehike/first_solo/"
        val content = FixtureCatalog.contents.getValue(FixtureCatalog.KYOTO_URL).copy(url = canonical)
        val useCase = OpenLinkUseCase(
            object : LinkContentRepository {
                override suspend fun resolve(url: String) = Result.success(content)
                override suspend fun refresh(url: String) = resolve(url)
                override suspend fun peekCached(url: String) = content
            },
            recents,
        )

        useCase("pane://sample/reddit-text").getOrThrow()

        assertEquals(canonical, recents.lastOpened)
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
