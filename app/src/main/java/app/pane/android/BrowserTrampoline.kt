package app.pane.android

import app.pane.android.domain.model.LinkShims
import java.net.URI
import java.util.Locale

/**
 * Decides what a catch-all browser VIEW should do.
 * Meta hosts open in Pane. Everything else is handed to another browser.
 * The original URL is what the other browser receives, so a redirect shim
 * still follows the tap the person made.
 */
internal object BrowserTrampoline {
    sealed class Decision {
        data object Ignore : Decision()
        data class OpenInPane(val url: String) : Decision()
        data class HandOff(val url: String) : Decision()
    }

    data class Candidate(
        val packageName: String,
        val activityName: String,
    )

    fun decide(rawUrl: String?, enabled: Boolean): Decision {
        val raw = rawUrl?.trim().orEmpty()
        if (!raw.startsWith("http://") && !raw.startsWith("https://")) return Decision.Ignore
        if (!enabled) return Decision.HandOff(raw)
        val unwrapped = LinkShims.unwrap(raw)
        val host = hostOf(unwrapped) ?: return Decision.HandOff(raw)
        return if (isMetaHost(host)) Decision.OpenInPane(unwrapped) else Decision.HandOff(raw)
    }

    fun isMetaHost(host: String): Boolean {
        val normalized = host.lowercase(Locale.US).removePrefix("www.")
        if (normalized in EXACT_HOSTS) return true
        return META_SUFFIXES.any { suffix -> normalized.endsWith(suffix) }
    }

    fun pickHandoff(candidates: List<Candidate>, ownPackage: String): Candidate? {
        val others = candidates.filter { it.packageName != ownPackage }
        if (others.isEmpty()) return null
        for (packageName in PREFERRED_BROWSERS) {
            others.firstOrNull { it.packageName == packageName }?.let { return it }
        }
        return others.first()
    }

    private fun hostOf(url: String): String? =
        runCatching { URI(url).host }.getOrNull()?.takeIf { it.isNotBlank() }

    private val EXACT_HOSTS = setOf(
        "facebook.com",
        "instagram.com",
        "fb.com",
        "fb.me",
        "fb.watch",
    )

    private val META_SUFFIXES = listOf(
        ".facebook.com",
        ".instagram.com",
        ".fb.com",
        ".fb.me",
    )

    private val PREFERRED_BROWSERS = listOf(
        "com.android.chrome",
        "com.chrome.beta",
        "com.chrome.dev",
        "com.google.android.apps.chrome",
        "com.sec.android.app.sbrowser",
        "com.android.browser",
        "com.brave.browser",
        "org.mozilla.firefox",
        "com.microsoft.emmx",
    )
}
