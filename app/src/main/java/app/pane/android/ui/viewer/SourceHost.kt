package app.pane.android.ui.viewer

import java.net.URI
import java.util.Locale

internal fun displayHost(url: String): String {
    val host = runCatching { URI(url).host }.getOrNull()
        ?.removePrefix("www.")
        ?.lowercase(Locale.US)
        .orEmpty()
    return host.ifBlank { url }
}

internal fun sourceDisplayName(host: String): String = when {
    host == "x.com" || host.endsWith(".x.com") || host.contains("twitter.com") -> "X"
    host.contains("instagram.com") -> "Instagram"
    host.contains("reddit.com") -> "Reddit"
    host.contains("facebook.com") || host.contains("fb.com") -> "Facebook"
    host.contains("youtube.com") || host.contains("youtu.be") -> "YouTube"
    host.contains("tiktok.com") -> "TikTok"
    host.contains("threads.net") -> "Threads"
    else -> host.substringBefore('.').replaceFirstChar { it.uppercase() }
}

internal fun threadMicroLabel(host: String): ThreadLabel = when {
    host.contains("reddit.com") -> ThreadLabel.TopComments
    host.contains("instagram.com") -> ThreadLabel.Comments
    else -> ThreadLabel.Thread
}

internal enum class ThreadLabel { Thread, Comments, TopComments }

/** A link in the caption that is not the post itself. Used for the raised preview card. */
internal fun outboundLink(text: String, sourceUrl: String): String? {
    val match = URL.find(text) ?: return null
    val found = match.value.trimEnd('.', ',', ')', ']', '>')
    val sourceHost = displayHost(sourceUrl)
    val foundHost = displayHost(found)
    if (foundHost.isBlank() || foundHost == sourceHost) return null
    return found
}

private val URL = Regex("""https?://[^\s]+""", RegexOption.IGNORE_CASE)
