package app.pane.android.data.youtube

import app.pane.android.domain.youtube.YouTubeLinkKind
import app.pane.android.domain.youtube.YouTubeLinks
import app.pane.android.domain.youtube.YouTubePlayer

/** A YouTube watch, short, live, embed, or youtu.be link. Home pages and channels are not videos. */
object YouTubeUrls {
    fun parse(url: String) = YouTubeLinks.parse(url)

    fun videoId(url: String): String? = parse(url)?.videoId

    fun canonical(url: String): String? = parse(url)?.canonicalUrl

    fun supports(url: String): Boolean = videoId(url) != null

    fun isChannel(url: String): Boolean = parse(url)?.kind == YouTubeLinkKind.Channel

    fun embedUrl(url: String): String? = videoId(url)?.let(YouTubePlayer::embedUrl)

    fun isYouTubeHost(url: String): Boolean = parse(url) != null
}
