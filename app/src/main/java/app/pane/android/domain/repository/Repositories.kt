package app.pane.android.domain.repository

import app.pane.android.domain.model.HistoryEntry
import app.pane.android.domain.model.HistoryQuery
import app.pane.android.domain.model.HistoryRetention
import app.pane.android.domain.model.HistoryUndo
import app.pane.android.domain.model.HistoryView
import app.pane.android.domain.model.SourceApp
import app.pane.android.domain.model.LinkContent
import app.pane.android.domain.model.LoadProgress
import app.pane.android.domain.model.PreparedMedia
import app.pane.android.domain.model.RecentContent
import app.pane.android.domain.model.RecentLink
import app.pane.android.domain.model.RemoteMedia
import app.pane.android.domain.model.StarCopy
import app.pane.android.domain.model.StarImageBytes
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

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

    /** Puts a removed recent back. The default opens it again at the current time. */
    suspend fun restore(link: RecentLink) = markOpened(link.url)
}

/**
 * Long-lived post history. Separate from [RecentLinksRepository].
 * Writes happen on a successful viewer load, star, and unstar. Unstar keeps the row.
 */
interface HistoryRepository {
    fun observeHistory(query: HistoryQuery = HistoryQuery()): Flow<List<HistoryEntry>>
    suspend fun distinctSourceApps(): List<SourceApp> = emptyList()
    suspend fun recordSuccessfulView(view: HistoryView)
    suspend fun star(url: String, copy: StarCopy, images: StarImageBytes = StarImageBytes())
    /** Clears the star and returns the row as it was. An unstarred row stays. Never deletes. */
    suspend fun unstar(url: String): HistoryUndo? = null

    /** History remove. Does not touch Recents. */
    suspend fun remove(url: String): HistoryUndo? = null

    /** Starred row unstars. An unstarred row is removed. Neither path writes Recents. */
    suspend fun applySwipe(url: String): HistoryUndo? = null

    suspend fun undo(undo: HistoryUndo) = Unit
    suspend fun seedFromRecents(recents: List<RecentLink>)

    /** Same seed, filling pfp, thumb, and media type when a cached post is still on the phone. */
    suspend fun seedFromCached(entries: List<RecentContent>) {
        seedFromRecents(entries.map { it.recentLink })
    }

    /**
     * Deletes the oldest unstarred rows past [cap]. Starred, noted, and tagged rows stay.
     * A false [enabled] flag returns without deleting. The default flag is off.
     */
    suspend fun pruneUnstarred(
        enabled: Boolean = HistoryRetention.ENFORCED,
        cap: Int = HistoryRetention.UNSTARRED_CAP,
    ): List<String>

    /** Clears TikTok display-cache columns on one row. Star, note, tags, and view time stay. */
    suspend fun stripDisplayCache(url: String) = Unit

    /** Clears TikTok display-cache columns for every row of [source]. */
    suspend fun stripSourceDisplayCache(source: SourceApp) = Unit

    /** Writes a refreshed oEmbed cache without counting another view. */
    suspend fun replaceDisplayCache(
        url: String,
        title: String,
        authorName: String,
        handle: String,
        caption: String,
        thumbUrl: String?,
        fetchedAtEpochMillis: Long,
    ) = Unit
}

object NoHistoryRepository : HistoryRepository {
    private val empty = MutableStateFlow<List<HistoryEntry>>(emptyList())

    override fun observeHistory(query: HistoryQuery): Flow<List<HistoryEntry>> = empty
    override suspend fun recordSuccessfulView(view: HistoryView) = Unit
    override suspend fun star(url: String, copy: StarCopy, images: StarImageBytes) = Unit
    override suspend fun unstar(url: String): HistoryUndo? = null
    override suspend fun seedFromRecents(recents: List<RecentLink>) = Unit
    override suspend fun pruneUnstarred(enabled: Boolean, cap: Int): List<String> = emptyList()
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
