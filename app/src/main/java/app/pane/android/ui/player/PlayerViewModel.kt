package app.pane.android.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.pane.android.domain.repository.LoadProgressListener
import app.pane.android.domain.usecase.OpenLinkUseCase
import app.pane.android.domain.usecase.LoadMoreCommentsUseCase
import app.pane.android.ui.mapper.ViewerUiMapper
import app.pane.android.ui.mapper.loadStageMessage
import app.pane.android.ui.model.ViewerUiState
import app.pane.android.ui.viewer.MonotonicProgress
import app.pane.android.ui.viewer.viewerStateFor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class PlayerViewModel(
    private val url: String,
    private val openLink: OpenLinkUseCase,
    private val loadMoreComments: LoadMoreCommentsUseCase,
    private val mapper: ViewerUiMapper,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow<ViewerUiState>(ViewerUiState.Loading())
    val uiState: StateFlow<ViewerUiState> = mutableUiState.asStateFlow()

    init {
        viewModelScope.launch {
            val monotonic = MonotonicProgress()
            val onProgress = LoadProgressListener { progress ->
                val steady = monotonic.apply(progress)
                mutableUiState.value = ViewerUiState.Loading(steady.fraction, loadStageMessage(steady.stage))
            }
            mutableUiState.value = openLink(url, onProgress).fold(
                onSuccess = { ViewerUiState.Content(mapper.map(it)) },
                onFailure = { viewerStateFor(url, it) },
            )
        }
    }

    fun onLoadMoreComments() {
        val content = mutableUiState.value as? ViewerUiState.Content ?: return
        if (!content.post.canLoadMoreComments || content.isLoadingMoreComments) return
        mutableUiState.value = content.copy(isLoadingMoreComments = true)
        viewModelScope.launch {
            loadMoreComments(url).onSuccess { updated ->
                mutableUiState.value = ViewerUiState.Content(mapper.map(updated))
            }.onFailure {
                mutableUiState.value = content
            }
        }
    }

    class Factory(
        private val url: String,
        private val openLink: OpenLinkUseCase,
        private val loadMoreComments: LoadMoreCommentsUseCase,
        private val mapper: ViewerUiMapper,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            PlayerViewModel(url, openLink, loadMoreComments, mapper) as T
    }
}
