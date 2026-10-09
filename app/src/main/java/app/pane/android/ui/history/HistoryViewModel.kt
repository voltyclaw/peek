package app.pane.android.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.pane.android.data.history.HistoryUrls
import app.pane.android.domain.model.Clock
import app.pane.android.domain.model.HistoryEntry
import app.pane.android.domain.model.HistoryFilterCatalog
import app.pane.android.domain.model.HistoryGesture
import app.pane.android.domain.model.HistoryLedger
import app.pane.android.domain.model.HistoryQuery
import app.pane.android.domain.model.HistoryRowAction
import app.pane.android.domain.model.HistoryScope
import app.pane.android.domain.model.HistoryUndo
import app.pane.android.domain.model.SourceApp
import app.pane.android.domain.model.StarCopy
import app.pane.android.domain.model.SystemClock
import app.pane.android.domain.repository.HistoryRepository
import app.pane.android.domain.tiktok.TikTokScrollRefresh
import app.pane.android.domain.tiktok.TikTokVisibleRow
import java.time.ZoneId
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

sealed interface HistoryNotice {
    data object Starred : HistoryNotice
    data class Unstarred(val undo: HistoryUndo) : HistoryNotice
    data class Removed(val undo: HistoryUndo) : HistoryNotice
}

class HistoryViewModel(
    private val historyRepository: HistoryRepository,
    initial: HistoryQuery,
    private val persist: (HistoryQuery) -> Unit,
    private val clock: Clock = SystemClock,
    private val zone: ZoneId = ZoneId.systemDefault(),
    private val refreshTikTok: (suspend (id: String, pageUrl: String) -> Unit)? = null,
    private val canRefreshTikTok: () -> Boolean = { false },
) : ViewModel() {
    private val query = MutableStateFlow(initial)
    private val notices = MutableSharedFlow<HistoryNotice>(extraBufferCapacity = 1)
    val notice: SharedFlow<HistoryNotice> = notices.asSharedFlow()
    private var latest = emptyList<HistoryEntry>()
    private var visibleTikTok = emptyList<TikTokVisibleRow>()
    private val refreshedTikTok = mutableSetOf<String>()
    private var refreshingTikTok = false
    private val writes = Mutex()

    val uiState: StateFlow<HistoryListUi> = combine(historyRepository.observeHistory(), query) { rows, filter ->
        latest = rows
        HistoryPresenter.present(rows, filter, clock.nowEpochMillis(), zone)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HistoryPresenter.present(emptyList(), initial, clock.nowEpochMillis(), zone),
    )

    /** All clears Starred and the app. Tapping All when it is already the only selection does nothing. */
    fun showAll() {
        val current = query.value
        if (current.scope == HistoryScope.All && current.apps.isEmpty()) return
        update(HistoryQuery())
    }

    /** Tapping Starred again turns it off. An app filter stays. */
    fun toggleStarred() {
        val current = query.value
        val scope = if (current.scope == HistoryScope.Starred) HistoryScope.All else HistoryScope.Starred
        update(current.copy(scope = scope))
    }

    /** One app at a time. Null clears the app chip. */
    fun selectApp(app: SourceApp?) {
        val apps = app?.takeIf { it in HistoryFilterCatalog.order }?.let { setOf(it) } ?: emptySet()
        if (query.value.apps == apps) return
        update(query.value.copy(apps = apps))
    }

    /** One expired TikTok thumbnail at a time, through the shared oEmbed bucket. */
    fun noteVisibleTikTok(rows: List<TikTokVisibleRow>) {
        visibleTikTok = rows
        refreshedTikTok.retainAll(rows.map { it.id }.toSet())
        pumpTikTok()
    }

    private fun pumpTikTok() {
        val refresh = refreshTikTok ?: return
        if (refreshingTikTok || !canRefreshTikTok()) return
        val now = clock.nowEpochMillis() / 1000L
        val pending = visibleTikTok.filter { it.id !in refreshedTikTok }
        val next = TikTokScrollRefresh.next(pending, now) ?: return
        refreshedTikTok.add(next.id)
        refreshingTikTok = true
        viewModelScope.launch {
            try {
                refresh(next.id, next.pageUrl)
            } finally {
                refreshingTikTok = false
                pumpTikTok()
            }
        }
    }

    /** Swipes unstar a starred row first. A second swipe removes it. */
    fun swipe(url: String, left: Boolean) {
        viewModelScope.launch {
            writes.withLock {
                val row = rowFor(url) ?: return@withLock
                when (HistoryLedger.gesture(left, row.starredAt != null)) {
                    HistoryGesture.Star -> star(row)
                    HistoryGesture.RemoveStar -> unstar(row.url)
                    HistoryGesture.Remove -> remove(row.url)
                }
            }
        }
    }

    /**
     * Menu actions. Remove deletes the row and the star together.
     * That is not a swipe: the unstar-first rule does not apply here.
     */
    fun menu(url: String, action: HistoryRowAction) {
        viewModelScope.launch {
            writes.withLock {
                val row = rowFor(url) ?: return@withLock
                when (action) {
                    HistoryRowAction.Star -> star(row)
                    HistoryRowAction.RemoveStar -> unstar(row.url)
                    HistoryRowAction.Remove -> remove(row.url)
                    else -> Unit
                }
            }
        }
    }

    private suspend fun rowFor(url: String): HistoryEntry? {
        val key = HistoryUrls.canonical(url)
        return historyRepository.observeHistory().first()
            .firstOrNull { HistoryUrls.canonical(it.url) == key || it.url == url }
    }

    fun undo(undo: HistoryUndo) {
        viewModelScope.launch { historyRepository.undo(undo) }
    }

    private suspend fun star(row: HistoryEntry) {
        historyRepository.star(
            row.url,
            StarCopy(row.title, row.authorName, row.handle, row.caption, row.thumbUrl, row.pfpUrl),
        )
        notices.emit(HistoryNotice.Starred)
    }

    private suspend fun unstar(url: String) {
        val undo = historyRepository.applySwipe(url) ?: return
        notices.emit(HistoryNotice.Unstarred(undo))
    }

    private suspend fun remove(url: String) {
        val undo = historyRepository.remove(url) ?: return
        notices.emit(HistoryNotice.Removed(undo))
    }

    private fun update(next: HistoryQuery) {
        query.value = next
        persist(next)
    }

    class Factory(
        private val historyRepository: HistoryRepository,
        private val initial: HistoryQuery,
        private val persist: (HistoryQuery) -> Unit,
        private val clock: Clock = SystemClock,
        private val zone: ZoneId = ZoneId.systemDefault(),
        private val refreshTikTok: (suspend (id: String, pageUrl: String) -> Unit)? = null,
        private val canRefreshTikTok: () -> Boolean = { false },
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            HistoryViewModel(historyRepository, initial, persist, clock, zone, refreshTikTok, canRefreshTikTok) as T
    }
}
