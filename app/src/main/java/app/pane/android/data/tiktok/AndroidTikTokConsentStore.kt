package app.pane.android.data.tiktok

import android.content.Context
import android.webkit.CookieManager
import app.pane.android.data.webview.EmbedWebProfiles
import app.pane.android.data.webview.LegacySiteData
import app.pane.android.domain.tiktok.TikTokConsent
import app.pane.android.domain.tiktok.TikTokConsentKeys
import app.pane.android.domain.tiktok.TikTokConsentStore
import app.pane.android.domain.tiktok.TikTokSiteData
import app.pane.android.domain.tiktok.TikTokWebOrigins

class AndroidTikTokConsentStore(context: Context) : TikTokConsentStore {
    private val preferences = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    override fun read(): TikTokConsent? {
        val version = preferences.getString(TikTokConsentKeys.VERSION, null) ?: return null
        val at = preferences.getLong(TikTokConsentKeys.ACCEPTED_AT, 0L)
        if (version.isBlank() || at <= 0L) return null
        return TikTokConsent(version, at)
    }

    override fun write(consent: TikTokConsent) {
        preferences.edit()
            .putString(TikTokConsentKeys.VERSION, consent.policyVersion)
            .putLong(TikTokConsentKeys.ACCEPTED_AT, consent.acceptedAtEpochMillis)
            .apply()
    }

    override fun clear() {
        preferences.edit()
            .remove(TikTokConsentKeys.VERSION)
            .remove(TikTokConsentKeys.ACCEPTED_AT)
            .apply()
    }

    private companion object {
        const val FILE = "pane_tiktok_consent"
    }
}

internal fun expiredTikTokCookies(cookieHeader: String?): List<String> {
    if (cookieHeader.isNullOrBlank()) return emptyList()
    return cookieHeader.split(';')
        .map { it.substringBefore('=').trim() }
        .filter { it.isNotEmpty() }
        .distinct()
        .map { name -> "$name=; Max-Age=0; Path=/" }
}

/** Expires TikTok cookies and clears only TikTok origins on the pre-119 path. */
class AndroidTikTokSiteData(context: Context) : TikTokSiteData {
    private val appContext = context.applicationContext
    override var withdrawClearsAllSiteData: Boolean = false
        private set

    override fun clear() {
        withdrawClearsAllSiteData = EmbedWebProfiles.delete(EmbedWebProfiles.TIKTOK)
        if (withdrawClearsAllSiteData) return
        runCatching {
            val manager = CookieManager.getInstance()
            TikTokWebOrigins.PAGES.forEach { origin ->
                expiredTikTokCookies(manager.getCookie(origin)).forEach { cookie ->
                    manager.setCookie(origin, cookie)
                }
            }
            manager.flush()
        }
        runCatching { LegacySiteData.clear(appContext, TikTokWebOrigins.PAGES) }
    }
}
