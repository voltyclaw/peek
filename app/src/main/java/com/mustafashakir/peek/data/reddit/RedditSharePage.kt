package com.mustafashakir.peek.data.reddit

import java.net.URI

/**
 * Finds the public post URL inside a Reddit share page that did not HTTP-redirect.
 * Prefers canonical and social metadata over later links in the document.
 */
object RedditSharePage {
    fun postUrl(html: String): String? =
        candidates(html).firstNotNullOfOrNull { candidate ->
            RedditUrls.fetchPageUrl(absolutize(unescape(candidate)))
        }

    fun absolutize(value: String, base: String = DEFAULT_BASE): String {
        val cleaned = unescape(value).trim()
        return when {
            cleaned.startsWith("https://", ignoreCase = true) -> cleaned
            cleaned.startsWith("http://", ignoreCase = true) -> "https://${cleaned.substring(7)}"
            cleaned.startsWith("//") -> "https:$cleaned"
            cleaned.startsWith("/") -> "https://www.reddit.com$cleaned"
            else -> runCatching { URI(base).resolve(cleaned).toASCIIString() }.getOrDefault(cleaned)
        }
    }

    private fun candidates(html: String): Sequence<String> = sequence {
        yieldAll(groupValues(CANONICAL_REL_THEN_HREF, html))
        yieldAll(groupValues(CANONICAL_HREF_THEN_REL, html))
        yieldAll(groupValues(OG_URL_PROPERTY_THEN_CONTENT, html))
        yieldAll(groupValues(OG_URL_CONTENT_THEN_PROPERTY, html))
        yieldAll(groupValues(META_REFRESH, html))
        yieldAll(groupValues(JS_LOCATION, html))
        yieldAll(groupValues(PERMALINK_ATTR, html))
    }

    private fun groupValues(pattern: Regex, html: String): Sequence<String> =
        pattern.findAll(html).map { it.groupValues[1] }

    private fun unescape(value: String): String =
        value.trim()
            .replace("\\u002F", "/", ignoreCase = true)
            .replace("\\u0026", "&", ignoreCase = true)
            .replace("\\/", "/")
            .replace("&amp;", "&")

    private const val DEFAULT_BASE = "https://www.reddit.com"
    private val CANONICAL_REL_THEN_HREF = Regex(
        """<link\b[^>]*\brel\s*=\s*["']canonical["'][^>]*\bhref\s*=\s*["']([^"']+)["']""",
        RegexOption.IGNORE_CASE,
    )
    private val CANONICAL_HREF_THEN_REL = Regex(
        """<link\b[^>]*\bhref\s*=\s*["']([^"']+)["'][^>]*\brel\s*=\s*["']canonical["']""",
        RegexOption.IGNORE_CASE,
    )
    private val OG_URL_PROPERTY_THEN_CONTENT = Regex(
        """<meta\b[^>]*(?:property|name)\s*=\s*["']og:url["'][^>]*\bcontent\s*=\s*["']([^"']+)["']""",
        RegexOption.IGNORE_CASE,
    )
    private val OG_URL_CONTENT_THEN_PROPERTY = Regex(
        """<meta\b[^>]*\bcontent\s*=\s*["']([^"']+)["'][^>]*(?:property|name)\s*=\s*["']og:url["']""",
        RegexOption.IGNORE_CASE,
    )
    private val META_REFRESH = Regex(
        """<meta\b[^>]*\bhttp-equiv\s*=\s*["']refresh["'][^>]*\bcontent\s*=\s*["'][^"']*?\burl\s*=\s*([^"'\s>]+)""",
        RegexOption.IGNORE_CASE,
    )
    private val JS_LOCATION = Regex(
        """(?:window\s*\.\s*)?location(?:\s*\.\s*(?:href|assign|replace))?\s*(?:=\s*|\(\s*)["']([^"']+)["']""",
        RegexOption.IGNORE_CASE,
    )
    private val PERMALINK_ATTR = Regex(
        """<shreddit-post\b[^>]*\bpermalink\s*=\s*["']([^"']+)["']""",
        RegexOption.IGNORE_CASE,
    )
}
