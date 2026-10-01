package app.pane.android.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.pane.android.domain.usecase.ObserveRecentContentUseCase
import app.pane.android.ui.mapper.HomeUiMapper
import app.pane.android.ui.model.HomeUiState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class HomeViewModel(
    observeRecentContent: ObserveRecentContentUseCase,
    mapper: HomeUiMapper,
) : ViewModel() {
    val uiState: StateFlow<HomeUiState> = observeRecentContent()
        .map(mapper::map)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = HomeUiState.Loading,
        )

    class Factory(
        private val observeRecentContent: ObserveRecentContentUseCase,
        private val mapper: HomeUiMapper,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            HomeViewModel(observeRecentContent, mapper) as T
    }
}
