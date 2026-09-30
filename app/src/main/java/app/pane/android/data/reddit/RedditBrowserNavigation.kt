package app.pane.android.data.reddit

import java.net.URI
import java.util.Locale

/** Main-frame destinations a logged-out Reddit WebView is allowed to follow. */
internal object RedditBrowserNavigation {
    fun isLogin(url: String): Boolean {
        val uri = https(url) ?: return false
        if (uri.host?.lowercase(Locale.US) !in REDDIT_HOSTS) return false
        val path = uri.path.orEmpty()
        return path == "/login" || path.startsWith("/login/") ||
            path == "/register" || path.startsWith("/register/") ||
            path == "/account/login" || path.startsWith("/account/login/")
    }

    fun isAllowed(url: String): Boolean {
        if (isLogin(url)) return false
        val uri = https(url) ?: return false
        return uri.host?.lowercase(Locale.US) in REDDIT_HOSTS
    }

    private fun https(url: String): URI? {
        val uri = runCatching { URI(url) }.getOrNull() ?: return null
        if (!uri.scheme.equals("https", ignoreCase = true)) return null
        return uri
    }

    private val REDDIT_HOSTS = setOf(
        "reddit.com",
        "www.reddit.com",
        "old.reddit.com",
        "np.reddit.com",
        "new.reddit.com",
        "m.reddit.com",
        "redd.it",
        "www.redd.it",
    )
}
