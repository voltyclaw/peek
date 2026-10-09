package app.pane.android.data.x

/**
 * Photos, videos, and GIFs attached to one status slice on the logged-out page.
 * Profile photos and card images are not post media.
 */
object XThreadMedia {
    fun parse(slice: String): List<ParsedXMedia> {
        if (slice.isBlank()) return emptyList()
        val items = mutableListOf<ParsedXMedia>()
        MEDIA_URL.findAll(slice).forEach { match ->
            val url = unescape(match.groupValues[1])
            if (!isPostMedia(url)) return@forEach
            val windowStart = maxOf(0, match.range.first - 400)
            val windowEnd = minOf(slice.length, match.range.last + 1_200)
            val window = slice.substring(windowStart, windowEnd)
            val (width, height) = size(window)
            val gif = isGif(window, url)
            val videos = if (gif || isVideo(window, url)) mp4s(window) else emptyList()
            val video = videos.maxByOrNull { it.bitrate ?: 0 }?.url
            items += ParsedXMedia(
                imageUrl = url,
                videoUrl = video,
                width = width,
                height = height,
                gif = gif,
                videos = videos,
            )
        }
        if (items.isEmpty()) {
            mp4s(slice).forEach { video ->
                if (video.url.contains("tweet_video") || video.url.contains("ext_tw_video") || video.url.contains("amplify_video")) {
                    items += ParsedXMedia(
                        imageUrl = "",
                        videoUrl = video.url,
                        width = null,
                        height = null,
                        gif = video.url.contains("tweet_video"),
                        videos = listOf(video),
                    )
                }
            }
        }
        return items.distinctBy { it.imageUrl.ifBlank { it.videoUrl.orEmpty() } }
    }

    private fun isPostMedia(url: String): Boolean {
        if (!url.startsWith("https://pbs.twimg.com/")) return false
        if ("profile_images" in url || "profile_banners" in url || "card_img" in url) return false
        return "/media/" in url || "tweet_video" in url || "ext_tw_video" in url || "amplify_video" in url
    }

    private fun isGif(window: String, url: String): Boolean =
        "animated_gif" in window || "tweet_video" in url || "tweet_video" in window

    private fun isVideo(window: String, url: String): Boolean =
        "\"video\"" in window || "ext_tw_video" in url || "amplify_video" in url || "video_info" in window

    private fun mp4s(window: String): List<ParsedXVideo> =
        MP4.findAll(window).map { match ->
            ParsedXVideo(url = unescape(match.groupValues[1]), bitrate = match.groupValues[2].toIntOrNull())
        }.distinctBy { it.url }.toList()

    private fun size(window: String): Pair<Int?, Int?> {
        val match = SIZE.find(window) ?: return null to null
        return match.groupValues[1].toIntOrNull() to match.groupValues[2].toIntOrNull()
    }

    private fun unescape(value: String): String = value.replace("\\/", "/").replace("\\u0026", "&")

    private val MEDIA_URL = Regex("""media_url_https\\?":\\?"(https://pbs\.twimg\.com/[^"\\]+)""")
    private val MP4 = Regex("""(https:\\?/\\?/video\.twimg\.com\\?/[^"\\]+?\.mp4[^"\\]*)(?:"|\\").{0,80}?bitrate\\?":\s*(\d+)?""")
    private val SIZE = Regex("""original_info\\?".{0,240}?width\\?":\s*(\d+).{0,80}?height\\?":\s*(\d+)""")
}
