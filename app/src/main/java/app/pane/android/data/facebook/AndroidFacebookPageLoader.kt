package app.pane.android.data.facebook

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import app.pane.android.data.net.loginWallFailure
import app.pane.android.data.resolver.PageLoadProgressElement
import app.pane.android.data.webview.ScraperFallbackCleanup
import app.pane.android.data.webview.ScraperWebViewFactory
import app.pane.android.data.webview.ScraperWebViews
import app.pane.android.domain.model.LoadProgress
import app.pane.android.domain.model.SourceFailure
import app.pane.android.domain.model.LoadStage
import app.pane.android.domain.repository.LoadProgressListener
import java.net.URI
import kotlin.coroutines.coroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Logged-out WebView fallback. It reads the public page HTML and does not present a Facebook login.
 */
class AndroidFacebookPageLoader(
    context: Context,
    private val timeoutMillis: Long = 10_000L,
    private val webViews: ScraperWebViewFactory = ScraperWebViews.shared,
) : FacebookPageLoader {
    private val applicationContext = context.applicationContext
    private val json = Json { isLenient = true }

    override val resolverId: String = "facebook-webview"

    override fun supports(url: String): Boolean {
        val post = FacebookUrls.parse(url) ?: return false
        return post.kind != FacebookUrls.Kind.Story
    }

    @SuppressLint("SetJavaScriptEnabled")
    override suspend fun resolve(url: String): ParsedFacebookPost {
        val post = FacebookUrls.parse(url)
            ?: throw SourceFailure.Unsupported(url)
        val listener = coroutineContext[PageLoadProgressElement]?.listener ?: LoadProgressListener {}
        try {
            return withContext(Dispatchers.Main.immediate) {
                withTimeout(timeoutMillis) {
                    suspendCancellableCoroutine { continuation ->
                        val handler = Handler(Looper.getMainLooper())
                        val webView = webViews.create(applicationContext)
                        var completed = false
                        var attempts = 0
                        var best: ParsedFacebookPost? = null

                        fun cleanup() {
                            handler.removeCallbacksAndMessages(null)
                            webView.webViewClient = WebViewClient()
                            webViews.release(webView)
                            ScraperFallbackCleanup.expireCookies(COOKIE_URLS)
                            ScraperFallbackCleanup.deleteOrigins(ORIGINS)
                        }

                        fun fail(error: Throwable) {
                            if (completed) return
                            completed = true
                            cleanup()
                            if (continuation.isActive) continuation.resumeWithException(error)
                        }

                        fun succeed(parsed: ParsedFacebookPost) {
                            if (completed) return
                            completed = true
                            cleanup()
                            if (continuation.isActive) continuation.resume(parsed)
                        }

                        lateinit var read: Runnable
                        read = Runnable {
                            if (completed) return@Runnable
                            attempts += 1
                            listener.onProgress(LoadProgress((0.55f + attempts * 0.1f).coerceAtMost(0.95f), LoadStage.ExtractingContent))
                            webView.evaluateJavascript(
                                "(function(){return document.documentElement?document.documentElement.outerHTML:''})()",
                            ) { encoded ->
                                if (completed) return@evaluateJavascript
                                val html = decode(encoded)
                                if (html != null && isLogin(webView.url)) {
                                    fail(loginWallFailure(FacebookDocument.LOGIN))
                                    return@evaluateJavascript
                                }
                                val parsed = html?.let { FacebookDocument.parse(it, post.id, post.canonicalUrl) }
                                val previous = best
                                val improved = when {
                                    parsed == null -> previous
                                    previous == null || parsed.text.length > previous.text.length -> parsed
                                    else -> previous
                                }
                                best = improved
                                val ready = improved?.takeIf { !FacebookDocument.isTruncatedPreview(it.text) }
                                when {
                                    ready != null -> {
                                        listener.onProgress(LoadProgress(1f, LoadStage.ExtractingContent))
                                        succeed(ready)
                                    }
                                    attempts >= 4 && improved != null -> {
                                        listener.onProgress(LoadProgress(1f, LoadStage.ExtractingContent))
                                        succeed(improved)
                                    }
                                    attempts >= 4 -> fail(SourceFailure.Network(message = FacebookDocument.UNAVAILABLE))
                                    else -> handler.postDelayed(read, 700L)
                                }
                            }
                        }

                        webView.settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            userAgentString = USER_AGENT
                            allowFileAccess = false
                            allowContentAccess = false
                            cacheMode = WebSettings.LOAD_NO_CACHE
                        }
                        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, false)
                        webView.webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView, finishedUrl: String) {
                                if (completed) return
                                if (isLogin(finishedUrl)) {
                                    fail(loginWallFailure(FacebookDocument.LOGIN))
                                    return
                                }
                                if (!isAllowed(finishedUrl)) {
                                    fail(SourceFailure.Network(message = "Facebook redirected away from the post"))
                                    return
                                }
                                handler.post(read)
                            }

                            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                                if (!request.isForMainFrame) return false
                                val target = request.url.toString()
                                if (isLogin(target)) {
                                    fail(loginWallFailure(FacebookDocument.LOGIN))
                                    return true
                                }
                                val allowed = isAllowed(target)
                                if (!allowed) {
                                    fail(SourceFailure.Network(message = "Blocked browser navigation away from Facebook"))
                                }
                                return !allowed
                            }
                        }
                        continuation.invokeOnCancellation {
                            handler.post {
                                if (!completed) {
                                    completed = true
                                    cleanup()
                                }
                            }
                        }
                        listener.onProgress(LoadProgress(0.12f, LoadStage.Connecting))
                        webView.loadUrl(post.canonicalUrl)
                    }
                }
            }
        } catch (timeout: TimeoutCancellationException) {
            throw SourceFailure.Network(message = FacebookDocument.UNAVAILABLE)
        }
    }

    private fun decode(value: String): String? =
        runCatching { json.parseToJsonElement(value).jsonPrimitive.contentOrNull }.getOrNull()

    private fun isAllowed(url: String): Boolean {
        val host = runCatching { URI(url).host }.getOrNull()?.lowercase().orEmpty().removePrefix("www.")
        return host in ALLOWED || host.endsWith(".facebook.com")
    }

    private fun isLogin(url: String?): Boolean {
        val path = url?.let { runCatching { URI(it).path }.getOrNull() }.orEmpty()
        return path.startsWith("/login") || path.startsWith("/checkpoint")
    }

    private companion object {
        val COOKIE_URLS = listOf("https://www.facebook.com", "https://m.facebook.com", "https://fb.watch")
        val ORIGINS = listOf("https://www.facebook.com", "https://m.facebook.com", "https://fb.watch")
        val ALLOWED = setOf("facebook.com", "m.facebook.com", "mbasic.facebook.com", "fb.com", "fb.watch")
        const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
    }
}
