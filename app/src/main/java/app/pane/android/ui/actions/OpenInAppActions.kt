package app.pane.android.ui.actions

import android.content.Context
import android.content.Intent
import android.net.Uri
import app.pane.android.R
import app.pane.android.data.facebook.FacebookUrls
import app.pane.android.data.reddit.RedditUrls
import app.pane.android.data.x.XUrls
import java.net.URI
import java.util.Locale

internal fun openInAppLabelRes(url: String): Int = when {
    XUrls.supports(url) -> R.string.open_in_x
    FacebookUrls.supports(url) -> R.string.open_in_facebook
    RedditUrls.supports(url) -> R.string.open_in_reddit
    isInstagramUrl(url) -> R.string.open_in_instagram
    else -> R.string.open_in_app
}

internal fun openInAppPackages(url: String): List<String> = when {
    XUrls.supports(url) -> listOf("com.twitter.android")
    FacebookUrls.supports(url) -> listOf("com.facebook.katana", "com.facebook.lite")
    RedditUrls.supports(url) -> listOf("com.reddit.frontpage")
    isInstagramUrl(url) -> listOf("com.instagram.android")
    else -> emptyList()
}

fun openPostInApp(context: Context, url: String): Boolean {
    if (url.isBlank()) return false
    val view = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    for (packageName in openInAppPackages(url)) {
        val targeted = Intent(view).setPackage(packageName)
        if (targeted.resolveActivity(context.packageManager) != null) {
            return runCatching { context.startActivity(targeted) }.isSuccess
        }
    }
    val statusId = XUrls.parse(url)?.id
    if (statusId != null) {
        val deepLink = Intent(Intent.ACTION_VIEW, Uri.parse("twitter://status?id=$statusId"))
            .setPackage("com.twitter.android")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (deepLink.resolveActivity(context.packageManager) != null) {
            return runCatching { context.startActivity(deepLink) }.isSuccess
        }
    }
    return runCatching { context.startActivity(view) }.isSuccess
}

private fun isInstagramUrl(url: String): Boolean {
    val host = runCatching { URI(url.trim()).host }.getOrNull()
        ?.lowercase(Locale.US)
        ?.removePrefix("www.")
        ?: return false
    return host == "instagram.com"
}
