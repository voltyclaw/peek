package app.pane.android.data.sample

import app.pane.android.domain.model.Author
import app.pane.android.domain.model.Comment
import app.pane.android.domain.model.ExternalMediaItem
import app.pane.android.domain.model.ExternalPostMetadata
import app.pane.android.domain.model.ExternalThreadPost
import app.pane.android.domain.model.InstagramMediaItem
import app.pane.android.domain.model.InstagramMetadata
import app.pane.android.domain.model.InstagramVideoVariant
import app.pane.android.domain.model.LinkContent
import app.pane.android.domain.model.LinkKind
import app.pane.android.domain.model.LinkSource
import app.pane.android.domain.model.Media
import app.pane.android.domain.model.MediaLocation
import app.pane.android.domain.model.PlayableVideo
import app.pane.android.domain.model.RedditMediaItem
import app.pane.android.domain.model.RedditMetadata
import java.net.URI

/**
 * Offline posts shaped like each source resolver's [LinkContent].
 * Canonical https URLs are what the viewer, chip, and Open button see.
 */
object SamplePosts {
    enum class Kind { Post, Error, Unsupported }

    data class Entry(
        val id: String,
        val group: String,
        val label: String,
        val canonicalUrl: String,
        val kind: Kind = Kind.Post,
        val maxPage: Int = 0,
    )

    val entries: List<Entry> = listOf(
        Entry("x-image", "X", "Image", "https://x.com/mayaren/status/1842700101"),
        Entry("x-video", "X", "Video", "https://x.com/mayaren/status/1842700102"),
        Entry("x-text", "X", "Text", "https://x.com/mayaren/status/1842700103"),
        Entry("x-link", "X", "Link card", "https://x.com/mayaren/status/1842700104"),
        Entry("x-thread", "X", "Author thread", "https://x.com/mayaren/status/1842700105"),
        Entry("x-gallery", "X", "Multi-image", "https://x.com/mayaren/status/1842700106"),
        Entry("ig-image", "Instagram", "Image", "https://www.instagram.com/p/sampleimg1/", maxPage = 1),
        Entry("ig-carousel", "Instagram", "Carousel", "https://www.instagram.com/p/samplecar1/"),
        Entry("ig-reel", "Instagram", "Reel", "https://www.instagram.com/reel/samplereel1/"),
        Entry("fb-text", "Facebook", "Text", "https://www.facebook.com/mayaren/posts/sampletext1"),
        Entry("fb-image", "Facebook", "Image", "https://www.facebook.com/mayaren/posts/samplephoto1"),
        Entry("fb-reel", "Facebook", "Video", "https://www.facebook.com/reel/samplereel1"),
        Entry("reddit-text", "Reddit", "Self text", "https://www.reddit.com/r/hiking/comments/samplehike/first_solo/", maxPage = 1),
        Entry("reddit-image", "Reddit", "Image", "https://www.reddit.com/r/hiking/comments/sampleridge/ridge_light/"),
        Entry("reddit-video", "Reddit", "Video", "https://www.reddit.com/r/hiking/comments/sampleclip/creek_clip/"),
        Entry("reddit-link", "Reddit", "Link post", "https://www.reddit.com/r/hiking/comments/samplelink/trail_report/"),
        Entry("error", "Other", "Couldn't load", "https://x.com/mayaren/status/1842700199", Kind.Error),
        Entry(
            "unsupported",
            "Other",
            "Marketplace",
            "https://www.facebook.com/marketplace/item/sampleitem",
            Kind.Unsupported,
        ),
    )

    private val byId = entries.associateBy { it.id }
    private val byUrl = entries.associateBy { it.canonicalUrl }

    fun entry(id: String): Entry? = byId[id]

    fun entryForCanonical(url: String): Entry? = byUrl[url.trim()]

    fun deepLink(id: String): String = "pane://sample/$id"

    fun canonicalForDeepLink(url: String): String? {
        val uri = runCatching { URI(url.trim()) }.getOrNull() ?: return null
        if (!uri.scheme.equals("pane", ignoreCase = true)) return null
        if (!uri.host.equals("sample", ignoreCase = true)) return null
        val id = uri.path.orEmpty().trim('/').substringBefore('/')
        return byId[id]?.canonicalUrl
    }

