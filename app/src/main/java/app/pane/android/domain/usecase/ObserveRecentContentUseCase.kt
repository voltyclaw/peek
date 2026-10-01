package app.pane.android.domain.usecase

import app.pane.android.domain.model.RecentContent
import app.pane.android.domain.repository.LinkContentRepository
import app.pane.android.domain.repository.RecentLinksRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ObserveRecentContentUseCase(
    private val contentRepository: LinkContentRepository,
    private val recentLinksRepository: RecentLinksRepository,
) {
    operator fun invoke(): Flow<List<RecentContent>> =
        recentLinksRepository.observeRecents().map { links ->
            links.map { recent -> RecentContent(recent, contentRepository.peekCached(recent.url)) }
        }
}
