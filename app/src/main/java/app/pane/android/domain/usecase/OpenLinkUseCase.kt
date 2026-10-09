package app.pane.android.domain.usecase

import app.pane.android.domain.model.Clock
import app.pane.android.domain.model.LinkContent
import app.pane.android.domain.model.NetworkStatus
import app.pane.android.domain.model.OfflineException
import app.pane.android.domain.model.SystemClock
import app.pane.android.domain.model.toHistoryView
import app.pane.android.domain.repository.HistoryRepository
import app.pane.android.domain.repository.LinkContentRepository
import app.pane.android.domain.repository.LoadProgressListener
import app.pane.android.domain.repository.NoHistoryRepository
import app.pane.android.domain.repository.RecentLinksRepository

class OpenLinkUseCase(
    private val contentRepository: LinkContentRepository,
    private val recentLinksRepository: RecentLinksRepository,
    private val historyRepository: HistoryRepository = NoHistoryRepository,
    private val clock: Clock = SystemClock,
    private val network: NetworkStatus = NetworkStatus.Always,
) {
    suspend operator fun invoke(
        url: String,
        onProgress: LoadProgressListener = LoadProgressListener {},
        onPreview: (LinkContent) -> Unit = {},
    ): Result<LinkContent> {
        if (!network.online()) return Result.failure(OfflineException())
        return contentRepository.resolve(url, onProgress, onPreview).onSuccess { content ->
            // Success only. A failed or unloadable open does not get a Recents or History row.
            // Sample deep links record the canonical https post, which is what the row displays.
            // Profile handoffs and mention taps never call this use case.
            val record = content.url.takeIf { it.startsWith("http://") || it.startsWith("https://") } ?: url
            recentLinksRepository.markOpened(record)
            recordHistory(content, record)
        }
    }

    private suspend fun recordHistory(content: LinkContent, record: String) {
        runCatching {
            historyRepository.recordSuccessfulView(content.toHistoryView(record, clock.nowEpochMillis()))
        }
    }
}
