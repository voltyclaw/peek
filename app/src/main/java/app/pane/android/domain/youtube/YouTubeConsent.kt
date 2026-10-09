package app.pane.android.domain.youtube

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

data class YouTubeVideo(val videoId: String)

/**
 * The YouTube Data API. Callers must go through [YouTubeSession], which does not call this
 * until Play on YouTube has been tapped or a stored consent already allows playback.
 */
interface YouTubeDataApi {
    fun fetch(videoId: String): YouTubeVideo
}

/** Clears cookies and site storage the YouTube player left in this app's WebView. */
interface YouTubeSiteData {
    fun clear()
}

object YouTubePlayer {
    fun embedUrl(videoId: String): String = "https://www.youtube-nocookie.com/embed/$videoId"
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
