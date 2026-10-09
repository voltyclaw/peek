package app.pane.android.domain.youtube

import app.pane.android.domain.model.YouTubeCommentsState

/**
 * Device record of YouTube consent.
 * [policyVersion] is the copy the person accepted. [acceptedAtEpochMillis] is when they tapped Play on YouTube.
 */
data class YouTubeConsent(
    val policyVersion: String,
    val acceptedAtEpochMillis: Long,
)

object YouTubeConsentKeys {
    const val POLICY_VERSION = "policy_accepted_version"
    const val ACCEPTED_AT = "policy_accepted_at"
}

/**
 * TODO: counsel review — this consent copy is a draft, not legal advice.
 * Bump [VERSION] when the accepted text changes so the poster is shown again.
 */
object YouTubeConsentPolicy {
    const val VERSION = "2026-10-09"
}

interface YouTubeConsentStore {
    fun read(): YouTubeConsent?
    fun write(consent: YouTubeConsent)
    fun clear()
}

/** In-memory and preference stores share this so a process restart keeps the same two fields. */
class MapYouTubeConsentStore(
    private val values: MutableMap<String, Any?> = mutableMapOf(),
) : YouTubeConsentStore {
    override fun read(): YouTubeConsent? {
        val version = values[YouTubeConsentKeys.POLICY_VERSION] as? String ?: return null
        val at = values[YouTubeConsentKeys.ACCEPTED_AT] as? Long ?: return null
        if (version.isBlank() || at <= 0L) return null
        return YouTubeConsent(version, at)
    }

    override fun write(consent: YouTubeConsent) {
        values[YouTubeConsentKeys.POLICY_VERSION] = consent.policyVersion
        values[YouTubeConsentKeys.ACCEPTED_AT] = consent.acceptedAtEpochMillis
    }

    override fun clear() {
        values.remove(YouTubeConsentKeys.POLICY_VERSION)
        values.remove(YouTubeConsentKeys.ACCEPTED_AT)
    }
}

/** How the video arrived. Share-in and VIEW use the same gate. There is no region argument. */
enum class YouTubeEntry { View, Share }

sealed interface YouTubeSurface {
    val videoId: String

    data class Poster(override val videoId: String) : YouTubeSurface
    data class Player(override val videoId: String, val embedUrl: String) : YouTubeSurface
}

data class YouTubeVideo(
    val videoId: String,
    val title: String = "",
    val description: String = "",
    val channelId: String = "",
    val channelName: String = "",
    val handle: String = "",
    val channelUrl: String = "",
    val avatarUrl: String = "",
    val thumbnailUrl: String = "",
    val viewCount: String = "",
    val publishedLabel: String = "",
    val embeddable: Boolean = true,
    val ageRestricted: Boolean = false,
    val commentsUnavailable: Boolean = false,
)

data class YouTubeComment(
    val id: String,
    val author: String,
    val body: String,
    val avatarUrl: String,
    val publishedAt: String,
)

data class YouTubeCommentPage(
    val comments: List<YouTubeComment>,
    val nextPageToken: String?,
    val hardWall: Boolean,
    val pagesLoaded: Int,
    val state: YouTubeCommentsState,
)

/**
 * The YouTube Data API and the no-key oEmbed fallback.
 * Callers must go through [YouTubeSession] or a repository that has already seen consent.
 * Neither opens a connection until Play on YouTube has been tapped or stored consent allows it.
 */
interface YouTubeDataApi {
    fun fetch(videoId: String): YouTubeVideo

    fun commentPage(videoId: String, pageToken: String?, pagesLoaded: Int): YouTubeCommentPage =
        YouTubeCommentPage(emptyList(), null, hardWall = false, pagesLoaded = pagesLoaded, state = YouTubeCommentsState.Ready)

    fun invalidate(videoId: String) = Unit
}

/** Clears cookies and site storage the YouTube player left in this app's WebView. */
interface YouTubeSiteData {
    fun clear()
}

object YouTubePlayer {
    /** Identity URL only. The viewer loads [iframeHtml] in a WebView, never an ExoPlayer stream. */
    fun embedUrl(videoId: String): String = "https://www.youtube-nocookie.com/embed/$videoId"

    /**
     * Official IFrame Player API document.
     * controls, fs, and rel stay at YouTube's own player. Pane does not draw on top of it.
     */
    fun iframeHtml(videoId: String, startSeconds: Int): String {
        val start = startSeconds.coerceAtLeast(0)
        return """
            <!DOCTYPE html>
            <html>
            <head>
              <meta name="referrer" content="strict-origin-when-cross-origin">
              <meta name="viewport" content="width=device-width, initial-scale=1">
              <style>html,body,#player{margin:0;padding:0;height:100%;width:100%;background:#0E0B0A;overflow:hidden}</style>
            </head>
            <body>
              <div id="player"></div>
              <script src="https://www.youtube.com/iframe_api"></script>
              <script>
                var player;
                function onYouTubeIframeAPIReady() {
                  player = new YT.Player('player', {
                    videoId: '$videoId',
                    playerVars: {
                      controls: 1,
                      fs: 1,
                      playsinline: 1,
                      rel: 0,
                      start: $start,
                      origin: 'https://app.pane.android'
                    },
                    events: {
                      onStateChange: function(event) {
                        if (event.data === 0 && window.Pane) Pane.onEnded();
                      }
                    }
                  });
                }
              </script>
            </body>
            </html>
        """.trimIndent()
    }
}

/**
 * One gate for the Data API and the player.
 * Neither runs until consent for [YouTubeConsentPolicy.VERSION] is stored.
 */
class YouTubeSession(
    private val store: YouTubeConsentStore,
    private val api: YouTubeDataApi,
    private val siteData: YouTubeSiteData,
) {
    fun hasConsent(): Boolean = matchesPolicy(store.read())

    fun open(videoId: String, entry: YouTubeEntry): YouTubeSurface {
        if (!hasConsent()) return YouTubeSurface.Poster(videoId)
        api.fetch(videoId)
        return YouTubeSurface.Player(videoId, YouTubePlayer.embedUrl(videoId))
    }

    fun accept(videoId: String, nowEpochMillis: Long): YouTubeSurface.Player {
        store.write(YouTubeConsent(YouTubeConsentPolicy.VERSION, nowEpochMillis))
        api.fetch(videoId)
        return YouTubeSurface.Player(videoId, YouTubePlayer.embedUrl(videoId))
    }

    fun withdraw() {
        store.clear()
        siteData.clear()
    }

    private fun matchesPolicy(consent: YouTubeConsent?): Boolean =
        consent != null && consent.policyVersion == YouTubeConsentPolicy.VERSION && consent.acceptedAtEpochMillis > 0L
}