    fun render(entry: Entry, files: SampleMedia.Files, page: Int): LinkContent = when (entry.id) {
        "x-image" -> xImage(entry, files)
        "x-video" -> xVideo(entry, files)
        "x-text" -> xText(entry)
        "x-link" -> xLink(entry, files)
        "x-thread" -> xThread(entry, files)
        "x-gallery" -> xGallery(entry, files)
        "ig-image" -> igImage(entry, files, page)
        "ig-carousel" -> igCarousel(entry, files)
        "ig-reel" -> igReel(entry, files)
        "fb-text" -> fbText(entry)
        "fb-image" -> fbImage(entry, files)
        "fb-reel" -> fbReel(entry, files)
        "reddit-text" -> redditText(entry, page)
        "reddit-image" -> redditImage(entry, files)
        "reddit-video" -> redditVideo(entry, files)
        "reddit-link" -> redditLink(entry, files)
        "error" -> errorStub(entry)
        "unsupported" -> unsupportedStub(entry)
        else -> error("Unknown sample ${entry.id}")
    }

    private fun xImage(entry: Entry, files: SampleMedia.Files): LinkContent {
        val text = "First light on the ridge, before the lot fills and the quiet leaves."
        val comments = listOf(
            reply("x-img-1", "jonah", "The color on the rock is doing all the work.", "2h"),
            reply("x-img-2", "leila", "I know that switchback. It stays empty if you start early.", "1h"),
            reply("x-img-3", "noah", "Saving this for the next clear morning.", "18m"),
        )
        return x(
            entry = entry,
            id = "1842700101",
            text = text,
            images = listOf(files.ridge),
            comments = comments,
            commentCount = 18,
        )
    }

    private fun xVideo(entry: Entry, files: SampleMedia.Files): LinkContent = x(
        entry = entry,
        id = "1842700102",
        text = "Three seconds of the creek where the path crosses. The rest of the walk was just as small.",
        images = listOf(files.ridge),
        video = files.video,
        comments = listOf(reply("x-vid-1", "ada", "That sound is the whole point.", "44m")),
        commentCount = 1,
    )

    private fun xText(entry: Entry): LinkContent = x(
        entry = entry,
        id = "1842700103",
        text = "The trail does not owe you a view. Some mornings the fog is the whole point, and the ridge can wait.",
        comments = listOf(
            reply("x-text-1", "sam", "Fog mornings are the ones I remember.", "3h"),
            reply("x-text-2", "noor", "Agreed. The view can wait.", "2h"),
        ),
        commentCount = 2,
    )

    private fun xLink(entry: Entry, files: SampleMedia.Files): LinkContent = x(
        entry = entry,
        id = "1842700104",
        text = "The notes from this loop are better than my memory of it.\n\nhttps://longtrail.review/quiet-loop",
        images = listOf(files.link),
        comments = listOf(reply("x-link-1", "ravi", "Bookmarked. The lot note is the useful part.", "5h")),
        commentCount = 1,
    )

    private fun xThread(entry: Entry, files: SampleMedia.Files): LinkContent {
        val opened = "1842700105"
        val thread = listOf(
            ExternalThreadPost("1842700095", "Maya Ren", "Starting from the lower lot while it is still dark."),
            ExternalThreadPost("1842700096", "Maya Ren", "The first switchback is just trees and cold air."),
            ExternalThreadPost("1842700097", "Maya Ren", "Water is running at the crossing. Shoes stayed dry."),
            ExternalThreadPost("1842700098", "Maya Ren", "The fog lifted in one strip and left the rest of the ridge alone."),
            ExternalThreadPost("1842700099", "Maya Ren", "I sat until the bottle was half empty. No one else came up."),
            ExternalThreadPost(opened, "Maya Ren", "Walking down now. The lot is still mostly empty, which is the whole trick."),
        )
        return x(
            entry = entry,
            id = opened,
            text = thread.last().text,
            images = listOf(files.kyoto),
            thread = thread,
            comments = listOf(reply("x-thread-1", "leila", "This is the version of the hike I wanted.", "26m")),
            commentCount = 1,
        )
    }

