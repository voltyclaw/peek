package app.pane.android.domain.model

import java.net.URI
import java.net.URLDecoder
import java.util.Locale

/**
 * Facebook and Instagram wrap some taps in redirect hosts. Those hosts do not publish
 * assetlinks.json without a redirect, so they are not verified for Meta's apps.
 * The `u` parameter is the post the person actually shared.
 */
object LinkShims {
    fun unwrap(url: String): String = unwrap(url, depth = 0)

    private fun unwrap(url: String, depth: Int): String {
        if (depth >= 3) return url
        val uri = runCatching { URI(url.trim()) }.getOrNull() ?: return url
        val host = uri.host?.lowercase(Locale.US)?.removePrefix("www.") ?: return url
        if (host !in SHIM_HOSTS) return url
        val target = query(uri, "u") ?: query(uri, "url") ?: return url
        if (!target.startsWith("http://") && !target.startsWith("https://")) return url
        return unwrap(target, depth + 1)
    }

    private fun query(uri: URI, name: String): String? {
        val raw = uri.rawQuery ?: return null
        return raw.split('&').firstNotNullOfOrNull { part ->
            val key = part.substringBefore('=')
            val value = part.substringAfter('=', "")
            if (key == name && value.isNotBlank()) {
                runCatching { URLDecoder.decode(value, Charsets.UTF_8.name()) }.getOrNull()
            } else {
                null
            }
        }
    }

    private val SHIM_HOSTS = setOf(
        "l.facebook.com",
        "lm.facebook.com",
        "l.instagram.com",
    )
}
