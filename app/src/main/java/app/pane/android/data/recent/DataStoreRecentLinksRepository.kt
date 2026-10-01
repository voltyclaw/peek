package app.pane.android.data.recent

import androidx.datastore.core.DataStore
import app.pane.android.domain.model.Clock
import app.pane.android.domain.model.RecentLink
import app.pane.android.domain.repository.RecentLinksRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class DataStoreRecentLinksRepository(
    private val dataStore: DataStore<RecentLinksDocument>,
    private val clock: Clock,
    private val maximumEntries: Int = 20,
) : RecentLinksRepository {
    override fun observeRecents(): Flow<List<RecentLink>> = dataStore.data.map { document ->
        document.links
            .sortedByDescending(RecentLinkRecord::openedAtEpochMillis)
            .map { RecentLink(it.url, it.openedAtEpochMillis) }
    }

    override suspend fun markOpened(url: String) {
        dataStore.updateData { current ->
            val opened = RecentLinkRecord(url, clock.nowEpochMillis())
            current.copy(
                links = (listOf(opened) + current.links.filterNot { it.url == url })
                    .take(maximumEntries),
            )
        }
    }
}
