package app.pane.android.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.pane.android.domain.repository.RecentLinksRepository
import app.pane.android.domain.usecase.ObserveRecentContentUseCase
import app.pane.android.ui.mapper.HomeUiMapper
import app.pane.android.ui.model.HomeUiState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HomeViewModel(
    observeRecentContent: ObserveRecentContentUseCase,
    mapper: HomeUiMapper,
    private val recentLinksRepository: RecentLinksRepository,
) : ViewModel() {
    val uiState: StateFlow<HomeUiState> = observeRecentContent()
        .map(mapper::map)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = HomeUiState.Loading,
        )

    fun removeRecent(url: String) {
        viewModelScope.launch { recentLinksRepository.remove(url) }
    }

    fun clearRecents() {
        viewModelScope.launch { recentLinksRepository.clear() }
    }

    class Factory(
        private val observeRecentContent: ObserveRecentContentUseCase,
        private val mapper: HomeUiMapper,
        private val recentLinksRepository: RecentLinksRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            HomeViewModel(observeRecentContent, mapper, recentLinksRepository) as T
    }
}
