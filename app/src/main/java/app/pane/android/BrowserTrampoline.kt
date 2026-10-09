package app.pane.android

import app.pane.android.data.facebook.FacebookUrls
import app.pane.android.data.links.profileLink
import app.pane.android.data.reddit.RedditUrls
import app.pane.android.data.x.XUrls
import app.pane.android.data.tiktok.TikTokUrls
import app.pane.android.data.youtube.YouTubeUrls
import app.pane.android.domain.model.LinkShims
import java.net.URI
import java.util.Locale

/**
 * Decides what a catch-all browser VIEW should do.
 * Posts Pane already opens (X, Reddit, Instagram, Facebook) stay in Pane, and so do
 * other Meta hosts. Everything else is handed to another browser.
 * The original URL is what the other browser receives, so a redirect shim
 * still follows the tap the person made.
 */
internal object BrowserTrampoline {
    sealed class Decision {
        data object Ignore : Decision()
        data class OpenInPane(val url: String) : Decision()
        data class OpenInSource(val url: String) : Decision()
        data class HandOff(val url: String) : Decision()
    }

    data class Candidate(
        val packageName: String,
        val activityName: String,
    )

    enum class BrowserDefaultsTarget {
        RoleRequest,
        DefaultApps,
    }

    /**
     * API 29 and later can show the browser-role prompt when Pane does not hold it.
     * Otherwise open the system Default apps screen, which includes Browser app.
     */
    fun browserDefaultsTarget(sdkInt: Int, roleAvailable: Boolean, roleHeld: Boolean): BrowserDefaultsTarget {
        if (sdkInt >= 29 && roleAvailable && !roleHeld) return BrowserDefaultsTarget.RoleRequest
        return BrowserDefaultsTarget.DefaultApps
    }

    fun decide(rawUrl: String?, enabled: Boolean): Decision {
        val raw = rawUrl?.trim().orEmpty()
        if (!raw.startsWith("http://") && !raw.startsWith("https://")) return Decision.Ignore
        if (!enabled) return Decision.HandOff(raw)
        val unwrapped = LinkShims.unwrap(raw)
        openablePost(unwrapped)?.let { return Decision.OpenInPane(it) }
        if (profileLink(unwrapped) != null) return Decision.OpenInSource(unwrapped)
        if (FacebookUrls.isMarketplace(unwrapped)) return Decision.HandOff(raw)
        val host = hostOf(unwrapped) ?: return Decision.HandOff(raw)
        return if (isMetaHost(host)) Decision.OpenInPane(unwrapped) else Decision.HandOff(raw)
    }

    /**
     * A post the existing viewers already accept. Http is promoted to https when that
     * is the form Reddit or Instagram recognize. Profiles and home pages are not posts.
     */
    fun openablePost(url: String): String? {
        if (XUrls.supports(url) || FacebookUrls.supports(url) || YouTubeUrls.supports(url) || TikTokUrls.supports(url)) return url
        val https = toHttps(url)
        if (RedditUrls.supports(https) || isInstagramPost(https) || YouTubeUrls.supports(https) || TikTokUrls.supports(https)) return https
        return null
    }

    fun isMetaHost(host: String): Boolean {
        val normalized = host.lowercase(Locale.US).removePrefix("www.")
        if (normalized in EXACT_HOSTS) return true
        return META_SUFFIXES.any { suffix -> normalized.endsWith(suffix) }
    }

    /**
     * Picks another browser that is actually in [candidates].
     * A saved package wins when it is still there. Otherwise Chrome, then the
     * current system browser, then the remaining known browsers, then whatever
     * else was resolved. Pane and the system chooser are never returned.
     */
    fun pickHandoff(
        candidates: List<Candidate>,
        ownPackage: String,
        preferredPackage: String? = null,
        systemPackage: String? = null,
    ): Candidate? {
        val others = candidates.filter { acceptsPackage(it.packageName, ownPackage) }
        if (others.isEmpty()) return null
        match(others, preferredPackage)?.let { return it }
        match(others, CHROME)?.let { return it }
        match(others, systemPackage)?.let { return it }
        for (packageName in PREFERRED_BROWSERS) {
            match(others, packageName)?.let { return it }
        }
        return others.first()
    }

    fun acceptsPackage(packageName: String, ownPackage: String): Boolean {
        if (packageName.isBlank() || packageName == ownPackage) return false
        if (packageName in RESOLVER_PACKAGES) return false
        if (packageName.startsWith("com.android.internal.")) return false
        return true
    }

    fun <T> orderBrowsers(browsers: List<T>, packageName: (T) -> String, label: (T) -> String): List<T> {
        val rank = PREFERRED_BROWSERS.withIndex().associate { it.value to it.index }
        return browsers.sortedWith(
            compareBy({ rank[packageName(it)] ?: Int.MAX_VALUE }, { label(it).lowercase(Locale.US) }),
        )
    }

    private fun match(candidates: List<Candidate>, packageName: String?): Candidate? {
        if (packageName.isNullOrBlank()) return null
        return candidates.firstOrNull { it.packageName == packageName }
    }

    private fun hostOf(url: String): String? =
        runCatching { URI(url).host }.getOrNull()?.takeIf { it.isNotBlank() }

    private fun toHttps(url: String): String =
        if (url.startsWith("http://")) "https://" + url.substring("http://".length) else url

    private fun isInstagramPost(url: String): Boolean {
        val uri = runCatching { URI(url.trim()) }.getOrNull() ?: return false
        if (!uri.scheme.equals("https", ignoreCase = true)) return false
        val host = uri.host?.lowercase(Locale.US) ?: return false
        if (host !in INSTAGRAM_HOSTS) return false
        return INSTAGRAM_POST.matches(uri.path.orEmpty().trimEnd('/'))
    }

    private val INSTAGRAM_HOSTS = setOf(
        "instagram.com",
        "www.instagram.com",
        "m.instagram.com",
        "l.instagram.com",
    )

    private val INSTAGRAM_POST = Regex("/(p|reel|reels)/([A-Za-z0-9_-]+)")

    private val EXACT_HOSTS = setOf(
        "facebook.com",
        "instagram.com",
        "fb.com",
        "fb.me",
        "fb.watch",
    )

    private val META_SUFFIXES = listOf(
        ".facebook.com",
        ".instagram.com",
        ".fb.com",
        ".fb.me",
    )

    private const val CHROME = "com.android.chrome"

    private val PREFERRED_BROWSERS = listOf(
        CHROME,
        "com.chrome.beta",
        "com.chrome.dev",
        "com.google.android.apps.chrome",
        "com.sec.android.app.sbrowser",
        "com.android.browser",
        "com.brave.browser",
        "org.mozilla.firefox",
        "com.microsoft.emmx",
    )

    private val RESOLVER_PACKAGES = setOf(
        "android",
        "com.android.internal.app",
        "com.android.intentresolver",
    )
}
