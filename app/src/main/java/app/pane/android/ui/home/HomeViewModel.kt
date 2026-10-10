package app.pane.android.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.pane.android.data.history.HistoryUrls
import app.pane.android.domain.model.HistoryGesture
import app.pane.android.domain.model.HistoryLedger
import app.pane.android.domain.model.HistoryUndo
import app.pane.android.domain.model.RecentContent
import app.pane.android.domain.model.RecentLink
import app.pane.android.domain.model.StarCopy
import app.pane.android.domain.model.toHistoryView
import app.pane.android.domain.repository.HistoryRepository
import app.pane.android.domain.repository.NoHistoryRepository
import app.pane.android.domain.repository.RecentLinksRepository
import app.pane.android.domain.usecase.ObserveRecentContentUseCase
import app.pane.android.ui.mapper.HomeUiMapper
import app.pane.android.ui.model.HomeUiState
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

sealed interface HubNotice {
    data class Removed(val link: RecentLink) : HubNotice
    data object Starred : HubNotice
    data class Unstarred(val undo: HistoryUndo) : HubNotice
}

class HomeViewModel(
    observeRecentContent: ObserveRecentContentUseCase,
    private val mapper: HomeUiMapper,
    private val recentLinksRepository: RecentLinksRepository,
    private val historyRepository: HistoryRepository = NoHistoryRepository,
) : ViewModel() {
    private val notices = MutableSharedFlow<HubNotice>(extraBufferCapacity = 1)
    val notice: SharedFlow<HubNotice> = notices.asSharedFlow()
    private var latest = emptyList<RecentContent>()
    private val writes = Mutex()

    val uiState: StateFlow<HomeUiState> = combine(
        observeRecentContent(),
        historyRepository.observeHistory(),
    ) { recents, history ->
        latest = recents
        mapper.map(recents, history)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeUiState.Loading,
    )

    fun removeRecent(url: String) = swipe(url, left = true)

    /** Left removes the recent only. Right stars, or unstars when the post is already starred. */
    fun swipe(url: String, left: Boolean) {
        viewModelScope.launch {
            val pending = writes.withLock {
                when (HistoryLedger.hubGesture(left, isStarred(url))) {
                    HistoryGesture.Remove -> removeFromRecents(url)
                    HistoryGesture.RemoveStar -> {
                        val undo = historyRepository.unstar(url) ?: return@withLock null
                        HubNotice.Unstarred(undo)
                    }
                    HistoryGesture.Star -> {
                        historyRepository.star(url, copyFor(url))
                        HubNotice.Starred
                    }
                }
            }
            pending?.let { notices.emit(it) }
        }
    }

    fun undoRecent(link: RecentLink) {
        viewModelScope.launch { recentLinksRepository.restore(link) }
    }

    fun undoHistory(undo: HistoryUndo) {
        viewModelScope.launch { historyRepository.undo(undo) }
    }

    fun clearRecents() {
        viewModelScope.launch { recentLinksRepository.clear() }
    }

    private suspend fun removeFromRecents(url: String): HubNotice? {
        val link = recentLinksRepository.observeRecents().first().firstOrNull { it.url == url } ?: return null
        recentLinksRepository.remove(url)
        return HubNotice.Removed(link)
    }

    private suspend fun isStarred(url: String): Boolean {
        val key = HistoryUrls.canonical(url)
        return historyRepository.observeHistory().first().any { it.url == key && it.starredAt != null }
    }

    private suspend fun copyFor(url: String): StarCopy {
        val key = HistoryUrls.canonical(url)
        val content = latest.firstOrNull { HistoryUrls.canonical(it.recentLink.url) == key }?.content
        if (content != null) {
            val viewed = content.toHistoryView(url, 0L)
            return StarCopy(viewed.title, viewed.authorName, viewed.handle, viewed.caption, viewed.thumbUrl, viewed.pfpUrl)
        }
        val existing = historyRepository.observeHistory().first().firstOrNull { it.url == key }
        if (existing != null) {
            return StarCopy(existing.title, existing.authorName, existing.handle, existing.caption, existing.thumbUrl, existing.pfpUrl)
        }
        return StarCopy(url, "", "", url)
    }

    class Factory(
        private val observeRecentContent: ObserveRecentContentUseCase,
        private val mapper: HomeUiMapper,
        private val recentLinksRepository: RecentLinksRepository,
        private val historyRepository: HistoryRepository = NoHistoryRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            HomeViewModel(observeRecentContent, mapper, recentLinksRepository, historyRepository) as T
    }
}
