package app.pane.android.data.reddit

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
import android.view.View
import android.webkit.WebView
import android.webkit.WebViewClient
import app.pane.android.data.resolver.PageLoadProgressElement
import app.pane.android.domain.model.LoadProgress
import app.pane.android.domain.model.LoadStage
import app.pane.android.domain.repository.LoadProgressListener
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
                        var polling = false
                        var evaluating = false
                        var completed = false
                        var pollAttempt = 0
                        var commentWaits = 0
                        var galleryWaits = 0
                        var lastGalleryCount = -1
                        var stableGalleryPolls = 0

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
                                        val galleryCount = read.post.media.count { it.isDisplayableMedia() }
                                        if (galleryCount == lastGalleryCount) {
                                            stableGalleryPolls += 1
                                        } else {
                                            stableGalleryPolls = 0
                                        }
                                        lastGalleryCount = galleryCount
                                        val waitingForComments = read.post.comments.isEmpty() &&
                                            read.post.commentCount > 0 &&
                                            commentWaits < MAX_COMMENT_WAITS
                                        val waitingForGallery = RedditGalleryWait.shouldWait(
                                            mediaPending = read.post.mediaPending,
                                            displayableCount = galleryCount,
                                            stablePolls = stableGalleryPolls,
                                            waits = galleryWaits,
                                        )
                                        if (waitingForComments || waitingForGallery) {
                                            if (waitingForComments) commentWaits += 1
                                            if (waitingForGallery) galleryWaits += 1
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

                        fun beginReading(url: String) {
                            if (completed) return
                            if (RedditBrowserNavigation.isLogin(url)) {
                                fail(IOException(RedditPageDocument.LOGIN))
                                return
                            }
                            if (!RedditBrowserNavigation.isAllowed(url)) {
                                fail(IOException("Reddit redirected to an unsupported page"))
                                return
                            }
                            if (polling) return
                            polling = true
                            pageFinished = true
                            handler.post(poll)
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
                                polling = false
                                handler.removeCallbacks(poll)
                            }

                            override fun onPageCommitVisible(view: WebView, url: String) {
                                beginReading(url)
                            }

                            override fun onPageFinished(view: WebView, url: String) {
                                beginReading(url)
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

                        webView.measure(
                            View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
                            View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY),
                        )
                        webView.layout(0, 0, 1080, 1920)
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
        private val DOCUMENT_HTML_SCRIPT = """
            (function() {
              if (!window.__peekGallery) window.__peekGallery = [];
              function keyOf(url) {
                try { var u = new URL(url); return (u.hostname + u.pathname).toLowerCase(); }
                catch (e) { return url.split('?')[0]; }
              }
              function consider(img) {
                if (!img || !img.getAttribute) return;
                var role = img.getAttribute('role') || '';
                var cls = img.getAttribute('class') || '';
                if (role === 'presentation' || cls.indexOf('post-background-image-filter') >= 0) return;
                var src = img.currentSrc || img.getAttribute('src') || '';
                var srcset = img.getAttribute('srcset') || '';
                var best = src;
                var bestW = -1;
                if (srcset) {
                  srcset.split(',').forEach(function(part) {
                    var bits = part.trim().split(/\s+/);
                    var u = bits[0] || '';
                    var w = 0;
                    if (bits[1] && /w${'$'}/.test(bits[1])) w = parseInt(bits[1], 10) || 0;
                    if (u.indexOf('https://') === 0 && w >= bestW) { bestW = w; best = u; }
                  });
                }
                if (!best || best.indexOf('https://') !== 0) return;
                var key = keyOf(best);
                for (var i = 0; i < window.__peekGallery.length; i++) {
                  if (window.__peekGallery[i].key === key) return;
                }
                window.__peekGallery.push({
                  key: key,
                  url: best,
                  w: img.getAttribute('width') || '',
                  h: img.getAttribute('height') || ''
                });
              }
              function harvest(node, depth) {
                if (!node || depth > 8 || !node.querySelectorAll) return;
                var imgs = node.querySelectorAll('img, source');
                for (var i = 0; i < imgs.length; i++) consider(imgs[i]);
                if (node.shadowRoot) harvest(node.shadowRoot, depth + 1);
                var children = node.children || [];
                for (var c = 0; c < children.length; c++) harvest(children[c], depth + 1);
              }
              var post = document.querySelector('shreddit-post');
              var postId = post && post.getAttribute('id');
              var carousels = document.querySelectorAll('gallery-carousel');
              for (var i = 0; i < carousels.length; i++) {
                var pid = carousels[i].getAttribute('post-id');
                if (postId && pid && pid !== postId) continue;
                harvest(carousels[i], 0);
                var root = carousels[i].shadowRoot || carousels[i];
                var buttons = root.querySelectorAll ? root.querySelectorAll('button, [role="button"]') : [];
                for (var b = 0; b < buttons.length; b++) {
                  if (buttons[b].disabled) continue;
                  var label = (buttons[b].getAttribute('aria-label') || '').toLowerCase();
                  if (label.indexOf('next') >= 0) { buttons[b].click(); break; }
                }
              }
              if (!window.__peekJsonState) {
                window.__peekJsonState = 'start';
                var permalink = post && post.getAttribute('permalink');
                var type = (post && post.getAttribute('post-type') || '').toLowerCase();
                if (permalink && type === 'gallery') {
                  var path = permalink.charAt(0) === '/' ? permalink : '/' + permalink;
                  if (path.charAt(path.length - 1) !== '/') path += '/';
                  fetch('https://www.reddit.com' + path + '.json?raw_json=1', {credentials: 'include'})
                    .then(function(response) { return response.ok ? response.text() : ''; })
                    .then(function(text) { window.__peekJson = text || ''; window.__peekJsonState = 'done'; })
                    .catch(function() { window.__peekJsonState = 'done'; });
                } else {
                  window.__peekJsonState = 'done';
                }
              }
              var extra = window.__peekGallery.map(function(item) {
                return '<img src="' + String(item.url).replace(/"/g, '&quot;') + '" width="' + item.w + '" height="' + item.h + '">';
              }).join('');
              var html = document.documentElement ? document.documentElement.innerHTML : '';
              if (window.__peekJson && window.__peekJson.indexOf('gallery_data') >= 0) {
                html += '<script type="application/json" id="peek-json">' + window.__peekJson.replace(/</g, '\\u003c') + '</script>';
              }
              return html + '<peek-gallery>' + extra + '</peek-gallery>';
            })();
        """.trimIndent()
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
