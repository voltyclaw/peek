package app.pane.android.domain.usecase

import app.pane.android.domain.model.LinkContent
import app.pane.android.domain.repository.LinkContentRepository

/** Retrieves exactly one next page from a post's existing comments connection. */
class LoadMoreCommentsUseCase(
    private val contentRepository: LinkContentRepository,
) {
    suspend operator fun invoke(url: String): Result<LinkContent> =
        contentRepository.loadMoreComments(url)
}
