package app.pane.android.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.pane.android.domain.model.Clock
import app.pane.android.domain.model.HistoryEntry
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
import java.time.ZoneId
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

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
) : ViewModel() {
    private val query = MutableStateFlow(initial)
    private val notices = MutableSharedFlow<HistoryNotice>(extraBufferCapacity = 1)
    val notice: SharedFlow<HistoryNotice> = notices.asSharedFlow()
    private var latest = emptyList<HistoryEntry>()

    val uiState: StateFlow<HistoryListUi> = combine(historyRepository.observeHistory(), query) { rows, filter ->
        latest = rows
        HistoryPresenter.present(rows, filter, clock.nowEpochMillis(), zone)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HistoryPresenter.present(emptyList(), initial, clock.nowEpochMillis(), zone),
    )

    fun showAll() = update(query.value.copy(scope = HistoryScope.All))

    fun showStarred() = update(query.value.copy(scope = HistoryScope.Starred))

    fun toggleApp(app: SourceApp) {
        val apps = query.value.apps.toMutableSet()
        if (!apps.add(app)) apps.remove(app)
        update(query.value.copy(apps = apps))
    }

    fun clearApps() = update(query.value.copy(apps = emptySet()))

    /** Swipes unstar a starred row first. A second swipe removes it. */
    fun swipe(url: String, left: Boolean) {
        val row = latest.firstOrNull { it.url == url } ?: return
        when (HistoryLedger.gesture(left, row.starredAt != null)) {
            HistoryGesture.Star -> star(row)
            HistoryGesture.RemoveStar -> unstar(url)
            HistoryGesture.Remove -> remove(url)
        }
    }

    /**
     * Menu actions. Remove deletes the row and the star together.
     * That is not a swipe: the unstar-first rule does not apply here.
     */
    fun menu(url: String, action: HistoryRowAction) {
        val row = latest.firstOrNull { it.url == url } ?: return
        when (action) {
            HistoryRowAction.Star -> star(row)
            HistoryRowAction.RemoveStar -> unstar(url)
            HistoryRowAction.Remove -> remove(url)
            else -> Unit
        }
    }

    fun undo(undo: HistoryUndo) {
        viewModelScope.launch { historyRepository.undo(undo) }
    }

    private fun star(row: HistoryEntry) {
        viewModelScope.launch {
            historyRepository.star(
                row.url,
                StarCopy(row.title, row.authorName, row.handle, row.caption, row.thumbUrl, row.pfpUrl),
            )
            notices.emit(HistoryNotice.Starred)
        }
    }

    private fun unstar(url: String) {
        viewModelScope.launch {
            val undo = historyRepository.applySwipe(url) ?: return@launch
            notices.emit(HistoryNotice.Unstarred(undo))
        }
    }

    private fun remove(url: String) {
        viewModelScope.launch {
            val undo = historyRepository.remove(url) ?: return@launch
            notices.emit(HistoryNotice.Removed(undo))
        }
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
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            HistoryViewModel(historyRepository, initial, persist, clock, zone) as T
    }
}