    private fun xGallery(entry: Entry, files: SampleMedia.Files): LinkContent = x(
        entry = entry,
        id = "1842700106",
        text = "Four frames from the same hour. The light moved faster than I did.",
        images = listOf(files.ridge, files.link, files.kyoto, files.material),
        comments = listOf(reply("x-gal-1", "jonah", "The third frame is the one.", "1h")),
        commentCount = 1,
    )

    private fun x(
        entry: Entry,
        id: String,
        text: String,
        images: List<String> = emptyList(),
        video: String? = null,
        comments: List<Comment> = emptyList(),
        commentCount: Int = comments.size,
        thread: List<ExternalThreadPost> = emptyList(),
    ): LinkContent {
        val items = externalItems(id, text, images, video)
        return LinkContent(
            url = entry.canonicalUrl,
            title = text,
            source = LinkSource.X,
            kind = if (video != null) LinkKind.Video else LinkKind.Post,
            thumbnail = MediaLocation.Remote(images.firstOrNull().orEmpty()),
            media = Media(
                location = MediaLocation.Remote(images.firstOrNull().orEmpty()),
                contentDescription = text.take(200),
                badge = when {
                    video != null -> "VIDEO"
                    images.size > 1 -> "GALLERY"
                    images.isNotEmpty() -> "PHOTO"
                    else -> "TEXT"
                },
            ),
            author = Author(name = "Maya Ren", metadata = "X"),
            commentCount = commentCount,
            comments = comments,
            sourceMetadata = ExternalPostMetadata(
                postId = id,
                mediaItems = items,
                authorThread = thread,
            ),
        )
    }

    private fun igImage(entry: Entry, files: SampleMedia.Files, page: Int): LinkContent {
        val caption = "The ridge before anyone else parks. One frame, then I put the phone away."
        val first = listOf(
            reply("ig-img-1", "jonah.park", "The quiet reads in the frame.", "2h"),
            reply(
                "ig-img-2",
                "maya.ren",
                "It stayed that way for another hour.",
                "1h",
                creator = true,
                replies = listOf(reply("ig-img-2a", "leila.m", "That is the version I want.", "48m")),
            ),
        )
        val more = listOf(
            reply("ig-img-3", "noah.r", "Adding the lower lot to my list.", "22m"),
            reply("ig-img-4", "ada.lin", "The light on the rock is enough.", "11m"),
            reply("ig-img-5", "sam.ote", "Going next clear morning.", "4m"),
        )
        val comments = if (page <= 0) first else first + more
        val item = igItem("ig-image-0", files.material, caption)
        return instagram(
            entry = entry,
            shortcode = "sampleimg1",
            caption = caption,
            items = listOf(item),
            likes = 128,
            comments = comments,
            commentCount = 6,
            cursor = if (page <= 0) "2" else null,
            badge = "PHOTO",
            video = false,
        )
    }

    private fun igCarousel(entry: Entry, files: SampleMedia.Files): LinkContent {
        val caption = "Three looks at the same hour. The middle one is when the fog finally moved."
        val items = listOf(
            igItem("ig-car-0", files.ridge, caption),
            igItem("ig-car-1", files.kyoto, caption),
            igItem("ig-car-2", files.link, caption),
        )
        return instagram(
            entry = entry,
            shortcode = "samplecar1",
            caption = caption,
            items = items,
            likes = 86,
            comments = listOf(reply("ig-car-1", "noor.e", "Slide two. That is the walk.", "3h")),
            commentCount = 1,
            cursor = null,
            badge = "CAROUSEL",
            video = false,
        )
    }

    private fun igReel(entry: Entry, files: SampleMedia.Files): LinkContent {
        val caption = "The creek crossing, and then the rest of the walk goes quiet again."
        val variants = listOf(InstagramVideoVariant(type = 101, url = files.video, width = 320, height = 180))
        val item = igItem("ig-reel-0", files.ridge, caption, variants)
        return instagram(
            entry = entry,
            shortcode = "samplereel1",
            caption = caption,
            items = listOf(item),
            likes = 240,
            verified = true,
            comments = listOf(reply("ig-reel-1", "ravi.k", "I could hear the water from here.", "16m")),
            commentCount = 1,
            cursor = null,
            badge = "REEL",
            video = true,
        )
    }

