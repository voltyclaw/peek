package app.pane.android.data.tiktok

import app.pane.android.domain.model.Author
import app.pane.android.domain.model.LinkContent
import app.pane.android.domain.model.LinkKind
import app.pane.android.domain.model.LinkSource
import app.pane.android.domain.model.Media
import app.pane.android.domain.model.MediaLocation
import app.pane.android.domain.model.TikTokMetadata
import app.pane.android.domain.repository.LinkContentRepository
import app.pane.android.domain.repository.LoadProgressListener
import app.pane.android.domain.tiktok.MapTikTokConsentStore
import app.pane.android.domain.tiktok.TikTokConsentPolicy
import app.pane.android.domain.tiktok.TikTokConsentStore
import app.pane.android.domain.tiktok.TikTokCopyRetention
import app.pane.android.domain.tiktok.TikTokIds
import app.pane.android.domain.tiktok.TikTokLinkKind
import app.pane.android.domain.tiktok.TikTokLinks
import app.pane.android.domain.tiktok.TikTokOEmbedApi
import app.pane.android.domain.tiktok.TikTokOEmbedResult
import app.pane.android.domain.tiktok.TikTokRedirects

/**
 * Resolves a TikTok URL without contacting TikTok until consent is stored.
 * After the tap, oEmbed fills the display cache. Playback stays on player v1.
 */
