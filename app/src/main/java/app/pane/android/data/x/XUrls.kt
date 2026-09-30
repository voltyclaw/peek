package app.pane.android.data.x

import java.net.URI
import java.util.Locale

/**
 * Public X and Twitter status URLs. Profiles, search, and home are not posts.
 * Host matching stays here so a later applicationId change does not touch it.
 */
object XUrls {
    data class Status(
        val id: String,
        val canonicalUrl: String,
    )

    fun supports(url: String): Boolean = parse(url) != null

    fun parse(url: String): Status? {
        val uri = runCatching { URI(url.trim()) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase(Locale.US) ?: return null
        if (scheme != "https" && scheme != "http") return null
        val host = uri.host?.lowercase(Locale.US)?.removePrefix("www.") ?: return null
        if (host !in HOSTS) return null
        val segments = uri.path.orEmpty().trimEnd('/').split('/').filter(String::isNotEmpty)
        val statusIndex = segments.indexOf("status")
        if (statusIndex < 0 || statusIndex + 1 >= segments.size) return null
        val id = segments[statusIndex + 1]
        if (!ID.matches(id)) return null
        return Status(id = id, canonicalUrl = "https://x.com/i/status/$id")
    }

    private val ID = Regex("[0-9]{2,}")
    private val HOSTS = setOf(
        "x.com",
        "mobile.x.com",
        "twitter.com",
        "mobile.twitter.com",
    )
}
