package app.pane.android.domain.usecase

import app.pane.android.domain.model.LinkContent
import app.pane.android.domain.repository.LinkContentRepository
import app.pane.android.domain.repository.LoadProgressListener
import app.pane.android.domain.repository.RecentLinksRepository

class OpenLinkUseCase(
    private val contentRepository: LinkContentRepository,
    private val recentLinksRepository: RecentLinksRepository,
) {
    suspend operator fun invoke(
        url: String,
        onProgress: LoadProgressListener = LoadProgressListener {},
        onPreview: (LinkContent) -> Unit = {},
    ): Result<LinkContent> =
        contentRepository.resolve(url, onProgress, onPreview).onSuccess { content ->
            // Success only. A failed or unloadable open does not get a Recents row.
            // Sample deep links record the canonical https post, which is what the row displays.
            val record = content.url.takeIf { it.startsWith("http://") || it.startsWith("https://") } ?: url
            recentLinksRepository.markOpened(record)
        }
}
