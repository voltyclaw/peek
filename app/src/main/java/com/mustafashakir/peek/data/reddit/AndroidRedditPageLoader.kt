package com.mustafashakir.peek.data.reddit

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import com.mustafashakir.peek.data.resolver.PageLoadProgressElement
import com.mustafashakir.peek.domain.model.LoadProgress
import com.mustafashakir.peek.domain.model.LoadStage
import com.mustafashakir.peek.domain.repository.LoadProgressListener
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
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
 * Logged-out fallback for when Reddit's comments JSON is blocked.
 * A hidden WebView loads the public post page, then [RedditPageDocument] reads
 * whatever the page rendered. Cookies set for that load are cleared afterwards.
 * There is no Reddit login UI.
 */
class AndroidRedditPageLoader(
    context: Context,
    private val timeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS,
    private val pollIntervalMillis: Long = DEFAULT_POLL_INTERVAL_MILLIS,
) : RedditPageLoader {
    private val applicationContext = context.applicationContext
    private val json = Json { isLenient = true }

    override val resolverId: String = "reddit-webview"

    override fun supports(url: String): Boolean = RedditUrls.supports(url)

    override suspend fun resolve(url: String): ParsedRedditPost {
        if (!supports(url)) throw IllegalArgumentException("Unsupported Reddit post URL: $url")
        val pageUrl = RedditUrls.fetchPageUrl(url) ?: url
        return loadPage { webView -> webView.loadUrl(pageUrl) }
    }

    internal suspend fun loadHtmlForTesting(
        html: String,
        baseUrl: String = "https://www.reddit.com/r/pics/comments/abc123/title/",
    ): ParsedRedditPost {
        return loadPage { webView ->
            webView.loadDataWithBaseURL(baseUrl, html, "text/html", "UTF-8", null)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private suspend fun loadPage(
        startLoad: (WebView) -> Unit,
    ): ParsedRedditPost {
        val listener = coroutineContext[PageLoadProgressElement]?.listener ?: LoadProgressListener {}
        val captured = AtomicReference<ParsedRedditPost?>(null)
        val httpStatus = AtomicInteger(0)
        try {
            return withContext(Dispatchers.Main.immediate) {
                withTimeout(timeoutMillis) {
                    suspendCancellableCoroutine { continuation ->
                        val handler = Handler(Looper.getMainLooper())
                        val webView = WebView(applicationContext)
                        var pageFinished = false
                        var evaluating = false
                        var completed = false
                        var pollAttempt = 0
                        var commentWaits = 0

                        fun reportProgress(fraction: Float, stage: LoadStage) {
                            listener.onProgress(LoadProgress(fraction.coerceIn(0f, 1f), stage))
                        }

                        reportProgress(0.02f, LoadStage.Connecting)

                        fun cleanup() {
                            handler.removeCallbacksAndMessages(null)
                        webView.stopLoading()
                        webView.webViewClient = WebViewClient()
                        webView.loadUrl("about:blank")
                        webView.clearCache(true)
                        webView.clearHistory()
                        webView.removeAllViews()
                            webView.destroy()
                            clearRedditCookies()
                        }

                        fun fail(error: Throwable) {
                            if (completed) return
                            completed = true
                            cleanup()
                            if (continuation.isActive) continuation.resumeWithException(error)
                        }

                        fun succeed(post: ParsedRedditPost) {
                            if (completed) return
                            completed = true
                            cleanup()
                            if (continuation.isActive) continuation.resume(post)
                        }

                        lateinit var poll: Runnable
                        poll = Runnable {
                            if (completed || !pageFinished || evaluating) return@Runnable
                            evaluating = true
                            pollAttempt += 1
                            val elapsedFraction = (pollAttempt * pollIntervalMillis).toFloat() / timeoutMillis
                            reportProgress(
                                0.5f + 0.47f * elapsedFraction.coerceAtMost(1f),
                                LoadStage.ExtractingContent,
                            )
                            webView.evaluateJavascript(DOCUMENT_HTML_SCRIPT) { encodedHtml ->
                                evaluating = false
                                if (completed) return@evaluateJavascript
                                val html = decodeJavascriptString(encodedHtml)
                                when (val read = html?.let(RedditPageDocument::read)) {
                                    is RedditPageDocument.Read.Ready -> {
                                        captured.set(read.post)
                                        val waitingForComments = read.post.comments.isEmpty() &&
                                            read.post.commentCount > 0 &&
                                            commentWaits < MAX_COMMENT_WAITS
                                        if (waitingForComments) {
                                            commentWaits += 1
                                            handler.postDelayed(poll, pollIntervalMillis)
                                        } else {
                                            reportProgress(1f, LoadStage.ExtractingContent)
                                            succeed(read.post)
                                        }
                                    }
                                    is RedditPageDocument.Read.Failed -> fail(IOException(read.reason.withStatus(httpStatus.get())))
                                    else -> handler.postDelayed(poll, pollIntervalMillis)
                                }
                            }
                        }

                        webView.settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            userAgentString = RedditFetchPlan.DESKTOP_USER_AGENT
                            allowFileAccess = false
                            allowContentAccess = false
                            javaScriptCanOpenWindowsAutomatically = false
                            setSupportMultipleWindows(false)
                            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                            cacheMode = WebSettings.LOAD_NO_CACHE
                        }
                        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, false)
                        webView.setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_BOUND, true)
                        webView.webChromeClient = object : WebChromeClient() {
                            override fun onProgressChanged(view: WebView, newProgress: Int) {
                                if (completed) return
                                reportProgress(0.05f + 0.45f * (newProgress / 100f), LoadStage.FetchingPage)
                            }
                        }
                        webView.webViewClient = object : WebViewClient() {
                            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                                pageFinished = false
                            }

                            override fun onPageFinished(view: WebView, url: String) {
                                if (completed) return
                                if (RedditBrowserNavigation.isLogin(url)) {
                                    fail(IOException(RedditPageDocument.LOGIN))
                                    return
                                }
                                if (!RedditBrowserNavigation.isAllowed(url)) {
                                    fail(IOException("Reddit redirected to an unsupported page"))
                                    return
                                }
                                pageFinished = true
                                handler.removeCallbacks(poll)
                                handler.post(poll)
                            }

                            override fun shouldOverrideUrlLoading(
                                view: WebView,
                                request: WebResourceRequest,
                            ): Boolean {
                                if (!request.isForMainFrame) return false
                                val target = request.url.toString()
                                if (RedditBrowserNavigation.isLogin(target)) {
                                    fail(IOException(RedditPageDocument.LOGIN))
                                    return true
                                }
                                val allowed = RedditBrowserNavigation.isAllowed(target)
                                if (!allowed) fail(IOException("Blocked browser navigation away from Reddit"))
                                return !allowed
                            }

                            override fun onReceivedError(
                                view: WebView,
                                request: WebResourceRequest,
                                error: WebResourceError,
                            ) {
                                if (request.isForMainFrame) {
                                    fail(IOException("Reddit page failed to load in the browser: ${error.description}"))
                                }
                            }

                            override fun onReceivedHttpError(
                                view: WebView,
                                request: WebResourceRequest,
                                errorResponse: WebResourceResponse,
                            ) {
                                if (!request.isForMainFrame) return
                                httpStatus.set(errorResponse.statusCode)
                            }

                            override fun onRenderProcessGone(
                                view: WebView,
                                detail: RenderProcessGoneDetail,
                            ): Boolean {
                                fail(IOException("Reddit page renderer exited"))
                                return true
                            }
                        }

                        continuation.invokeOnCancellation {
                            if (Looper.myLooper() == Looper.getMainLooper()) {
                                if (!completed) {
                                    completed = true
                                    cleanup()
                                }
                            } else {
                                handler.post {
                                    if (!completed) {
                                        completed = true
                                        cleanup()
                                    }
                                }
                            }
                        }

                        startLoad(webView)
                    }
                }
            }
        } catch (timeout: TimeoutCancellationException) {
            captured.get()?.let { return it }
            val status = httpStatus.get()
            throw IOException(
                when (status) {
                    403, 429 -> "${RedditPageDocument.BLOCKED} (HTTP $status)"
                    in 400..599 -> "Reddit returned HTTP $status in the browser"
                    else -> RedditPageDocument.EMPTY
                },
            )
        }
    }

    private fun decodeJavascriptString(value: String): String? = runCatching {
        json.parseToJsonElement(value).jsonPrimitive.contentOrNull
    }.getOrNull()

    private fun String.withStatus(status: Int): String {
        if (status < 400 || this != RedditPageDocument.BLOCKED) return this
        return "$this (HTTP $status)"
    }

    private fun clearRedditCookies() {
        runCatching {
            val manager = CookieManager.getInstance()
            REDDIT_COOKIE_URLS.forEach { url ->
                val header = manager.getCookie(url) ?: return@forEach
                header.split(';').forEach { part ->
                    val name = part.substringBefore('=').trim()
                    if (name.isNotEmpty()) manager.setCookie(url, "$name=; Max-Age=0; Path=/")
                }
            }
            manager.flush()
        }
    }

    companion object {
        const val DEFAULT_TIMEOUT_MILLIS = 45_000L
        const val DEFAULT_POLL_INTERVAL_MILLIS = 500L
        private const val MAX_COMMENT_WAITS = 10
        private const val DOCUMENT_HTML_SCRIPT =
            "(function() { return document.documentElement ? document.documentElement.innerHTML : null; })();"
        private val REDDIT_COOKIE_URLS = listOf(
            "https://www.reddit.com/",
            "https://old.reddit.com/",
            "https://reddit.com/",
            "https://np.reddit.com/",
            "https://new.reddit.com/",
            "https://m.reddit.com/",
            "https://redd.it/",
        )
    }
}
