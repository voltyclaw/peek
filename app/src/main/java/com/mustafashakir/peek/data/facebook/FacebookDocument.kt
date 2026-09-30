package com.mustafashakir.peek.data.facebook

data class ParsedFacebookPost(
    val id: String,
    val canonicalUrl: String,
    val author: String,
    val text: String,
    val imageUrls: List<String>,
    val videoUrl: String?,
)

/**
 * Reads a public post from Facebook HTML, including the logged-out embed plugin.
 * A login wall without a real description is not a post.
 */
object FacebookDocument {
    const val UNAVAILABLE = "Facebook didn't return this public post. It may be private or blocked."
    const val LOGIN = "Facebook asked for a login"

    fun parse(html: String, id: String, canonicalUrl: String): ParsedFacebookPost? {
        if (html.isBlank()) return null
        val metas = metas(html)
        val title = metas["og:title"] ?: metas["twitter:title"]
        val description = metas["og:description"] ?: metas["twitter:description"]
        val images = listOfNotNull(metas["og:image"], metas["twitter:image"])
            .map(::cleanUrl)
            .filter(::isRemote)
            .distinct()
        val video = listOf("og:video:secure_url", "og:video:url", "og:video")
            .firstNotNullOfOrNull { metas[it] }
            ?.let(::cleanUrl)
            ?.takeIf { isRemote(it) && !isFacebookPage(it) }
        val text = listOf(description, title).firstOrNull { !it.isNullOrBlank() && !isGeneric(it) }
        if (text == null && images.isEmpty() && video == null) return null
        if (isLoginWall(html) && text == null) return null
        return ParsedFacebookPost(
            id = id,
            canonicalUrl = canonicalUrl,
            author = author(title) ?: "Facebook",
            text = text ?: title?.takeIf { !isGeneric(it) } ?: "Facebook post",
            imageUrls = images,
            videoUrl = video,
        )
    }

    private fun metas(html: String): Map<String, String> {
        val values = linkedMapOf<String, String>()
        META_TAG.findAll(html).forEach { match ->
            val tag = match.value
            val key = attr(tag, "property") ?: attr(tag, "name") ?: return@forEach
            val content = attr(tag, "content") ?: return@forEach
            if (content.isNotBlank()) values.putIfAbsent(key.lowercase(), unescape(content))
        }
        return values
    }

    private fun attr(tag: String, name: String): String? =
        Regex("""\b$name\s*=\s*(?:"([^"]*)"|'([^']*)')""", RegexOption.IGNORE_CASE)
            .find(tag)
            ?.let { it.groupValues[1].ifEmpty { it.groupValues[2] } }

    private fun author(title: String?): String? {
        val raw = title?.trim()?.takeIf { it.isNotEmpty() && !isGeneric(it) } ?: return null
        return raw
            .removeSuffix(" - Facebook")
            .removeSuffix(" | Facebook")
            .removeSuffix(" on Facebook")
            .removeSuffix(" on Reels")
            .substringBefore(" - ").trim()
            .takeIf { it.isNotEmpty() && !isGeneric(it) }
    }

    private fun isGeneric(value: String): Boolean {
        val text = value.trim().lowercase()
        if (text.isEmpty() || text == "facebook") return true
        return GENERIC.any { text.contains(it) }
    }

    private fun isLoginWall(html: String): Boolean {
        val text = html.lowercase()
        return "login_form" in text || "id=\"loginform\"" in text || "/login.php" in text && "password" in text
    }

    private fun isRemote(url: String): Boolean = url.startsWith("https://") || url.startsWith("http://")

    private fun isFacebookPage(url: String): Boolean {
        val host = runCatching { java.net.URI(url).host }.getOrNull()?.lowercase().orEmpty()
        return host == "facebook.com" || host.endsWith(".facebook.com") || host == "fb.watch" || host == "fb.com"
    }

    private fun cleanUrl(url: String): String = unescape(url).replace("&amp;", "&").trim()

    private fun unescape(value: String): String = value
        .replace("&amp;", "&")
        .replace("&quot;", "\"")
        .replace("&#039;", "'")
        .replace("&#39;", "'")
        .replace("&apos;", "'")
        .replace("&lt;", "<")
        .replace("&gt;", ">")

    private val META_TAG = Regex("""<meta\b[^>]*>""", RegexOption.IGNORE_CASE)
    private val GENERIC = listOf(
        "log into facebook",
        "log in to facebook",
        "facebook is a social",
        "create new account",
        "sign up for facebook",
    )
}
