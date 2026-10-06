package app.pane.android.ui.actions

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import app.pane.android.R
import app.pane.android.data.facebook.FacebookUrls
import app.pane.android.data.reddit.RedditUrls
import app.pane.android.data.x.XUrls
import app.pane.android.domain.model.LinkShims
import java.net.URI
import java.util.Locale

internal data class OpenInAppTarget(val uri: String, val packageName: String?)

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
    val facebook = FacebookUrls.parse(unwrapped)?.canonicalUrl
    if (facebook != null) {
        return listOf(
            OpenInAppTarget(facebook, "com.facebook.katana"),
            OpenInAppTarget(facebook, "com.facebook.lite"),
            OpenInAppTarget(facebook, null),
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
    for (target in openInAppTargets(url)) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(target.uri)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (target.packageName != null) intent.setPackage(target.packageName)
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
