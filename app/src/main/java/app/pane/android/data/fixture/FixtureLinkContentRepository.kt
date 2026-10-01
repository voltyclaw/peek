package app.pane.android.data.fixture

import app.pane.android.domain.model.LinkContent
import app.pane.android.domain.repository.LinkContentRepository

class FixtureLinkContentRepository : LinkContentRepository {
    override suspend fun resolve(url: String): Result<LinkContent> =
        FixtureCatalog.contents[url]?.let(Result.Companion::success)
            ?: Result.failure(IllegalArgumentException("Unknown fixture link"))

    override suspend fun peekCached(url: String): LinkContent? = FixtureCatalog.contents[url]

    override suspend fun refresh(url: String): Result<LinkContent> = resolve(url)
}
