package app.pane.android.ui.viewer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.pane.android.data.history.HistoryUrls
import app.pane.android.domain.model.HistoryEntry
import app.pane.android.domain.model.HistoryLedger
import app.pane.android.domain.model.HistoryUndo
import app.pane.android.domain.model.LinkContent
import app.pane.android.domain.model.StarCopy
import app.pane.android.domain.model.toHistoryView
import app.pane.android.domain.repository.HistoryRepository
import app.pane.android.domain.repository.LoadProgressListener
import app.pane.android.domain.repository.NoHistoryRepository
import app.pane.android.domain.usecase.OpenLinkUseCase
import app.pane.android.domain.usecase.RefreshLinkUseCase
import app.pane.android.domain.usecase.LoadMoreCommentsUseCase
import app.pane.android.ui.mapper.ViewerUiMapper
import app.pane.android.ui.mapper.loadStageMessage
import app.pane.android.ui.model.UiImage
import app.pane.android.ui.model.ViewerPostUiModel
import app.pane.android.ui.model.ViewerUiState
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

sealed interface ViewerNotice {
    data object Starred : ViewerNotice
    data class Unstarred(val undo: HistoryUndo) : ViewerNotice
}

class ViewerViewModel(
    private val url: String,
    private val openLink: OpenLinkUseCase,
    private val refreshLink: RefreshLinkUseCase,
    private val loadMoreComments: LoadMoreCommentsUseCase,
    private val mapper: ViewerUiMapper,
    private val historyRepository: HistoryRepository = NoHistoryRepository,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow<ViewerUiState>(ViewerUiState.Loading())
    val uiState: StateFlow<ViewerUiState> = mutableUiState.asStateFlow()
    private val notices = MutableSharedFlow<ViewerNotice>(extraBufferCapacity = 1)
    val notice: SharedFlow<ViewerNotice> = notices.asSharedFlow()
    private var loaded: LinkContent? = null

    init {
        viewModelScope.launch { load { onProgress, onPreview -> openLink(url, onProgress, onPreview) } }
    }

    fun onRefresh() {
        mutableUiState.value = ViewerUiState.Loading()
        viewModelScope.launch { load { onProgress, onPreview -> refreshLink(url, onProgress, onPreview) } }
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

    private suspend fun load(
        fetch: suspend (LoadProgressListener, (LinkContent) -> Unit) -> Result<LinkContent>,
    ) {
        val monotonic = MonotonicProgress()
        var revealed = false
        val onProgress = LoadProgressListener { progress ->
            if (revealed) return@LoadProgressListener
            val steady = monotonic.apply(progress)
            mutableUiState.value = ViewerUiState.Loading(steady.fraction, loadStageMessage(steady.stage))
        }
        val onPreview: (LinkContent) -> Unit = { preview ->
            revealed = true
            mutableUiState.value = ViewerUiState.Content(mapper.map(preview))
        }
        fetch(onProgress, onPreview).onSuccess { content ->
            loaded = content
            mutableUiState.value = ViewerUiState.Content(mapper.map(content), starred = isStarred(url))
        }.onFailure { error ->
            if (!revealed) mutableUiState.value = savedStar(url) ?: viewerStateFor(url, error)
        }
    }

    fun onToggleStar() {
        val content = mutableUiState.value as? ViewerUiState.Content ?: return
        viewModelScope.launch {
            if (content.starred) {
                val undo = historyRepository.applySwipe(url) ?: return@launch
                mutableUiState.value = content.copy(starred = false)
                notices.emit(ViewerNotice.Unstarred(undo))
            } else {
                historyRepository.star(url, starCopy(content))
                mutableUiState.value = content.copy(starred = true)
                notices.emit(ViewerNotice.Starred)
            }
        }
    }

    fun undoStar(undo: HistoryUndo) {
        viewModelScope.launch {
            historyRepository.undo(undo)
            val content = mutableUiState.value as? ViewerUiState.Content ?: return@launch
            mutableUiState.value = content.copy(starred = undo.row.starredAt != null)
        }
    }

    private suspend fun isStarred(openedUrl: String): Boolean {
        val key = HistoryUrls.canonical(openedUrl)
        return historyRepository.observeHistory().first().any { it.url == key && it.starredAt != null }
    }

    private suspend fun savedStar(openedUrl: String): ViewerUiState.Content? {
        val key = HistoryUrls.canonical(openedUrl)
        val row = historyRepository.observeHistory().first()
            .firstOrNull { it.url == key && it.starredAt != null }
            ?: return null
        return ViewerUiState.Content(savedPost(row), starred = true)
    }

    private fun starCopy(content: ViewerUiState.Content): StarCopy {
        val viewed = loaded?.toHistoryView(url, 0L)
        return if (viewed != null) {
            StarCopy(viewed.title, viewed.authorName, viewed.handle, viewed.caption, viewed.thumbUrl, viewed.pfpUrl)
        } else {
            StarCopy(content.post.title, content.post.authorName, "", content.post.title)
        }
    }

    private fun savedPost(row: HistoryEntry): ViewerPostUiModel {
        val thumb = row.thumbUrl?.takeIf { it.isNotBlank() }?.let(UiImage::Url)
        val video = HistoryLedger.isVideo(row.mediaType)
        return ViewerPostUiModel(
            title = row.caption.ifBlank { row.title },
            isVideo = video,
            media = thumb ?: UiImage.Url(""),
            mediaDescription = row.title,
            mediaBadge = if (video) "VIDEO" else null,
            duration = null,
            videoUrl = null,
            authorName = row.authorName,
            authorMetadata = HistoryLedger.identity(row.sourceApp, row.handle, row.authorName),
            commentCount = 0,
            comments = emptyList(),
            sourceUrl = row.url,
            authorAvatar = row.pfpUrl?.takeIf { it.isNotBlank() }?.let(UiImage::Url),
        )
    }

    class Factory(
        private val url: String,
        private val openLink: OpenLinkUseCase,
        private val refreshLink: RefreshLinkUseCase,
        private val loadMoreComments: LoadMoreCommentsUseCase,
        private val mapper: ViewerUiMapper,
        private val historyRepository: HistoryRepository = NoHistoryRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            ViewerViewModel(url, openLink, refreshLink, loadMoreComments, mapper, historyRepository) as T
    }
}