    private fun instagram(
        entry: Entry,
        shortcode: String,
        caption: String,
        items: List<InstagramMediaItem>,
        likes: Int,
        comments: List<Comment>,
        commentCount: Int,
        cursor: String?,
        badge: String,
        video: Boolean,
        verified: Boolean = false,
    ): LinkContent {
        val first = items.first()
        return LinkContent(
            url = entry.canonicalUrl,
            title = caption,
            source = LinkSource.Instagram,
            kind = if (video) LinkKind.Video else LinkKind.Post,
            thumbnail = MediaLocation.Remote(first.imageUrl),
            media = Media(
                location = MediaLocation.Remote(first.imageUrl),
                contentDescription = first.contentDescription,
                badge = badge,
            ),
            author = Author(
                name = "maya.ren",
                metadata = buildString {
                    append("INSTAGRAM")
                    if (verified) append(" · VERIFIED")
                    append(" · $likes LIKES")
                },
            ),
            commentCount = commentCount,
            comments = comments,
            sourceMetadata = InstagramMetadata(
                postId = shortcode,
                shortcode = shortcode,
                code = shortcode,
                takenAtEpochSeconds = TAKEN_AT,
                likeCount = likes,
                commentCount = commentCount,
                authorId = "maya",
                authorUsername = "maya.ren",
                authorFullName = "Maya Ren",
                authorProfilePictureUrl = null,
                authorIsVerified = verified,
                videoVariants = first.videoVariants,
                mediaItems = items,
                commentsEndCursor = cursor,
            ),
        )
    }

    private fun fbText(entry: Entry): LinkContent = facebook(
        entry = entry,
        id = "sampletext1",
        text = FACEBOOK_TEXT,
    )

    private fun fbImage(entry: Entry, files: SampleMedia.Files): LinkContent = facebook(
        entry = entry,
        id = "samplephoto1",
        text = "The porch light was still on when I got back. The ridge can keep the rest.",
        images = listOf(files.kyoto),
    )

    private fun fbReel(entry: Entry, files: SampleMedia.Files): LinkContent = facebook(
        entry = entry,
        id = "samplereel1",
        text = "A short look at the creek, then back to the walk.",
        images = listOf(files.ridge),
        video = files.video,
    )

    private fun facebook(
        entry: Entry,
        id: String,
        text: String,
        images: List<String> = emptyList(),
        video: String? = null,
    ): LinkContent {
        val items = externalItems(id, text, images, video)
        return LinkContent(
            url = entry.canonicalUrl,
            title = text,
            source = LinkSource.Facebook,
            kind = if (video != null) LinkKind.Video else LinkKind.Post,
            thumbnail = MediaLocation.Remote(images.firstOrNull().orEmpty()),
            media = Media(
                location = MediaLocation.Remote(images.firstOrNull().orEmpty()),
                contentDescription = text.take(200),
                badge = when {
                    video != null -> "VIDEO"
                    images.isNotEmpty() -> "PHOTO"
                    else -> "TEXT"
                },
            ),
            author = Author(name = "Maya Ren", metadata = "FACEBOOK"),
            commentCount = 0,
            comments = emptyList(),
            sourceMetadata = ExternalPostMetadata(postId = id, mediaItems = items),
        )
    }

