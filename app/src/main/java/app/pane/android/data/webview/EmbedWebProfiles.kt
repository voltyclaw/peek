package app.pane.android.data.webview

import android.webkit.WebView
import androidx.webkit.ProfileStore
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
/**
 * One WebView profile per embed source.
 *
 * [WebViewFeature.MULTI_PROFILE] is implemented in Android System WebView 119 and newer,
 * and only when that WebView is running multi-process. Deleting the profile removes
 * that source's cookies, DOM storage including IndexedDB, and its HTTP cache.
 *
 * On older WebView, or when multi-process is off, the feature check is false.
 * [assign] does nothing and withdraw keeps the per-origin cookie and
 * [android.webkit.WebStorage.deleteOrigin] path. That fallback does not clear
 * IndexedDB or the HTTP cache, so [withdrawClearsAllSiteData] stays false.
 */
object EmbedWebProfiles {
    const val YOUTUBE = "pane-youtube"
    const val TIKTOK = "pane-tiktok"
    const val INSTAGRAM = "pane-instagram"
    const val THREADS = "pane-threads"

    /** Profiles this build actually assigns to a WebView. Instagram and Threads stay reserved. */
    private val assignedProfiles = setOf(YOUTUBE, TIKTOK)

    /** First Chromium milestone whose WebView boundary exposes [WebViewFeature.MULTI_PROFILE]. */
    const val MIN_WEBVIEW_MAJOR = 119

    fun supported(): Boolean =
        runCatching { WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE) }.getOrDefault(false)

    /** Call before the WebView loads anything. No-op when multi-profile is unavailable. */
    fun assign(webView: WebView, profile: String) {
        if (!supported()) return
        runCatching { WebViewCompat.setProfile(webView, profile) }
    }

    /**
     * Deletes [profile] after its WebViews are destroyed.
     * True when the profile path ran ([withdrawClearsAllSiteData]).
     */
    fun delete(profile: String): Boolean {
        if (!supported()) return false
        return runCatching {
            val store = ProfileStore.getInstance()
            val existing = store.getProfile(profile) ?: return@runCatching false
            val deleted = store.deleteProfile(profile)
            if (!deleted) {
                runCatching {
                    existing.cookieManager.removeAllCookies(null)
                    existing.cookieManager.flush()
                    existing.webStorage.deleteAllData()
                }
            }
            deleted
        }.getOrDefault(false)
    }

    /**
     * Sheet copy, chosen before [delete]. Full only when multi-profile is on,
     * this source's profile is one we assign, and that profile already exists.
     */
    fun expectsFullProfileClear(profile: String): Boolean {
        val assigned = profile in assignedProfiles
        val multiProfile = supported()
        val exists = assigned && multiProfile && runCatching {
            ProfileStore.getInstance().getProfile(profile) != null
        }.getOrDefault(false)
        return withdrawSheetIsFull(multiProfile, exists, assigned)
    }

    /** Sheet prediction without touching WebView. Instagram and Threads are not assigned. */
    internal fun predictsFullSheet(
        profile: String,
        multiProfileSupported: Boolean,
        profileExists: Boolean,
    ): Boolean = withdrawSheetIsFull(multiProfileSupported, profileExists, profile in assignedProfiles)
}

/** Which withdraw path ran. Profile deletion is the only path that clears all site data. */
internal enum class SiteClearPath { Profile, Origins }

internal fun siteClearPath(multiProfileSupported: Boolean): SiteClearPath =
    if (multiProfileSupported) SiteClearPath.Profile else SiteClearPath.Origins

internal fun withdrawClearsAllSiteData(path: SiteClearPath, profileDeleted: Boolean): Boolean =
    path == SiteClearPath.Profile && profileDeleted

/**
 * Withdraw sheet body, before clear runs.
 * True only when multi-profile is supported, the source's profile exists, and this build assigns it.
 */
internal fun withdrawSheetIsFull(
    multiProfileSupported: Boolean,
    profileExists: Boolean,
    profileAssigned: Boolean,
): Boolean = multiProfileSupported && profileExists && profileAssigned
