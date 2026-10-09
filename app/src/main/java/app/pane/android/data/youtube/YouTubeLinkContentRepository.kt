package app.pane.android.data.youtube

import app.pane.android.domain.model.Author
import app.pane.android.domain.model.LinkContent
import app.pane.android.domain.model.LinkKind
import app.pane.android.domain.model.LinkSource
import app.pane.android.domain.model.Media
import app.pane.android.domain.model.MediaLocation
import app.pane.android.domain.repository.LinkContentRepository
import app.pane.android.domain.repository.LoadProgressListener

/**
 * Local description of a YouTube URL. This does not call the YouTube Data API and does not
 * build a player. The viewer shows a poster until consent, then loads youtube-nocookie.com.
 */
class YouTubeLinkContentRepository : LinkContentRepository {
    fun supports(url: String): Boolean = YouTubeUrls.supports(url)

    override suspend fun resolve(url: String): Result<LinkContent> {
        if (!supports(url)) return Result.failure(IllegalArgumentException("Unsupported link: $url"))
        return Result.success(content(url))
    }

    override suspend fun resolve(url: String, onProgress: LoadProgressListener): Result<LinkContent> = resolve(url)

    override suspend fun peekCached(url: String): LinkContent? = null

    override suspend fun refresh(url: String): Result<LinkContent> = resolve(url)
}

private fun content(url: String): LinkContent = LinkContent(
    url = url,
    title = "",
    source = LinkSource.YouTube,
    kind = LinkKind.Video,
    thumbnail = MediaLocation.Remote(""),
    media = Media(
        location = MediaLocation.Remote(""),
        contentDescription = "",
    ),
    author = Author(name = "", metadata = ""),
    commentCount = 0,
    comments = emptyList(),
)
