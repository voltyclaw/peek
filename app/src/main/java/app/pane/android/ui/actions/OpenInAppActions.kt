package app.pane.android.ui.actions

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import app.pane.android.BrowserTrampoline
import app.pane.android.BrowserTrampolinePreferences
import app.pane.android.BuildConfig
import app.pane.android.InstalledBrowsers
import app.pane.android.R
import app.pane.android.data.facebook.FacebookUrls
import app.pane.android.data.instagram.InstagramStories
import app.pane.android.data.reddit.RedditUrls
import app.pane.android.data.x.XUrls
import app.pane.android.domain.model.LinkShims
import java.net.URI
import java.util.Locale

internal data class OpenInAppTarget(
    val uri: String,
    val packageName: String?,
    /** When the source app cannot open a story, hand this same URL to the chosen browser. */
    val handoffBrowser: Boolean = false,
)

internal fun openInAppLabelRes(url: String): Int = when {
    XUrls.supports(url) -> R.string.open_in_x
    FacebookUrls.supports(url) || FacebookUrls.isMarketplace(url) -> R.string.open_in_facebook
    RedditUrls.supports(url) -> R.string.open_in_reddit
    isInstagramUrl(url) -> R.string.open_in_instagram
    else -> R.string.open_in_app
}

/** Monochrome source glyph for the Open button. Null uses the generic arrow. */
internal fun sourceMarkRes(url: String): Int? = when {
    XUrls.supports(url) -> R.drawable.ic_source_x
    FacebookUrls.supports(url) || FacebookUrls.isMarketplace(url) -> R.drawable.ic_source_facebook
    RedditUrls.supports(url) -> R.drawable.ic_source_reddit
    isInstagramUrl(url) -> R.drawable.ic_source_instagram
    else -> null
}

internal fun openInAppPackages(url: String): List<String> =
    openInAppTargets(url).mapNotNull { it.packageName }.distinct()

/**
 * Package-targeted attempts first, then the same https URL with no package so a browser can open it.
 * [Context.startActivity] is attempted even when package visibility would hide the app from resolveActivity.
 */
internal fun openInAppTargets(url: String): List<OpenInAppTarget> {
    val unwrapped = LinkShims.unwrap(url.trim())
    if (unwrapped.isBlank()) return emptyList()
    val statusId = XUrls.parse(unwrapped)?.id
    if (statusId != null) {
        val https = "https://x.com/i/status/$statusId"
        return listOf(
            OpenInAppTarget(https, "com.twitter.android"),
            OpenInAppTarget("twitter://status?id=$statusId", "com.twitter.android"),
            OpenInAppTarget(https, null),
        )
    }
    val facebook = FacebookUrls.parse(unwrapped)
    if (facebook != null) {
        val handoff = if (facebook.kind == FacebookUrls.Kind.Story) facebook.sourceUrl else facebook.canonicalUrl
        val story = facebook.kind == FacebookUrls.Kind.Story
        return listOf(
            OpenInAppTarget(handoff, "com.facebook.katana"),
            OpenInAppTarget(handoff, "com.facebook.lite"),
            OpenInAppTarget(handoff, null, handoffBrowser = story),
        )
    }
    if (RedditUrls.supports(unwrapped)) {
        val https = redditHttps(unwrapped)
        val path = runCatching { URI(https).rawPath }.getOrNull().orEmpty().ifBlank { "/" }
        return listOf(
            OpenInAppTarget(https, "com.reddit.frontpage"),
            OpenInAppTarget("reddit://reddit$path", "com.reddit.frontpage"),
            OpenInAppTarget(https, null),
        )
    }
    if (FacebookUrls.isMarketplace(unwrapped)) {
        return listOf(
            OpenInAppTarget(unwrapped, "com.facebook.katana"),
            OpenInAppTarget(unwrapped, "com.facebook.lite"),
            OpenInAppTarget(unwrapped, null),
        )
    }
    val igStory = InstagramStories.parse(unwrapped)
    if (igStory != null) {
        return listOf(
            OpenInAppTarget(igStory.fetchUrl, "com.instagram.android"),
            OpenInAppTarget(igStory.fetchUrl, null, handoffBrowser = true),
        )
    }
    if (isInstagramUrl(unwrapped)) {
        val https = instagramHttps(unwrapped)
        return listOf(
            OpenInAppTarget(https, "com.instagram.android"),
            OpenInAppTarget(https, null),
        )
    }
    return listOf(OpenInAppTarget(unwrapped, null))
}

fun openPostInApp(context: Context, url: String): Boolean {
    if (url.isBlank()) return false
    val targets = openInAppTargets(url)
    if (BuildConfig.DEBUG) {
        Log.i("PaneOpen", "open incoming=$url outgoing=${targets.firstOrNull()?.uri.orEmpty()}")
    }
    for (target in targets) {
        val intent = if (target.handoffBrowser) {
            handoffBrowserIntent(context, target.uri) ?: continue
        } else {
            Intent(Intent.ACTION_VIEW, Uri.parse(target.uri)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).apply {
                if (target.packageName != null) setPackage(target.packageName)
            }
        }
        val started = try {
            context.startActivity(intent)
            true
        } catch (_: ActivityNotFoundException) {
            false
        } catch (_: SecurityException) {
            false
        }
        if (started) return true
    }
    return false
}

private fun handoffBrowserIntent(context: Context, url: String): Intent? {
    val browsers = InstalledBrowsers.list(context)
    val pick = BrowserTrampoline.pickHandoff(
        candidates = browsers.map { browser ->
            BrowserTrampoline.Candidate(browser.packageName, browser.activityName)
        },
        ownPackage = context.packageName,
        preferredPackage = BrowserTrampolinePreferences.readHandoff(context),
        systemPackage = InstalledBrowsers.systemPackage(context),
    ) ?: return null
    return Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
        component = ComponentName(pick.packageName, pick.activityName)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}

private fun redditHttps(url: String): String {
    RedditUrls.fetchPageUrl(url)?.let { return it }
    val uri = runCatching { URI(url) }.getOrNull() ?: return url
    val path = uri.rawPath?.takeIf { it.isNotBlank() } ?: "/"
    val query = uri.rawQuery?.let { "?$it" }.orEmpty()
    return "https://www.reddit.com$path$query"
}

private fun instagramHttps(url: String): String {
    val uri = runCatching { URI(url) }.getOrNull() ?: return url
    val path = uri.rawPath?.takeIf { it.isNotBlank() } ?: "/"
    val query = uri.rawQuery?.let { "?$it" }.orEmpty()
    return "https://www.instagram.com$path$query"
}

private fun isInstagramUrl(url: String): Boolean {
    val host = runCatching { URI(url.trim()).host }.getOrNull()
        ?.lowercase(Locale.US)
        ?.removePrefix("www.")
        ?: return false
    return host == "instagram.com" || host == "m.instagram.com" || host == "instagr.am" || host == "l.instagram.com"
}
