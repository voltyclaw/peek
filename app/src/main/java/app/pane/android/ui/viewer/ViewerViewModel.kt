package app.pane.android.ui.viewer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.pane.android.domain.model.LinkContent
import app.pane.android.domain.repository.LoadProgressListener
import app.pane.android.domain.usecase.OpenLinkUseCase
import app.pane.android.domain.usecase.RefreshLinkUseCase
import app.pane.android.domain.usecase.LoadMoreCommentsUseCase
import app.pane.android.ui.mapper.ViewerUiMapper
import app.pane.android.ui.mapper.loadStageMessage
import app.pane.android.ui.model.ViewerUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ViewerViewModel(
    private val url: String,
    private val openLink: OpenLinkUseCase,
    private val refreshLink: RefreshLinkUseCase,
    private val loadMoreComments: LoadMoreCommentsUseCase,
    private val mapper: ViewerUiMapper,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow<ViewerUiState>(ViewerUiState.Loading())
    val uiState: StateFlow<ViewerUiState> = mutableUiState.asStateFlow()

    init {
        viewModelScope.launch { load { onProgress -> openLink(url, onProgress) } }
    }

    fun onRefresh() {
        mutableUiState.value = ViewerUiState.Loading()
        viewModelScope.launch { load { onProgress -> refreshLink(url, onProgress) } }
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

    private suspend fun load(fetch: suspend (LoadProgressListener) -> Result<LinkContent>) {
        val monotonic = MonotonicProgress()
        val onProgress = LoadProgressListener { progress ->
            val steady = monotonic.apply(progress)
            mutableUiState.value = ViewerUiState.Loading(steady.fraction, loadStageMessage(steady.stage))
        }
        mutableUiState.value = fetch(onProgress).fold(
            onSuccess = { ViewerUiState.Content(mapper.map(it)) },
            onFailure = { viewerStateFor(url, it) },
        )
    }

    class Factory(
        private val url: String,
        private val openLink: OpenLinkUseCase,
        private val refreshLink: RefreshLinkUseCase,
        private val loadMoreComments: LoadMoreCommentsUseCase,
        private val mapper: ViewerUiMapper,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            ViewerViewModel(url, openLink, refreshLink, loadMoreComments, mapper) as T
    }
}