    private fun redditText(entry: Entry, page: Int): LinkContent {
        val title = "First night alone on the ridge"
        val body = "I left the lower lot before sunrise with one bottle and a plan to turn around at the first switchback. The switchback was quiet, so I kept walking until the trees opened. Nothing dramatic happened. I sat until the light changed and walked down while the lot was still mostly empty."
        val first = listOf(
            reply(
                "rt1",
                "maya_ren",
                "I will answer questions about the water crossing. Shoes stayed dry.",
                "6h",
                creator = true,
                replies = listOf(
                    reply(
                        "rt1a",
                        "jonah",
                        "How high was the water by midmorning?",
                        "5h",
                        replies = listOf(reply("rt1b", "maya_ren", "Ankle deep, and the rocks were obvious.", "4h", creator = true)),
                    ),
                ),
            ),
            reply("rt2", "leila", "The lower lot is the right start. The upper one fills first.", "5h"),
            reply("rt3", "noah", "Fog or clear when you turned around?", "4h"),
            reply("rt4", "sam", "This is the report I wanted. Thank you for skipping the drama.", "3h"),
            reply("rt5", "ada", "Parking note saved.", "2h"),
            reply("rt6", "ravi", "Going up this weekend if the wind stays down.", "1h"),
        )
        val more = listOf(
            reply("rt7", "noor", "The second switchback is where I usually stop. Good to know it opens up.", "48m"),
            reply("rt8", "ken", "Brought a filter anyway. The creek looked clear in your note.", "31m"),
            reply("rt9", "priya", "Sat in the same spot last fall. It holds the quiet.", "12m"),
            reply("rt10", "otto", "Thanks for the lot timing. That is the part people skip.", "4m"),
        )
        return reddit(
            entry = entry,
            id = "samplehike",
            title = "$title\n\n$body",
            author = "maya_ren",
            score = 86,
            comments = if (page <= 0) first else first + more,
            commentCount = 12,
            moreIds = if (page <= 0) listOf("rt7", "rt8", "rt9", "rt10") else emptyList(),
            badge = "TEXT",
        )
    }

    private fun redditImage(entry: Entry, files: SampleMedia.Files): LinkContent = reddit(
        entry = entry,
        id = "sampleridge",
        title = "The ridge when the fog finally moved",
        author = "leila",
        score = 42,
        comments = listOf(reply("ri1", "jonah", "That strip of light is the whole photo.", "2h")),
        commentCount = 1,
        media = listOf(redditPhoto("sampleridge-0", files.ridge, "The ridge when the fog finally moved")),
        badge = "PHOTO",
    )

    private fun redditVideo(entry: Entry, files: SampleMedia.Files): LinkContent = reddit(
        entry = entry,
        id = "sampleclip",
        title = "Creek crossing on the way down",
        author = "noah",
        score = 19,
        comments = listOf(reply("rv1", "ada", "I could hear it. That is plenty.", "33m")),
        commentCount = 1,
        media = listOf(
            RedditMediaItem(
                id = "sampleclip-0",
                imageUrl = files.ridge,
                contentDescription = "Creek crossing on the way down",
                videoUrl = files.video,
                width = 320,
                height = 180,
                durationSeconds = 3,
                videos = listOf(PlayableVideo(url = files.video, width = 320, height = 180, bitrate = 60_000)),
            ),
        ),
        badge = "VIDEO",
        video = true,
        duration = "00:03",
    )

    private fun redditLink(entry: Entry, files: SampleMedia.Files): LinkContent = reddit(
        entry = entry,
        id = "samplelink",
        title = "The ridge notes are worth the bookmark\n\nhttps://longtrail.review/ridge-notes",
        author = "sam",
        score = 64,
        comments = listOf(reply("rl1", "ravi", "The lot paragraph is the useful one.", "8h")),
        commentCount = 1,
        media = listOf(redditPhoto("samplelink-0", files.link, "Preview of the ridge notes")),
        badge = "PHOTO",
    )

    private fun reddit(
        entry: Entry,
        id: String,
        title: String,
        author: String,
        score: Int,
        comments: List<Comment>,
        commentCount: Int,
        moreIds: List<String> = emptyList(),
        media: List<RedditMediaItem> = emptyList(),
        badge: String,
        video: Boolean = false,
        duration: String? = null,
    ): LinkContent {
        val subreddit = "hiking"
        val image = media.firstOrNull()?.imageUrl.orEmpty()
        return LinkContent(
            url = entry.canonicalUrl,
            title = title,
            source = LinkSource.Reddit,
            kind = if (video) LinkKind.Video else LinkKind.Post,
            thumbnail = MediaLocation.Remote(image),
            media = Media(
                location = MediaLocation.Remote(image),
                contentDescription = media.firstOrNull()?.contentDescription ?: title.substringBefore('\n'),
                badge = badge,
                duration = duration,
            ),
            author = Author(
                name = author,
                metadata = "REDDIT · r/$subreddit · $score POINTS",
            ),
            commentCount = commentCount,
            comments = comments,
            sourceMetadata = RedditMetadata(
                postId = id,
                subreddit = subreddit,
                permalink = entry.canonicalUrl.removePrefix("https://www.reddit.com"),
                score = score,
                commentCount = commentCount,
                createdUtcEpochSeconds = TAKEN_AT,
                author = author,
                over18 = false,
                spoiler = false,
                mediaItems = media,
                moreCommentIds = moreIds,
            ),
        )
    }

