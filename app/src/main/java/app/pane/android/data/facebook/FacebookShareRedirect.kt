package app.pane.android.data.facebook

import java.net.URI

/**
 * Where a Facebook `/share/<id>/` interstitial actually points.
 * A redirect Location header wins. Otherwise the page's og:url or canonical link.
 */
internal object FacebookShareRedirect {
    fun nextUrl(status: Int, location: String?, html: String, current: String): String? {
        if (status in 300..399) {
            val header = location?.trim().orEmpty()
            if (header.isNotEmpty()) return absolute(header, current)
        }
        val embedded = landingInHtml(html) ?: return null
        return absolute(embedded, current)
    }

    fun landingInHtml(html: String): String? =
        LANDING.firstNotNullOfOrNull { pattern ->
            pattern.find(html)?.groupValues?.getOrNull(1)?.let(::decode)
        }

    fun absolute(location: String, current: String): String {
        val trimmed = decode(location.trim())
        if (trimmed.startsWith("https://") || trimmed.startsWith("http://")) return trimmed
        return runCatching { URI(current).resolve(trimmed).toString() }.getOrDefault(trimmed)
    }

    private fun decode(value: String): String =
        value.replace("&amp;", "&").replace("\\/", "/")

    private val LANDING = listOf(
        Regex("""property\s*=\s*["']og:url["'][^>]*content\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE),
        Regex("""content\s*=\s*["']([^"']+)["'][^>]*property\s*=\s*["']og:url["']""", RegexOption.IGNORE_CASE),
        Regex("""rel\s*=\s*["']canonical["'][^>]*href\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE),
        Regex("""href\s*=\s*["']([^"']+)["'][^>]*rel\s*=\s*["']canonical["']""", RegexOption.IGNORE_CASE),
    )
}
