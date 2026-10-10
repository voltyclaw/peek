package app.pane.android.data.webview

import android.content.Context
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/** Builds hidden scraper WebViews on the shared `pane-scraper` profile. */
interface ScraperWebViewFactory {
    fun create(ctx: Context): WebView
    fun release(webView: WebView)
}

/**
 * One factory for every scraper loader so the live-WebView count covers Reddit, Facebook, and Instagram.
 */
object ScraperWebViews {
    val shared: ScraperWebViewFactory = DefaultScraperWebViewFactory()
}

class DefaultScraperWebViewFactory(
    private val janitor: ScraperProfileJanitor = ScraperProfileJanitor(),
) : ScraperWebViewFactory {
    override fun create(ctx: Context): WebView {
        janitor.acquire()
        return try {
            WebView(ctx).also { webView ->
                if (!EmbedWebProfiles.assign(webView, EmbedWebProfiles.SCRAPER)) {
                    logFallback()
                }
                CookieManager.getInstance().setAcceptThirdPartyCookies(webView, false)
                webView.settings.apply {
                    saveFormData = false
                    setGeolocationEnabled(false)
                    cacheMode = WebSettings.LOAD_NO_CACHE
                }
            }
        } catch (error: Throwable) {
            janitor.releaseAndMaybeWipe()
            throw error
        }
    }

    override fun release(webView: WebView) {
        runCatching {
            webView.stopLoading()
            webView.loadUrl("about:blank")
            webView.removeAllViews()
            webView.destroy()
        }
        janitor.releaseAndMaybeWipe()
    }

    private fun logFallback() {
        if (loggedFallback.compareAndSet(false, true)) {
            Log.i(TAG, "pane-scraper profile unavailable; scraper cleanup uses per-origin cookies and storage")
        }
    }

    private companion object {
        const val TAG = "PaneScraper"
        val loggedFallback = AtomicBoolean(false)
    }
}

/**
 * Deletes and recreates `pane-scraper` only when no scraper WebView is alive.
 * Pre-119 devices skip this; loaders expire cookies and origins themselves.
 */
class ScraperProfileJanitor(
    private val supported: () -> Boolean = { EmbedWebProfiles.supported() },
    private val recreate: () -> Unit = { EmbedWebProfiles.recreate(EmbedWebProfiles.SCRAPER) },
) {
    private val live = AtomicInteger(0)

    fun liveCount(): Int = live.get().coerceAtLeast(0)

    fun acquire() {
        live.incrementAndGet()
    }

    fun releaseAndMaybeWipe() {
        val left = live.decrementAndGet()
        if (left <= 0) {
            live.set(0)
            wipe()
        }
    }

    fun wipe() {
        if (live.get() != 0) return
        if (!supported()) return
        runCatching { recreate() }
    }
}

/** Per-origin cleanup for the default jar. Used on every scrape, and it is the only cleanup before WebView 119. */
internal object ScraperFallbackCleanup {
    fun expireCookies(urls: List<String>) {
        runCatching {
            val manager = CookieManager.getInstance()
            urls.forEach { url ->
                val header = manager.getCookie(url) ?: return@forEach
                header.split(';').forEach { part ->
                    val name = part.substringBefore('=').trim()
                    if (name.isNotEmpty()) manager.setCookie(url, "$name=; Max-Age=0; Path=/")
                }
            }
            manager.flush()
        }
    }

    fun deleteOrigins(origins: List<String>) {
        runCatching {
            val storage = WebStorage.getInstance()
            origins.forEach { origin -> storage.deleteOrigin(origin) }
        }
    }
}
