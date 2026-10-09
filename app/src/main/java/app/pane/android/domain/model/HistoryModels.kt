package app.pane.android.domain.model

/** One history row. The primary key is the canonical post URL. */
/** Normalized app chip. YouTube, TikTok, and anything else land in [SourceApp.Other]. */
enum class SourceApp { X, Reddit, Facebook, Instagram, Threads, Other }

enum class HistoryScope { All, Starred }

/**
 * History list filter. [scope] and [apps] AND together.
 * An empty [apps] set means every app. Tag and date filters stay P3;
 * [HistoryQueries] is where those clauses will be appended.
 */
data class HistoryQuery(
    val scope: HistoryScope = HistoryScope.All,
    val apps: Set<SourceApp> = emptySet(),
)

/** What a swipe-left does. A starred row unstars; the next swipe removes it. */
enum class HistorySwipeEffect { Unstar, Remove }

object HistorySwipeResolver {
    fun effect(starred: Boolean): HistorySwipeEffect =
        if (starred) HistorySwipeEffect.Unstar else HistorySwipeEffect.Remove
}

/**
 * Long-press menu and TalkBack custom actions.
 * A menu Remove deletes the row even when it is starred. Swipes unstar first.
 */
enum class HistoryRowAction(val label: String) {
    Open("Open"),
    Star("Star"),
    RemoveStar("Remove star"),
    Remove("Remove from History"),
    Share("Share"),
    CopyLink("Copy link"),
    NoteAndTags("Note & tags"),
}

object HistoryRowActions {
    fun menu(starred: Boolean): List<HistoryRowAction> = listOf(
        if (starred) HistoryRowAction.RemoveStar else HistoryRowAction.Star,
        HistoryRowAction.Remove,
        HistoryRowAction.Share,
        HistoryRowAction.CopyLink,
        HistoryRowAction.NoteAndTags,
    )

    fun longPress(starred: Boolean): List<HistoryRowAction> = menu(starred)

    fun talkBack(starred: Boolean): List<HistoryRowAction> = listOf(
        if (starred) HistoryRowAction.RemoveStar else HistoryRowAction.Star,
        HistoryRowAction.Remove,
    )
}

/** Exact row to put back after unstar or remove, including the saved star copy. */
class HistoryUndo(
    val row: HistoryEntry,
    val thumbBytes: ByteArray? = null,
    val profileBytes: ByteArray? = null,
)

data class HistoryEntry(
    val url: String,
    val source: String,
    val sourceApp: SourceApp,
    val title: String,
    val authorName: String,
    val handle: String,
    val caption: String,
    val thumbUrl: String?,
    val pfpUrl: String?,
    val pinnedThumbPath: String?,
    val pinnedPfpPath: String?,
    val mediaType: String,
    val note: String?,
    val firstViewedAt: Long,
    val lastViewedAt: Long,
    val viewCount: Int,
    val starredAt: Long?,
)

/** Text snapshot captured when a post view succeeds. Image bytes are pinned only on star. */
data class HistoryView(
    val url: String,
    val source: String,
    val title: String,
    val authorName: String,
    val handle: String,
    val caption: String,
    val thumbUrl: String?,
    val pfpUrl: String?,
    val mediaType: String,
    val viewedAtEpochMillis: Long,
)

/** Fields a star keeps after the live post is gone. */
data class StarCopy(
    val title: String,
    val authorName: String,
    val handle: String,
    val caption: String,
    val thumbUrl: String? = null,
    val pfpUrl: String? = null,
)

/** Thumbnail and profile-photo bytes. No video. The store compresses these under the size cap. */
class StarImageBytes(
    val thumbnail: ByteArray? = null,
    val profile: ByteArray? = null,
)

const val HISTORY_TEXT_LIMIT = 2_000

fun LinkContent.toHistoryView(recordUrl: String, viewedAtEpochMillis: Long): HistoryView {
    val handle = historyHandle()
    return HistoryView(
        url = recordUrl,
        source = source.name,
        title = title.take(HISTORY_TEXT_LIMIT),
        authorName = author.name.take(HISTORY_TEXT_LIMIT),
        handle = handle.take(80),
        caption = title.take(HISTORY_TEXT_LIMIT),
        thumbUrl = remoteUrl(thumbnail) ?: remoteUrl(media.location),
        pfpUrl = author.avatarUrl,
        mediaType = historyMediaType(),
        viewedAtEpochMillis = viewedAtEpochMillis,
    )
}

private fun LinkContent.historyHandle(): String {
    when (source) {
        LinkSource.Instagram -> {
            val user = (sourceMetadata as? InstagramMetadata)?.authorUsername
            if (!user.isNullOrBlank()) return user.removePrefix("@").trim()
        }
        LinkSource.Reddit -> {
            val sub = (sourceMetadata as? RedditMetadata)?.subreddit?.removePrefix("r/")?.trim()
            if (!sub.isNullOrBlank()) return sub
            val user = (sourceMetadata as? RedditMetadata)?.author
            if (!user.isNullOrBlank() && !user.equals("[deleted]", ignoreCase = true)) return user.trim()
        }
        else -> Unit
    }
    val meta = author.metadata.trim()
    if (meta.startsWith("@")) {
        val token = meta.removePrefix("@").substringBefore(' ').substringBefore('·').trim()
        if (token.isNotEmpty() && !AuthorLines.isSourceLabel(token)) return token
    }
    return ""
}

private fun LinkContent.historyMediaType(): String {
    val badge = media.badge.orEmpty()
    val video = kind == LinkKind.Video || badge.contains("VIDEO", ignoreCase = true) || when (val meta = sourceMetadata) {
        is RedditMetadata -> meta.mediaItems.any { !it.videoUrl.isNullOrBlank() || it.videos.isNotEmpty() }
        is InstagramMetadata -> meta.videoVariants.isNotEmpty() || meta.mediaItems.any { it.videoVariants.isNotEmpty() }
        is ExternalPostMetadata -> meta.mediaItems.any { !it.videoUrl.isNullOrBlank() || it.videos.isNotEmpty() }
        else -> false
    }
    if (video) return HistoryLedger.VIDEO
    val thumb = remoteUrl(thumbnail) ?: remoteUrl(media.location)
    return if (thumb != null) HistoryLedger.IMAGE else HistoryLedger.NONE
}

private fun remoteUrl(location: MediaLocation): String? =
    (location as? MediaLocation.Remote)?.url?.takeIf { it.isNotBlank() }

/**
 * P2 retention. Starred, noted, and tagged rows are exempt.
 * [HistoryRetention.ENFORCED] stays false until the prune job is turned on.
 */
data class HistoryRetentionCandidate(
    val url: String,
    val lastViewedAt: Long,
    val starred: Boolean,
    val hasNote: Boolean,
    val hasTags: Boolean,
)

object HistoryRetention {
    const val UNSTARRED_CAP = 2_000

    /** Off in this build. The selector and delete path exist so P2 can turn the job on. */
    const val ENFORCED = false

    fun urlsToPrune(
        rows: List<HistoryRetentionCandidate>,
        cap: Int = UNSTARRED_CAP,
    ): List<String> {
        val eligible = rows
            .filter { row -> !row.starred && !row.hasNote && !row.hasTags }
            .sortedWith(compareBy({ it.lastViewedAt }, { it.url }))
        val overflow = (eligible.size - cap).coerceAtLeast(0)
        return eligible.take(overflow).map { it.url }
    }
}
