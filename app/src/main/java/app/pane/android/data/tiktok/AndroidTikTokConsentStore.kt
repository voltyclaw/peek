package app.pane.android.data.tiktok

import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebStorage
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

/** Expires TikTok cookies and deletes only TikTok web-storage origins. */
class AndroidTikTokSiteData : TikTokSiteData {
    override fun clear() {
        runCatching {
            val manager = CookieManager.getInstance()
            TikTokWebOrigins.PAGES.forEach { origin ->
                expiredTikTokCookies(manager.getCookie(origin)).forEach { cookie ->
                    manager.setCookie(origin, cookie)
                }
            }
            manager.flush()
        }
        runCatching {
            val storage = WebStorage.getInstance()
            TikTokWebOrigins.PAGES.forEach { origin -> storage.deleteOrigin(origin) }
        }
    }
}
