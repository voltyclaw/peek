package app.pane.android.domain.usecase

import app.pane.android.domain.model.LinkContent
import app.pane.android.domain.repository.LinkContentRepository
import app.pane.android.domain.repository.LoadProgressListener
import app.pane.android.domain.repository.RecentLinksRepository

class RefreshLinkUseCase(
    private val contentRepository: LinkContentRepository,
    private val recentLinksRepository: RecentLinksRepository,
) {
    suspend operator fun invoke(
        url: String,
        onProgress: LoadProgressListener = LoadProgressListener {},
        onPreview: (LinkContent) -> Unit = {},
    ): Result<LinkContent> =
        contentRepository.refresh(url, onProgress, onPreview).onSuccess { content ->
            val record = content.url.takeIf { it.startsWith("http://") || it.startsWith("https://") } ?: url
            recentLinksRepository.markOpened(record)
        }
}
