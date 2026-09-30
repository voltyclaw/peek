package com.mustafashakir.peek.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mustafashakir.peek.domain.repository.LoadProgressListener
import com.mustafashakir.peek.domain.usecase.OpenLinkUseCase
import com.mustafashakir.peek.domain.usecase.LoadMoreCommentsUseCase
import com.mustafashakir.peek.ui.mapper.ViewerUiMapper
import com.mustafashakir.peek.ui.mapper.loadStageMessage
import com.mustafashakir.peek.ui.model.ViewerUiState
import com.mustafashakir.peek.ui.viewer.MonotonicProgress
import com.mustafashakir.peek.ui.viewer.viewerStateFor
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
