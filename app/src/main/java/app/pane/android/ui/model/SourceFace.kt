package app.pane.android.ui.model

import java.net.URI

/** Short chip and readable source name for a public post host. */
data class SourceFace(val chip: String, val label: String) {
    companion object {
        val None = SourceFace("", "")
    }
}

fun sourceFace(url: String): SourceFace {
    val host = runCatching { URI(url).host }.getOrNull()
        ?.lowercase()
        ?.removePrefix("www.")
        .orEmpty()
    if (host.isEmpty()) return SourceFace.None
    return when {
        host == "instagram.com" || host.endsWith(".instagram.com") -> SourceFace("IG", "Instagram")
        host == "reddit.com" || host.endsWith(".reddit.com") || host == "redd.it" || host.endsWith(".redd.it") ->
            SourceFace("Reddit", "Reddit")
        host == "facebook.com" || host.endsWith(".facebook.com") || host == "fb.com" || host.endsWith(".fb.com") ||
            host == "fb.watch" || host == "fb.me" -> SourceFace("FB", "Facebook")
        host == "x.com" || host.endsWith(".x.com") || host == "twitter.com" || host.endsWith(".twitter.com") ->
            SourceFace("X", "X")
        else -> SourceFace.None
    }
}
