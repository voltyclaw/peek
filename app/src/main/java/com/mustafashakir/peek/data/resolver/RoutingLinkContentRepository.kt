package com.mustafashakir.peek.data.resolver

import com.mustafashakir.peek.domain.model.LinkContent
import com.mustafashakir.peek.domain.repository.LinkContentRepository
import com.mustafashakir.peek.domain.repository.LoadProgressListener

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

    override suspend fun resolve(url: String, onProgress: LoadProgressListener): Result<LinkContent> =
        route(url)?.resolve(url, onProgress) ?: unsupported(url)

    override suspend fun peekCached(url: String): LinkContent? = route(url)?.peekCached(url)

    override suspend fun loadMoreComments(url: String): Result<LinkContent> =
        route(url)?.loadMoreComments(url) ?: unsupported(url)

    override suspend fun refresh(url: String): Result<LinkContent> =
        refresh(url, LoadProgressListener {})

    override suspend fun refresh(url: String, onProgress: LoadProgressListener): Result<LinkContent> =
        route(url)?.refresh(url, onProgress) ?: unsupported(url)

    private fun route(url: String): LinkContentRepository? =
        routes.firstOrNull { it.supports(url) }?.repository

    private fun unsupported(url: String): Result<LinkContent> =
        Result.failure(IllegalArgumentException("Unsupported link: $url"))
}
