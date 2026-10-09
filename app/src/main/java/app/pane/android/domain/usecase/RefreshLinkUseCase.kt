package app.pane.android.domain.usecase

import app.pane.android.domain.model.Clock
import app.pane.android.domain.model.LinkContent
import app.pane.android.domain.model.SystemClock
import app.pane.android.domain.model.toHistoryView
import app.pane.android.domain.repository.HistoryRepository
import app.pane.android.domain.repository.LinkContentRepository
import app.pane.android.domain.repository.LoadProgressListener
import app.pane.android.domain.repository.NoHistoryRepository
import app.pane.android.domain.repository.RecentLinksRepository

class RefreshLinkUseCase(
    private val contentRepository: LinkContentRepository,
    private val recentLinksRepository: RecentLinksRepository,
    private val historyRepository: HistoryRepository = NoHistoryRepository,
    private val clock: Clock = SystemClock,
) {
    suspend operator fun invoke(
        url: String,
        onProgress: LoadProgressListener = LoadProgressListener {},
        onPreview: (LinkContent) -> Unit = {},
    ): Result<LinkContent> =
        contentRepository.refresh(url, onProgress, onPreview).onSuccess { content ->
            val record = content.url.takeIf { it.startsWith("http://") || it.startsWith("https://") } ?: url
            recentLinksRepository.markOpened(record)
            runCatching {
                historyRepository.recordSuccessfulView(content.toHistoryView(record, clock.nowEpochMillis()))
            }
        }
}