    private fun errorStub(entry: Entry): LinkContent = LinkContent(
        url = entry.canonicalUrl,
        title = "This post was removed",
        source = LinkSource.X,
        kind = LinkKind.Post,
        thumbnail = MediaLocation.Remote(""),
        media = Media(location = MediaLocation.Remote(""), contentDescription = "", badge = "TEXT"),
        author = Author(name = "Maya Ren", metadata = "X"),
        commentCount = 0,
        comments = emptyList(),
        sourceMetadata = ExternalPostMetadata(postId = "1842700199"),
    )

    private fun unsupportedStub(entry: Entry): LinkContent = LinkContent(
        url = entry.canonicalUrl,
        title = "Marketplace listing",
        source = LinkSource.Facebook,
        kind = LinkKind.Post,
        thumbnail = MediaLocation.Remote(""),
        media = Media(location = MediaLocation.Remote(""), contentDescription = "", badge = "TEXT"),
        author = Author(name = "Maya Ren", metadata = "FACEBOOK"),
        commentCount = 0,
        comments = emptyList(),
        sourceMetadata = ExternalPostMetadata(postId = "sampleitem"),
    )

    private fun externalItems(
        id: String,
        text: String,
        images: List<String>,
        video: String?,
    ): List<ExternalMediaItem> {
        val videos = video?.let { listOf(PlayableVideo(url = it, width = 320, height = 180, bitrate = 60_000)) }.orEmpty()
        if (images.isEmpty() && video != null) {
            return listOf(
                ExternalMediaItem(
                    id = id,
                    imageUrl = "",
                    contentDescription = text.take(200),
                    videoUrl = video,
                    videos = videos,
                ),
            )
        }
        return images.mapIndexed { index, image ->
            ExternalMediaItem(
                id = "$id-$index",
                imageUrl = image,
                contentDescription = text.take(200),
                videoUrl = if (index == 0) video else null,
                videos = if (index == 0) videos else emptyList(),
            )
        }
    }

    private fun igItem(
        id: String,
        image: String,
        caption: String,
        variants: List<InstagramVideoVariant> = emptyList(),
    ): InstagramMediaItem = InstagramMediaItem(
        id = id,
        imageUrl = image,
        contentDescription = caption.take(200),
        videoVariants = variants,
        width = if (variants.isEmpty()) null else 320,
        height = if (variants.isEmpty()) null else 180,
    )

    private fun redditPhoto(id: String, image: String, description: String): RedditMediaItem = RedditMediaItem(
        id = id,
        imageUrl = image,
        contentDescription = description,
    )

    private fun reply(
        id: String,
        author: String,
        body: String,
        age: String,
        creator: Boolean = false,
        replies: List<Comment> = emptyList(),
    ): Comment = Comment(
        id = id,
        author = author,
        initial = author.firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()?.toString() ?: "?",
        age = age,
        body = body,
        isCreator = creator,
        replies = replies,
    )

    private const val TAKEN_AT = 1_720_000_000L

    private val FACEBOOK_TEXT = """
        The porch light was still on when I got back, which meant the ridge had taken longer than the map suggested.

        I left the lower lot before sunrise with one bottle and the excuse that I would turn around at the first switchback. The switchback was quiet, and the one after it was quieter, so I kept the same pace until the trees opened onto the pale rock.

        Nothing dramatic happened. A hawk circled once. The wind moved the grass in one direction and then changed its mind. I sat until the bottle was half empty and walked down while the lot was still mostly empty.

        This is the whole post. No photo, on purpose.
    """.trimIndent()
}
