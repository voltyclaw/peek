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
 * [assign] does nothing. Withdraw expires that source's cookies by name, spikes
 * [android.webkit.WebStorage.deleteOrigin], clears the app HTTP cache, and deletes
 * that source's IndexedDB folders when the on-disk layout matches and no WebView
 * is alive. [withdrawClearsAllSiteData] stays false on that path, so the withdrawal
 * copy remains the partial strings. App-wide storage and cookie wipes are not used.
 */
object EmbedWebProfiles {
    const val YOUTUBE = "pane-youtube"
    const val TIKTOK = "pane-tiktok"
    const val INSTAGRAM = "pane-instagram"
    const val THREADS = "pane-threads"

    /**
     * Shared profile for the hidden Reddit, Facebook, and Instagram scrapers.
     * It is not a withdraw target, so it stays out of [assignedProfiles].
     */
    const val SCRAPER = "pane-scraper"

    /** Profiles this build assigns for embeds. Instagram, Threads, and the scraper stay out of withdraw. */
    private val assignedProfiles = setOf(YOUTUBE, TIKTOK)

    /** First Chromium milestone whose WebView boundary exposes [WebViewFeature.MULTI_PROFILE]. */
    const val MIN_WEBVIEW_MAJOR = 119

    fun supported(): Boolean =
        runCatching { WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE) }.getOrDefault(false)

    /**
     * Call before the WebView loads anything.
     * False when multi-profile is unavailable or [WebViewCompat.setProfile] fails.
     */
    fun assign(webView: WebView, profile: String): Boolean {
        if (!supported()) return false
        return runCatching { WebViewCompat.setProfile(webView, profile) }.isSuccess
    }

    /** Deletes [profile] when it exists, then creates an empty one. No-op without multi-profile. */
    fun recreate(profile: String): Boolean {
        if (!supported()) return false
        return runCatching {
            val store = ProfileStore.getInstance()
            if (store.getProfile(profile) != null) store.deleteProfile(profile)
            store.getOrCreateProfile(profile)
            true
        }.getOrDefault(false)
    }

    /**
     * Deletes [profile] after its WebViews are destroyed.
     * True when the profile path ran ([withdrawClearsAllSiteData]).
     */
    fun delete(profile: String): Boolean {
        if (!supported()) return false
        return runCatching {
            val store = ProfileStore.getInstance()
            if (store.getProfile(profile) == null) return@runCatching false
            store.deleteProfile(profile)
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

/**
 * True only after a device matrix proves pre-119 IndexedDB deletion.
 * Until then, pre-119 withdraw keeps the partial withdrawal copy.
 */
internal const val PRE119_WITHDRAW_COPY_IS_FULL = false

/** Which withdraw path ran. Pre-119 copy stays partial while [PRE119_WITHDRAW_COPY_IS_FULL] is false. */
internal enum class SiteClearPath { Profile, OriginsFull, OriginsPartial }

internal fun siteClearPath(multiProfileSupported: Boolean): SiteClearPath =
    if (multiProfileSupported) SiteClearPath.Profile else SiteClearPath.OriginsPartial

/**
 * [SiteClearPath.OriginsFull] means the origin-scoped IndexedDB delete and the HTTP cache
 * clear both ran on an expected layout with no WebView alive. It does not by itself switch
 * the withdrawal copy; that still requires [PRE119_WITHDRAW_COPY_IS_FULL].
 */
internal fun selectSiteClearPath(
    multiProfileSupported: Boolean,
    idbVerified: Boolean,
    cacheCleared: Boolean,
    layoutExpected: Boolean = true,
    webViewLive: Boolean = false,
): SiteClearPath = when {
    multiProfileSupported -> SiteClearPath.Profile
    idbVerified && cacheCleared && layoutExpected && !webViewLive -> SiteClearPath.OriginsFull
    else -> SiteClearPath.OriginsPartial
}

internal fun withdrawClearsAllSiteData(
    path: SiteClearPath,
    profileDeleted: Boolean,
    idbVerified: Boolean = false,
): Boolean = when (path) {
    SiteClearPath.Profile -> profileDeleted
    SiteClearPath.OriginsFull -> PRE119_WITHDRAW_COPY_IS_FULL && idbVerified
    SiteClearPath.OriginsPartial -> false
}

/** Sheet promise and snackbar for one withdraw. Pre-119 stays partial on both. */
internal fun withdrawalSnackbarIsFull(
    multiProfileSupported: Boolean,
    profileDeleted: Boolean,
    idbVerified: Boolean,
    cacheCleared: Boolean,
    layoutExpected: Boolean,
    webViewLive: Boolean,
): Boolean {
    val path = selectSiteClearPath(
        multiProfileSupported,
        idbVerified,
        cacheCleared,
        layoutExpected,
        webViewLive,
    )
    return withdrawClearsAllSiteData(path, profileDeleted, idbVerified)
}

/**
 * Withdraw sheet body, before clear runs.
 * True only when multi-profile is supported, the source's profile exists, and this build assigns it.
 */
internal fun withdrawSheetIsFull(
    multiProfileSupported: Boolean,
    profileExists: Boolean,
    profileAssigned: Boolean,
): Boolean = multiProfileSupported && profileExists && profileAssigned
