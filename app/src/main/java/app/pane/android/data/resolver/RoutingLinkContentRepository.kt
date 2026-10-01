package app.pane.android.data.resolver

import app.pane.android.domain.model.LinkContent
import app.pane.android.domain.model.LinkShims
import app.pane.android.domain.repository.LinkContentRepository
import app.pane.android.domain.repository.LoadProgressListener

/**
 * Dispatches a URL to the first source repository that [Route.supports] it.
 * Source repositories keep their own resolver chains.
 */
class RoutingLinkContentRepository(
    private val routes: List<Route>,
) : LinkContentRepository {
    init {
        require(routes.isNotEmpty()) { "RoutingLinkContentRepository requires at least one route" }
    }

    data class Route(
        val supports: (String) -> Boolean,
        val repository: LinkContentRepository,
    )

    override suspend fun resolve(url: String): Result<LinkContent> =
        resolve(url, LoadProgressListener {})

    override suspend fun resolve(url: String, onProgress: LoadProgressListener): Result<LinkContent> {
        val opened = LinkShims.unwrap(url)
        return route(opened)?.resolve(opened, onProgress) ?: unsupported(opened)
    }

    override suspend fun peekCached(url: String): LinkContent? {
        val opened = LinkShims.unwrap(url)
        return route(opened)?.peekCached(opened)
    }

    override suspend fun loadMoreComments(url: String): Result<LinkContent> {
        val opened = LinkShims.unwrap(url)
        return route(opened)?.loadMoreComments(opened) ?: unsupported(opened)
    }

    override suspend fun refresh(url: String): Result<LinkContent> =
        refresh(url, LoadProgressListener {})

    override suspend fun refresh(url: String, onProgress: LoadProgressListener): Result<LinkContent> {
        val opened = LinkShims.unwrap(url)
        return route(opened)?.refresh(opened, onProgress) ?: unsupported(opened)
    }

    private fun route(url: String): LinkContentRepository? =
        routes.firstOrNull { it.supports(url) }?.repository

    private fun unsupported(url: String): Result<LinkContent> =
        Result.failure(IllegalArgumentException("Unsupported link: $url"))
}
