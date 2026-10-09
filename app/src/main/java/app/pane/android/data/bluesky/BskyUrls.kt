package app.pane.android.data.bluesky

import app.pane.android.domain.model.LinkShims
import java.net.URI
import java.util.Locale

/** A Bluesky post the AppView can load. [actor] is a handle or a DID. */
internal data class BskyPostRef(val actor: String, val rkey: String) {
    val atUri: String get() = "at://$actor/app.bsky.feed.post/$rkey"

    fun https(actor: String = this.actor): String = "https://bsky.app/profile/$actor/post/$rkey"
}

internal object BskyUrls {
    const val PACKAGE = "xyz.blueskyweb.app"

    fun parsePost(raw: String): BskyPostRef? {
        val text = LinkShims.unwrap(raw.trim())
        if (text.startsWith("at://")) return atPost(text)
        val uri = runCatching { URI(text) }.getOrNull() ?: return null
        if (!uri.scheme.equals("https", true) && !uri.scheme.equals("http", true)) return null
        val host = uri.host?.lowercase(Locale.US)?.removePrefix("www.") ?: return null
        val segments = uri.path.orEmpty().trim('/').split('/').filter(String::isNotEmpty)
        if (host == "embed.bsky.app") return embedPost(segments)
        if (host != "bsky.app") return null
        return httpsPost(segments)
    }

    /** Profile pages hand off. Posts, feeds, and starter packs do not. */
    fun isProfile(raw: String): Boolean {
        val text = LinkShims.unwrap(raw.trim())
        if (parsePost(text) != null) return false
        val uri = runCatching { URI(text) }.getOrNull() ?: return false
        if (!uri.scheme.equals("https", true) && !uri.scheme.equals("http", true)) return false
        val host = uri.host?.lowercase(Locale.US)?.removePrefix("www.") ?: return false
        if (host != "bsky.app") return false
        val segments = uri.path.orEmpty().trim('/').split('/').filter(String::isNotEmpty)
        return segments.size == 2 && segments[0] == "profile" && segments[1].isNotBlank()
    }

    fun isHost(raw: String): Boolean {
        val text = LinkShims.unwrap(raw.trim())
        if (text.startsWith("at://")) return text.contains("/app.bsky.feed.post/")
        val host = runCatching { URI(text).host }.getOrNull()?.lowercase(Locale.US)?.removePrefix("www.")
        return host == "bsky.app" || host == "embed.bsky.app" || host == "go.bsky.app"
    }

    private fun httpsPost(segments: List<String>): BskyPostRef? {
        if (segments.size < 4 || segments[0] != "profile" || segments[2] != "post") return null
        val actor = segments[1].trim()
        val rkey = segments[3].trim()
        if (!validActor(actor) || !validRkey(rkey)) return null
        return BskyPostRef(actor, rkey)
    }

    private fun embedPost(segments: List<String>): BskyPostRef? {
        // embed/{did}/app.bsky.feed.post/{rkey}
        if (segments.size < 4 || segments[0] != "embed") return null
        if (segments[2] != "app.bsky.feed.post") return null
        val actor = segments[1].trim()
        val rkey = segments[3].trim()
        if (!validActor(actor) || !validRkey(rkey)) return null
        return BskyPostRef(actor, rkey)
    }

    private fun atPost(text: String): BskyPostRef? {
        val body = text.removePrefix("at://").substringBefore('?').trim('/')
        val parts = body.split('/').filter(String::isNotEmpty)
        if (parts.size < 3 || parts[1] != "app.bsky.feed.post") return null
        val actor = parts[0]
        val rkey = parts[2]
        if (!validActor(actor) || !validRkey(rkey)) return null
        return BskyPostRef(actor, rkey)
    }

    private fun validActor(actor: String): Boolean {
        if (actor.isBlank() || actor.length > 253) return false
        if (actor.startsWith("did:")) return actor.length > 4 && !actor.contains('/')
        return actor.none { it.isWhitespace() || it == '/' }
    }

    private fun validRkey(rkey: String): Boolean =
        rkey.isNotBlank() && rkey.length <= 512 && rkey.none { it == '/' || it.isWhitespace() }
}
