package app.pane.android.ui.text

/**
 * Inline mentions and bare URLs inside a post or comment.
 * Hashtags stay plain text. An email address is not an @handle.
 * Trailing punctuation is not part of the handle or the URL.
 */
internal enum class MentionNetwork { X, Instagram, Reddit, Threads, Other }

internal enum class MentionKind { XHandle, InstagramHandle, ThreadsHandle, RedditUser, RedditSub, Url }

internal data class MentionHit(
    val start: Int,
    val end: Int,
    val url: String,
    val kind: MentionKind,
    val token: String,
)

internal fun mentionNetwork(urlOrHost: String): MentionNetwork {
    val value = urlOrHost.lowercase()
    return when {
        "threads.net" in value -> MentionNetwork.Threads
        "instagram.com" in value || "instagr.am" in value -> MentionNetwork.Instagram
        "reddit.com" in value -> MentionNetwork.Reddit
        "x.com" in value || "twitter.com" in value -> MentionNetwork.X
        else -> MentionNetwork.Other
    }
}

internal fun mentionHits(text: String, network: MentionNetwork): List<MentionHit> {
    val hits = mutableListOf<MentionHit>()
    var index = 0
    while (index < text.length) {
        val hit = mentionAt(text, index, network)
        if (hit == null) {
            index += 1
        } else {
            hits += hit
            index = hit.end
        }
    }
    return hits
}

internal fun mentionAt(text: String, index: Int, network: MentionNetwork): MentionHit? {
    urlAt(text, index)?.let { url ->
        return MentionHit(index, index + url.length, url, MentionKind.Url, url)
    }
    return when (network) {
        MentionNetwork.X -> handleAt(text, index, MentionKind.XHandle, X_HANDLE) { "https://x.com/$it" }
        MentionNetwork.Instagram -> handleAt(text, index, MentionKind.InstagramHandle, IG_HANDLE) {
            "https://www.instagram.com/$it/"
        }
        MentionNetwork.Threads -> handleAt(text, index, MentionKind.ThreadsHandle, IG_HANDLE) {
            "https://www.threads.net/@$it"
        }
        MentionNetwork.Reddit -> redditAt(text, index)
        MentionNetwork.Other -> null
    }
}

/** Screen-reader copy. One placeholder, no styling words. */
internal fun mentionSpoken(kind: MentionKind, token: String, opensInApp: Boolean): String = when (kind) {
    MentionKind.XHandle -> if (opensInApp) "Open @$token on X" else "Open @$token in browser"
    MentionKind.InstagramHandle -> if (opensInApp) "Open @$token on Instagram" else "Open @$token in browser"
    MentionKind.ThreadsHandle -> if (opensInApp) "Open @$token on Threads" else "Open @$token in browser"
    MentionKind.RedditUser -> if (opensInApp) "Open u/$token on Reddit" else "Open u/$token in browser"
    MentionKind.RedditSub -> if (opensInApp) "Open r/$token on Reddit" else "Open r/$token in browser"
    MentionKind.Url -> "Open link"
}

internal fun profileSpoken(sourceName: String): String = "Open profile on $sourceName"

private fun handleAt(
    text: String,
    index: Int,
    kind: MentionKind,
    pattern: Regex,
    url: (String) -> String,
): MentionHit? {
    if (text.getOrNull(index) != '@') return null
    val previous = text.getOrNull(index - 1)
    if (previous != null && (previous.isLetterOrDigit() || previous == '.' || previous == '@')) return null
    val raw = takeWhile(text, index + 1) { char -> char.isLetterOrDigit() || char == '_' || char == '.' }
    val handle = raw.trimEnd('.')
    if (handle.isEmpty() || !pattern.matches(handle)) return null
    return MentionHit(index, index + 1 + handle.length, url(handle), kind, handle)
}

private fun redditAt(text: String, index: Int): MentionHit? {
    val previous = text.getOrNull(index - 1)
    if (previous != null && (previous.isLetterOrDigit() || previous == '/')) return null
    val marker = when {
        text.startsWith("u/", index, ignoreCase = true) -> "u"
        text.startsWith("r/", index, ignoreCase = true) -> "r"
        else -> return null
    }
    val raw = takeWhile(text, index + 2) { char -> char.isLetterOrDigit() || char == '_' }
    if (!REDDIT_NAME.matches(raw)) return null
    val kind = if (marker == "u") MentionKind.RedditUser else MentionKind.RedditSub
    val path = if (marker == "u") "user" else "r"
    return MentionHit(index, index + 2 + raw.length, "https://www.reddit.com/$path/$raw", kind, raw)
}

private fun takeWhile(text: String, start: Int, allowed: (Char) -> Boolean): String {
    var end = start
    while (end < text.length && allowed(text[end])) end += 1
    return text.substring(start, end)
}

private fun urlAt(text: String, index: Int): String? {
    val rest = text.substring(index)
    val marker = when {
        rest.startsWith("https://", ignoreCase = true) -> "https://"
        rest.startsWith("http://", ignoreCase = true) -> "http://"
        else -> return null
    }
    if (index > 0) {
        val previous = text[index - 1]
        if (previous.isLetterOrDigit() || previous == '@' || previous == '/') return null
    }
    val cut = rest.indexOfAny(charArrayOf(' ', '\n', '\t', '<', '>', '"', '\'')).let { if (it < 0) rest.length else it }
    var url = rest.substring(0, cut)
    while (url.length > marker.length && url.last() in TRAILING) url = url.dropLast(1)
    if (url.length <= marker.length || url.contains("://t.co/", ignoreCase = true)) return null
    return url
}

private val X_HANDLE = Regex("[A-Za-z0-9_]{1,15}")
private val IG_HANDLE = Regex("[A-Za-z0-9._]{1,30}")
private val REDDIT_NAME = Regex("[A-Za-z0-9_]{2,21}")
private const val TRAILING = ".,;:!?)\\]}>\"'”’"
