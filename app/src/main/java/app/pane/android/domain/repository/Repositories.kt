package app.pane.android.domain.repository

import app.pane.android.domain.model.LinkContent
import app.pane.android.domain.model.LoadProgress
import app.pane.android.domain.model.PreparedMedia
import app.pane.android.domain.model.RecentLink
import app.pane.android.domain.model.RemoteMedia
import kotlinx.coroutines.flow.Flow

fun interface LoadProgressListener {
    fun onProgress(progress: LoadProgress)
}

interface LinkContentRepository {
    suspend fun resolve(url: String): Result<LinkContent>

    /** Same as [resolve], additionally reporting progress if the underlying source can. */
    suspend fun resolve(url: String, onProgress: LoadProgressListener): Result<LinkContent> = resolve(url)

    /**
     * Same as [resolve], and may call [onPreview] with a usable post before the full
     * result (comments, author thread) is ready. The returned result is still the full post.
     */
    suspend fun resolve(
        url: String,
        onProgress: LoadProgressListener,
        onPreview: (LinkContent) -> Unit,
    ): Result<LinkContent> = resolve(url, onProgress)

    /** Returns cached content for [url] without triggering a network load, or null if not cached. */
    suspend fun peekCached(url: String): LinkContent?

    /** Loads the next cached comments page, if the source has one. */
    suspend fun loadMoreComments(url: String): Result<LinkContent> = Result.failure(
        UnsupportedOperationException("Comment pagination is not supported by this source"),
    )

    /** Discards any cached content for [url] and resolves it again from the network. */
    suspend fun refresh(url: String): Result<LinkContent>

    /** Same as [refresh], additionally reporting progress if the underlying source can. */
    suspend fun refresh(url: String, onProgress: LoadProgressListener): Result<LinkContent> = refresh(url)

    /** Same as [refresh], and may call [onPreview] with a usable post before the full result. */
    suspend fun refresh(
        url: String,
        onProgress: LoadProgressListener,
        onPreview: (LinkContent) -> Unit,
    ): Result<LinkContent> = refresh(url, onProgress)
}

interface RecentLinksRepository {
    fun observeRecents(): Flow<List<RecentLink>>
    suspend fun markOpened(url: String)
    suspend fun remove(url: String)
    suspend fun clear()
}

interface MediaRepository {
    /**
     * Fetches all requested media into short-lived cache files.
     *
     * Implementations must either return every requested item or fail and clean up partial output.
     */
    suspend fun prepareForSharing(media: List<RemoteMedia>): Result<List<PreparedMedia>>

    /** Saves each requested item to public downloads and returns the number successfully saved. */
    suspend fun download(media: List<RemoteMedia>): Int
}
