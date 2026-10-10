package app.pane.android.domain.tiktok

/**
 * Device record of TikTok consent. Separate from YouTube's policy keys.
 * TODO: counsel review — the agreement line is a draft. Guard owns the final wording and the Hebrew.
 */
data class TikTokConsent(
    val policyVersion: String,
    val acceptedAtEpochMillis: Long,
)

object TikTokConsentKeys {
    const val VERSION = "tt_consent_version"
    const val ACCEPTED_AT = "tt_consent_at"
}

object TikTokConsentPolicy {
    const val VERSION = "2026-10-09"
}

interface TikTokConsentStore {
    fun read(): TikTokConsent?
    fun write(consent: TikTokConsent)
    fun clear()
}

class MapTikTokConsentStore(
    private val values: MutableMap<String, Any?> = mutableMapOf(),
) : TikTokConsentStore {
    override fun read(): TikTokConsent? {
        val version = values[TikTokConsentKeys.VERSION] as? String ?: return null
        val at = values[TikTokConsentKeys.ACCEPTED_AT] as? Long ?: return null
        if (version.isBlank() || at <= 0L) return null
        return TikTokConsent(version, at)
    }

    override fun write(consent: TikTokConsent) {
        values[TikTokConsentKeys.VERSION] = consent.policyVersion
        values[TikTokConsentKeys.ACCEPTED_AT] = consent.acceptedAtEpochMillis
    }

    override fun clear() {
        values.remove(TikTokConsentKeys.VERSION)
        values.remove(TikTokConsentKeys.ACCEPTED_AT)
    }
}

enum class TikTokEntry { View, Share }

sealed interface TikTokSurface {
    val videoId: String

    data class Poster(override val videoId: String) : TikTokSurface
    data class Player(override val videoId: String, val playerUrl: String) : TikTokSurface
}

data class TikTokOEmbed(
    val caption: String = "",
    val authorName: String = "",
    val authorUrl: String = "",
    val thumbnailUrl: String = "",
)

sealed interface TikTokOEmbedResult {
    data class Ready(val embed: TikTokOEmbed) : TikTokOEmbedResult
    data object Removed : TikTokOEmbedResult
    data object Failed : TikTokOEmbedResult
}

/**
 * oEmbed and short-link resolution. Callers go through [TikTokSession] or a repository
 * that has already seen consent. Neither contacts TikTok before Play on TikTok.
 */
interface TikTokOEmbedApi {
    fun fetch(videoId: String, pageUrl: String): TikTokOEmbedResult
    fun invalidate(videoId: String) = Unit
}

fun interface TikTokRedirects {
    fun resolve(url: String): String?
}

/** Clears TikTok cookies and web storage only. YouTube data stays. */
interface TikTokSiteData {
    fun clear()

    /** True after [clear] deleted a named WebView profile. Pre-119 stays false. See [YouTubeSiteData.withdrawClearsAllSiteData]. */
    val withdrawClearsAllSiteData: Boolean get() = false
}

object TikTokWebOrigins {
    const val CLEARS_ALL_WEBVIEW_STORAGE = false

    val PAGES = listOf(
        "https://www.tiktok.com",
        "https://tiktok.com",
        "https://m.tiktok.com",
        "https://vm.tiktok.com",
        "https://vt.tiktok.com",
    )
}

class TikTokSession(
    private val store: TikTokConsentStore,
    private val api: TikTokOEmbedApi,
    private val siteData: TikTokSiteData,
) {
    fun hasConsent(): Boolean = matches(store.read())

    fun acceptedAtEpochMillis(): Long? = store.read()?.takeIf(::matches)?.acceptedAtEpochMillis

    /** Poster, and no oEmbed, when this version is not stored. */
    fun open(videoId: String, entry: TikTokEntry): TikTokSurface {
        if (!hasConsent()) return TikTokSurface.Poster(videoId)
        if (videoId.isNotBlank()) api.fetch(videoId, pageFor(videoId))
        return if (videoId.isBlank()) TikTokSurface.Poster(videoId) else TikTokSurface.Player(videoId, TikTokPlayer.url(videoId))
    }

    fun accept(videoId: String, nowEpochMillis: Long): TikTokSurface {
        store.write(TikTokConsent(TikTokConsentPolicy.VERSION, nowEpochMillis))
        if (videoId.isBlank()) return TikTokSurface.Poster(videoId)
        api.fetch(videoId, pageFor(videoId))
        return TikTokSurface.Player(videoId, TikTokPlayer.url(videoId))
    }

    /** Settings confirm. Records consent and does not contact TikTok. */
    fun allow(nowEpochMillis: Long) {
        store.write(TikTokConsent(TikTokConsentPolicy.VERSION, nowEpochMillis))
    }

    fun withdraw() {
        store.clear()
        siteData.clear()
    }

    /** Actual result of the last [withdraw]. Snackbar copy follows this, not the sheet's prediction. */
    fun clearedAllSiteData(): Boolean = siteData.withdrawClearsAllSiteData

    private fun matches(consent: TikTokConsent?): Boolean =
        consent != null && consent.policyVersion == TikTokConsentPolicy.VERSION && consent.acceptedAtEpochMillis > 0L

    private fun pageFor(videoId: String): String = "https://www.tiktok.com/embed/v2/$videoId"
}
