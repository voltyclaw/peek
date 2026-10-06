package app.pane.android

import androidx.datastore.core.DataStore
import app.pane.android.data.recent.DataStoreRecentLinksRepository
import app.pane.android.data.recent.RecentLinkRecord
import app.pane.android.data.recent.RecentLinksDocument
import app.pane.android.domain.model.Clock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class RecentLinksRepositoryTest {
    @Test
    fun reopeningLinkDeduplicatesAndMovesItToTop() = runTest {
        val store = FakeDataStore(
            RecentLinksDocument(
                listOf(
                    RecentLinkRecord("one", 100),
                    RecentLinkRecord("two", 200),
                    RecentLinkRecord("one", 50),
                ),
            ),
        )
        val repository = DataStoreRecentLinksRepository(store, Clock { 500 })

        repository.markOpened("one")

        assertEquals(listOf("one", "two"), repository.observeRecents().first().map { it.url })
        assertEquals(500, repository.observeRecents().first().first().openedAtEpochMillis)
    }

    @Test
    fun historyIsCappedAtEightEntries() = runTest {
        val records = (1..8).map { RecentLinkRecord("url-$it", it.toLong()) }
        val store = FakeDataStore(RecentLinksDocument(records))
        val repository = DataStoreRecentLinksRepository(store, Clock { 1_000 })

        repository.markOpened("new-url")

        val links = repository.observeRecents().first()
        assertEquals(8, links.size)
        assertEquals("new-url", links.first().url)
    }

    @Test
    fun removeDropsOneLinkAndClearDropsTheRest() = runTest {
        val store = FakeDataStore(
            RecentLinksDocument(
                listOf(
                    RecentLinkRecord("one", 100),
                    RecentLinkRecord("two", 200),
                ),
            ),
        )
        val repository = DataStoreRecentLinksRepository(store, Clock { 1 })

        repository.remove("one")
        assertEquals(listOf("two"), repository.observeRecents().first().map { it.url })

        repository.clear()
        assertEquals(emptyList<String>(), repository.observeRecents().first().map { it.url })
    }
}

private class FakeDataStore<T>(initial: T) : DataStore<T> {
    private val state = MutableStateFlow(initial)
    private val mutex = Mutex()
    override val data: Flow<T> = state

    override suspend fun updateData(transform: suspend (t: T) -> T): T = mutex.withLock {
        transform(state.value).also { state.value = it }
    }
}
