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

internal fun sourceDisplayNameRes(host: String): Int? = app.pane.android.ui.actions.sourceNameRes(host)

/** Host label when the source has no string resource. Known apps use [sourceDisplayNameRes]. */
internal fun sourceDisplayNameFallback(host: String): String =
    host.substringBefore('.').replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString() }

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
    if (foundHost.isBlank() || foundHost == sourceHost || foundHost == "t.co") return null
    return found
}

private val URL = Regex("""https?://[^\s]+""", RegexOption.IGNORE_CASE)