class TikTokLinkContentRepository(
    private val consent: TikTokConsentStore = MapTikTokConsentStore(),
    private val api: TikTokOEmbedApi = InertTikTokOEmbed,
    private val redirects: TikTokRedirects = TikTokRedirects { null },
    private val nowEpochSeconds: () -> Long = { System.currentTimeMillis() / 1000L },
) : LinkContentRepository {
    fun supports(url: String): Boolean = TikTokUrls.supports(url)

    override suspend fun resolve(url: String): Result<LinkContent> {
        val link = TikTokUrls.parse(url) ?: return Result.failure(app.pane.android.domain.model.SourceFailure.Unsupported(url))
        if (!TikTokLinks.supports(url)) return Result.failure(app.pane.android.domain.model.SourceFailure.Unsupported(url))
        if (link.kind == TikTokLinkKind.Live) return Result.success(live(url, link.handle))
        if (!hasConsent()) return Result.success(stub(url, link))
        val resolved = if (link.kind == TikTokLinkKind.Short) {
            val landed = redirects.resolve(url) ?: return Result.failure(IllegalStateException("Short link did not resolve"))
            TikTokUrls.parse(landed) ?: return Result.failure(IllegalStateException("Short link did not resolve"))
        } else {
            link
        }
        if (resolved.kind == TikTokLinkKind.Live) return Result.success(live(resolved.canonicalUrl ?: url, resolved.handle))
        val videoId = resolved.videoId ?: return Result.failure(IllegalStateException("Missing TikTok id"))
        val page = resolved.canonicalUrl ?: url
        return when (val result = api.fetch(videoId, page)) {
            is TikTokOEmbedResult.Ready -> Result.success(
                loaded(
                    url,
                    resolved,
                    TikTokCopyRetention.caption(result.embed.caption),
                    result.embed.authorName,
                    result.embed.authorUrl,
                    result.embed.thumbnailUrl,
                    false,
                ),
            )
            is TikTokOEmbedResult.Removed -> Result.success(loaded(url, resolved, "", "", "", "", removed = true))
            is TikTokOEmbedResult.Failed -> Result.success(loaded(url, resolved, "", "", "", "", detailsFailed = true))
        }
    }

    override suspend fun resolve(url: String, onProgress: LoadProgressListener): Result<LinkContent> = resolve(url)

    override suspend fun peekCached(url: String): LinkContent? = null

    override suspend fun loadMoreComments(url: String): Result<LinkContent> =
        Result.failure(IllegalStateException("TikTok comments stay on TikTok"))

    override suspend fun refresh(url: String): Result<LinkContent> {
        TikTokUrls.parse(url)?.videoId?.let(api::invalidate)
        return resolve(url)
    }

    private fun hasConsent(): Boolean {
        val saved = consent.read() ?: return false
        return saved.policyVersion == TikTokConsentPolicy.VERSION && saved.acceptedAtEpochMillis > 0L
    }

    private fun stub(url: String, link: app.pane.android.domain.tiktok.TikTokLink): LinkContent {
        val handle = link.handle.orEmpty()
        return content(
            url = url,
            title = "",
            authorName = "",
            handle = handle,
            authorUrl = handle.takeIf { it.isNotBlank() }?.let { "https://www.tiktok.com/@$it" }.orEmpty(),
            thumb = "",
            metadata = TikTokMetadata(
                videoId = link.videoId.orEmpty(),
                handle = handle,
                authorUrl = handle.takeIf { it.isNotBlank() }?.let { "https://www.tiktok.com/@$it" }.orEmpty(),
                postedAtEpochSeconds = link.videoId?.let { TikTokIds.postedAtEpochSeconds(it, nowEpochSeconds()) },
                photo = link.kind == TikTokLinkKind.Photo,
                shortLink = link.kind == TikTokLinkKind.Short,
            ),
            photo = link.kind == TikTokLinkKind.Photo,
        )
    }

    private fun loaded(
        requested: String,
        link: app.pane.android.domain.tiktok.TikTokLink,
        caption: String,
        authorName: String,
        authorUrl: String,
        thumb: String,
        detailsFailed: Boolean = false,
        removed: Boolean = false,
    ): LinkContent {
        val handle = handleOf(authorUrl).ifBlank { link.handle.orEmpty() }
        val profile = authorUrl.ifBlank { handle.takeIf { it.isNotBlank() }?.let { "https://www.tiktok.com/@$it" }.orEmpty() }
        val id = link.videoId.orEmpty()
        return content(
            url = link.canonicalUrl ?: requested,
            title = caption,
            authorName = authorName,
            handle = handle,
            authorUrl = profile,
            thumb = thumb,
            metadata = TikTokMetadata(
                videoId = id,
                handle = handle,
                authorUrl = profile,
                caption = caption,
                postedAtEpochSeconds = TikTokIds.postedAtEpochSeconds(id, nowEpochSeconds()),
                photo = link.kind == TikTokLinkKind.Photo,
                detailsFailed = detailsFailed,
                removed = removed,
            ),
            photo = link.kind == TikTokLinkKind.Photo,
        )
    }

    private fun live(url: String, handle: String?): LinkContent = content(
        url = url,
        title = "",
        authorName = "",
        handle = handle.orEmpty(),
        authorUrl = handle?.let { "https://www.tiktok.com/@$it" }.orEmpty(),
        thumb = "",
        metadata = TikTokMetadata(videoId = "", handle = handle.orEmpty(), live = true),
        photo = false,
    )

    private fun content(
        url: String,
        title: String,
        authorName: String,
        handle: String,
        authorUrl: String,
        thumb: String,
        metadata: TikTokMetadata,
        photo: Boolean,
    ): LinkContent = LinkContent(
        url = url,
        title = title,
        source = LinkSource.TikTok,
        kind = if (photo) LinkKind.Post else LinkKind.Video,
        thumbnail = MediaLocation.Remote(thumb),
        media = Media(location = MediaLocation.Remote(thumb), contentDescription = title),
        author = Author(
            name = authorName,
            metadata = handle.takeIf { it.isNotBlank() }?.let { "@$it" }.orEmpty(),
            avatarUrl = null,
        ),
        commentCount = 0,
        comments = emptyList(),
        sourceMetadata = metadata,
    )

    private fun handleOf(authorUrl: String): String {
        val path = runCatching { java.net.URI(authorUrl).path }.getOrNull().orEmpty().trim('/')
        return path.removePrefix("@").substringBefore('/').takeIf { it.isNotBlank() }.orEmpty()
    }
}

private object InertTikTokOEmbed : TikTokOEmbedApi {
    override fun fetch(videoId: String, pageUrl: String): TikTokOEmbedResult = TikTokOEmbedResult.Failed
}
