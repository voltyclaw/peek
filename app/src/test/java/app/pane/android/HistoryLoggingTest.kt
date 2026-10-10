package app.pane.android

import app.pane.android.domain.model.Author
import app.pane.android.domain.model.Clock
import app.pane.android.domain.model.HistoryEntry
import app.pane.android.domain.model.HistoryQuery
import app.pane.android.domain.model.HistoryView
import app.pane.android.domain.model.LinkContent
import app.pane.android.domain.model.LinkKind
import app.pane.android.domain.model.LinkSource
import app.pane.android.domain.model.Media
import app.pane.android.domain.model.MediaLocation
import app.pane.android.domain.model.RecentLink
import app.pane.android.domain.model.StarCopy
import app.pane.android.domain.model.StarImageBytes
import app.pane.android.domain.repository.HistoryRepository
import app.pane.android.domain.repository.LinkContentRepository
import app.pane.android.domain.repository.RecentLinksRepository
import app.pane.android.domain.usecase.OpenLinkUseCase
import app.pane.android.domain.usecase.RefreshLinkUseCase
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryLoggingTest {
    @Test
    fun aSuccessfulViewIsLoggedAndARepeatIncrementsTheCount() = runTest {
        val history = RecordingHistoryRepository()
        val recents = RecordingRecents()
        val useCase = OpenLinkUseCase(
            contentRepository = object : LinkContentRepository {
                override suspend fun resolve(url: String) = Result.success(post(url))
                override suspend fun refresh(url: String) = resolve(url)
                override suspend fun peekCached(url: String) = null
            },
            recentLinksRepository = recents,
            historyRepository = history,
            clock = Clock { 10L },
        )

        useCase("https://twitter.com/ada/status/20").getOrThrow()
        useCase("https://twitter.com/ada/status/20").getOrThrow()

        assertEquals("https://twitter.com/ada/status/20", recents.lastOpened)
        assertEquals(2, history.views.size)
        assertEquals(10L, history.views.last().viewedAtEpochMillis)
        assertEquals("Ada Lovelace", history.views.first().authorName)
        assertEquals("ada", history.views.first().handle)
    }

    @Test
    fun aFailedOrUnloadableOpenIsNotLogged() = runTest {
        val history = RecordingHistoryRepository()
        val recents = RecordingRecents()
        val useCase = OpenLinkUseCase(
            contentRepository = object : LinkContentRepository {
                override suspend fun resolve(url: String) = Result.failure<LinkContent>(IOException("unloadable"))
                override suspend fun refresh(url: String) = resolve(url)
                override suspend fun peekCached(url: String) = null
            },
            recentLinksRepository = recents,
            historyRepository = history,
        )

        assertTrue(useCase("https://x.com/i/status/20").isFailure)
        assertNull(recents.lastOpened)
        assertTrue(history.views.isEmpty())
    }

    @Test
    fun aSuccessfulSampleViewIsLogged() = runTest {
        val history = RecordingHistoryRepository()
        val canonical = "https://www.reddit.com/r/hiking/comments/samplehike/first_solo/"
        val useCase = OpenLinkUseCase(
            contentRepository = object : LinkContentRepository {
                override suspend fun resolve(url: String) = Result.success(post(canonical, source = LinkSource.Reddit))
                override suspend fun refresh(url: String) = resolve(url)
                override suspend fun peekCached(url: String) = null
            },
            recentLinksRepository = RecordingRecents(),
            historyRepository = history,
        )

        useCase("pane://sample/reddit-text").getOrThrow()

        assertEquals(canonical, history.views.single().url)
        assertEquals("Reddit", history.views.single().source)
    }

    @Test
    fun aSuccessfulRefreshIsLoggedAndAFailedRefreshIsNot() = runTest {
        val history = RecordingHistoryRepository()
        val failing = RefreshLinkUseCase(
            contentRepository = object : LinkContentRepository {
                override suspend fun resolve(url: String) = Result.failure<LinkContent>(IOException("no"))
                override suspend fun refresh(url: String) = resolve(url)
                override suspend fun peekCached(url: String) = null
            },
            recentLinksRepository = RecordingRecents(),
            historyRepository = history,
        )
        assertTrue(failing("https://x.com/i/status/20").isFailure)
        assertTrue(history.views.isEmpty())

        val refreshing = RefreshLinkUseCase(
            contentRepository = object : LinkContentRepository {
                override suspend fun resolve(url: String) = Result.success(post(url))
                override suspend fun refresh(url: String) = resolve(url)
                override suspend fun peekCached(url: String) = null
            },
            recentLinksRepository = RecordingRecents(),
            historyRepository = history,
        )
        refreshing("https://x.com/i/status/20").getOrThrow()
        assertEquals(1, history.views.size)
    }

    private fun post(
        url: String,
        source: LinkSource = LinkSource.X,
    ) = LinkContent(
        url = url,
        title = "Hello from Ada",
        source = source,
        kind = LinkKind.Post,
        thumbnail = MediaLocation.Remote("https://cdn.example/thumb.jpg"),
        media = Media(
            location = MediaLocation.Remote("https://cdn.example/thumb.jpg"),
            contentDescription = "thumb",
            badge = "PHOTO",
        ),
        author = Author(name = "Ada Lovelace", metadata = "@ada", avatarUrl = "https://cdn.example/ada.jpg"),
        commentCount = 0,
        comments = emptyList(),
    )
}

private class RecordingHistoryRepository : HistoryRepository {
    val views = mutableListOf<HistoryView>()
    override fun observeHistory(query: HistoryQuery): Flow<List<HistoryEntry>> = flowOf(emptyList())
    override suspend fun recordSuccessfulView(view: HistoryView) {
        views += view
    }
    override suspend fun star(url: String, copy: StarCopy, images: StarImageBytes) = Unit
    override suspend fun unstar(url: String): app.pane.android.domain.model.HistoryUndo? = null
    override suspend fun seedFromRecents(recents: List<RecentLink>) = Unit
    override suspend fun pruneUnstarred(enabled: Boolean, cap: Int): List<String> = emptyList()
}

private class RecordingRecents : RecentLinksRepository {
    private val items = MutableStateFlow<List<RecentLink>>(emptyList())
    var lastOpened: String? = null
    override fun observeRecents(): Flow<List<RecentLink>> = items
    override suspend fun markOpened(url: String) {
        lastOpened = url
    }
    override suspend fun remove(url: String) = Unit
    override suspend fun clear() = Unit
}
