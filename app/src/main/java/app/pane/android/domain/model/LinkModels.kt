package app.pane.android.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class LinkSource { Instagram, YouTube, TikTok, Reddit, Facebook, X }

@Serializable
enum class LinkKind { Post, Video }

@Serializable
enum class BundledImageKey { Kyoto, Material, FieldNotes, Coast }

@Serializable
sealed interface MediaLocation {
    @Serializable
    data class Bundled(val key: BundledImageKey) : MediaLocation

    @Serializable
    data class Remote(val url: String) : MediaLocation
}

@Serializable
data class Media(
    val location: MediaLocation,
    val contentDescription: String,
    val badge: String? = null,
    val duration: String? = null,
)

@Serializable
data class Author(
    val name: String,
    val metadata: String,
    val avatarUrl: String? = null,
)

@Serializable
sealed interface SourceMetadata

@Serializable
data class InstagramVideoVariant(
    val type: Int,
    val url: String,
    val width: Int? = null,
    val height: Int? = null,
)

/** One playable URL the source actually returned. Adaptive streams are DASH or HLS. */
@Serializable
data class PlayableVideo(
    val url: String,
    val width: Int? = null,
    val height: Int? = null,
    val bitrate: Int? = null,
    val adaptive: Boolean = false,
)

@Serializable
data class InstagramMediaItem(
    val id: String,
    val imageUrl: String,
    val contentDescription: String,
    val videoVariants: List<InstagramVideoVariant> = emptyList(),
    val width: Int? = null,
    val height: Int? = null,
)

@Serializable
data class InstagramMetadata(
    val postId: String,
    val shortcode: String,
    val code: String,
    val takenAtEpochSeconds: Long,
    val likeCount: Int,
    val commentCount: Int,
    val authorId: String,
    val authorUsername: String,
    val authorFullName: String?,
    val authorProfilePictureUrl: String?,
    val authorIsVerified: Boolean,
    val videoVariants: List<InstagramVideoVariant>,
    val mediaItems: List<InstagramMediaItem> = emptyList(),
    val commentsEndCursor: String? = null,
) : SourceMetadata

@Serializable
data class RedditMediaItem(
    val id: String,
    val imageUrl: String,
    val contentDescription: String,
    val videoUrl: String? = null,
    val width: Int? = null,
    val height: Int? = null,
    val durationSeconds: Int? = null,
    val videos: List<PlayableVideo> = emptyList(),
)

@Serializable
data class ExternalMediaItem(
    val id: String,
    val imageUrl: String,
    val contentDescription: String,
    val videoUrl: String? = null,
    val videos: List<PlayableVideo> = emptyList(),
)

/** One post in an X author's own chain. Empty for Facebook. */
@Serializable
data class ExternalThreadPost(
    val id: String,
    val author: String,
    val text: String,
)

/** Whether more X replies can be fetched, the public list is complete, or the guest page stopped. */
@Serializable
enum class XReplyContinuation { More, Exhausted, Blocked }

/** Public Facebook and X posts. An X author thread is separate from other people's replies. */
@Serializable
data class ExternalPostMetadata(
    val postId: String,
    val mediaItems: List<ExternalMediaItem> = emptyList(),
    val authorThread: List<ExternalThreadPost> = emptyList(),
    val authorThreadPartial: Boolean = false,
    val repliesCursor: String? = null,
    val replyContinuation: XReplyContinuation? = null,
    val articleTitle: String? = null,
    val articlePreview: String? = null,
    val articleBody: String? = null,
    val articleCoverUrl: String? = null,
    val articleUrl: String? = null,
    val quoteAuthor: String? = null,
    val quoteHandle: String? = null,
    val quoteText: String? = null,
    val quoteUrl: String? = null,
    val linkCards: List<ExternalLinkCard> = emptyList(),
) : SourceMetadata

@Serializable
enum class YouTubeCommentsState { Ready, Unavailable, Off, Failed }

/** YouTube page kept in memory for the viewer session. Comments are not written to disk. */
@Serializable
data class YouTubeMetadata(
    val videoId: String,
    val description: String = "",
    val metaLine: String = "",
    val channelUrl: String = "",
    val handle: String = "",
    val commentPageToken: String? = null,
    val commentPagesLoaded: Int = 0,
    val commentsHardWall: Boolean = false,
    val commentsState: YouTubeCommentsState = YouTubeCommentsState.Ready,
    val embeddable: Boolean = true,
    val ageRestricted: Boolean = false,
    val isShort: Boolean = false,
) : SourceMetadata

@Serializable
data class RedditMetadata(
    val postId: String,
    val subreddit: String,
    val permalink: String,
    val score: Int,
    val commentCount: Int,
    val createdUtcEpochSeconds: Long,
    val author: String,
    val over18: Boolean,
    val spoiler: Boolean,
    val mediaItems: List<RedditMediaItem> = emptyList(),
    val moreCommentIds: List<String> = emptyList(),
) : SourceMetadata

@Serializable
data class ExternalLinkCard(
    val url: String,
    val label: String,
    val title: String = "",
)

@Serializable
data class Comment(
    val id: String,
    val author: String,
    val initial: String,
    val age: String,
    val body: String,
    val isCreator: Boolean = false,
    val replies: List<Comment> = emptyList(),
    val avatarUrl: String? = null,
    val handle: String? = null,
    val cardTitle: String? = null,
    val cardBody: String? = null,
    val cardUrl: String? = null,
)

@Serializable
data class LinkContent(
    val url: String,
    val title: String,
    val source: LinkSource,
    val kind: LinkKind,
    val thumbnail: MediaLocation,
    val media: Media,
    val author: Author,
    val commentCount: Int,
    val comments: List<Comment>,
    val sourceMetadata: SourceMetadata? = null,
)

data class RecentLink(
    val url: String,
    val openedAtEpochMillis: Long,
)

data class RecentContent(
    val recentLink: RecentLink,
    val content: LinkContent?,
)

enum class LoadStage { Connecting, FetchingPage, ExtractingContent }

data class LoadProgress(val fraction: Float, val stage: LoadStage)

fun interface Clock {
    fun nowEpochMillis(): Long
}

object SystemClock : Clock {
    override fun nowEpochMillis(): Long = System.currentTimeMillis()
}
