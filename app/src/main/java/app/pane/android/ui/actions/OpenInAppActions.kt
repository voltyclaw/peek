package app.pane.android.ui.actions

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
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
        val shortShare = facebook.kind == FacebookUrls.Kind.ShareShort
        val handoff = if (facebook.kind == FacebookUrls.Kind.Story || shortShare) {
            facebook.sourceUrl
        } else {
            facebook.canonicalUrl
        }
        val story = facebook.kind == FacebookUrls.Kind.Story
        return listOf(
            OpenInAppTarget(handoff, "com.facebook.katana"),
            OpenInAppTarget(handoff, "com.facebook.lite"),
            OpenInAppTarget(handoff, null, handoffBrowser = story || shortShare),
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

fun openPostInApp(context: Context, url: String): Boolean = openExternally(context, url)

/**
 * One Open path for the body button, the coral tile, and the thread hard-wall tile.
 * The intent carries the full incoming URL. A source app is used only when
 * resolveActivity says that app will handle it. Otherwise the handoff browser,
 * then the system chooser, receives the same URL. A package-less VIEW is never
 * started, so the tap cannot land back inside Pane.
 */
fun openExternally(context: Context, url: String): Boolean {
    if (url.isBlank()) return false
    val plan = externalOpen(url, context.packageName) { packageName ->
        resolveExternalPackage(context, externalOpenUrl(url), packageName)
    }
    if (BuildConfig.DEBUG) {
        Log.i("PaneOpen", "open incoming=$url outgoing=${plan.url} package=${plan.packageName}")
    }
    if (plan.packageName != null && startView(context, plan.url, plan.packageName)) return true
    val browser = handoffBrowserIntent(context, plan.url)
    if (browser != null && start(context, browser)) return true
    val chooser = Intent.createChooser(viewIntent(plan.url, null), null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    return start(context, chooser)
}

internal data class ExternalOpen(
    val url: String,
    val packageName: String?,
    val opensInApp: Boolean,
    val appNameRes: Int?,
    val sourceKnown: Boolean,
    val markRes: Int?,
)

internal enum class RecoveryReason { Unloadable, Offline, Timeout, Other }

internal enum class RecoveryHeadline { NotPublic, CantShow, Offline, CouldntLoad }

internal enum class RecoveryBody { NamedApp, Browser, CheckConnection }

internal data class RecoveryPresentation(
    val headline: RecoveryHeadline,
    val body: RecoveryBody,
    val retryPrimary: Boolean,
    val opensInApp: Boolean,
    val appNameRes: Int?,
    val openUrl: String,
    val markRes: Int?,
)

internal fun externalOpenUrl(url: String): String {
    val targets = openInAppTargets(url)
    return targets.firstOrNull()?.uri ?: LinkShims.unwrap(url)
}

internal fun externalOpen(
    url: String,
    ownPackage: String,
    resolve: (packageName: String) -> String?,
): ExternalOpen {
    val openUrl = externalOpenUrl(url)
    val appNameRes = sourceAppNameRes(url)
    for (target in openInAppTargets(url)) {
        val packageName = target.packageName ?: continue
        if (packageName == ownPackage || isBrowserPackage(packageName)) continue
        val resolved = resolve(packageName) ?: continue
        if (resolved != packageName || resolved == ownPackage || isBrowserPackage(resolved)) continue
        return ExternalOpen(
            url = openUrl,
            packageName = packageName,
            opensInApp = appNameRes != null,
            appNameRes = appNameRes,
            sourceKnown = appNameRes != null,
            markRes = sourceMarkFor(appNameRes),
        )
    }
    return ExternalOpen(
        url = openUrl,
        packageName = null,
        opensInApp = false,
        appNameRes = appNameRes,
        sourceKnown = appNameRes != null,
        markRes = null,
    )
}

internal fun recoveryPresentation(
    url: String,
    reason: RecoveryReason = RecoveryReason.Unloadable,
    ownPackage: String = "",
    resolve: (String) -> String? = { null },
): RecoveryPresentation {
    val open = externalOpen(url, ownPackage, resolve)
    val network = reason == RecoveryReason.Offline || reason == RecoveryReason.Timeout
    val headline = when (reason) {
        RecoveryReason.Offline -> RecoveryHeadline.Offline
        RecoveryReason.Timeout -> RecoveryHeadline.CouldntLoad
        RecoveryReason.Unloadable, RecoveryReason.Other ->
            if (open.sourceKnown) RecoveryHeadline.NotPublic else RecoveryHeadline.CantShow
    }
    val named = open.opensInApp && open.appNameRes != null
    val body = when {
        network -> RecoveryBody.CheckConnection
        named -> RecoveryBody.NamedApp
        else -> RecoveryBody.Browser
    }
    return RecoveryPresentation(
        headline = headline,
        body = body,
        retryPrimary = network,
        opensInApp = named,
        appNameRes = open.appNameRes,
        openUrl = open.url,
        markRes = if (named) open.markRes else null,
    )
}

internal fun resolveExternalPackage(context: Context, url: String, packageName: String): String? {
    val resolved = context.packageManager
        .resolveActivity(viewIntent(url, packageName), PackageManager.MATCH_DEFAULT_ONLY)
        ?.activityInfo
        ?.packageName
        ?: return null
    if (resolved != packageName || resolved == context.packageName || isBrowserPackage(resolved)) return null
    return resolved
}

internal fun sourceAppNameRes(url: String): Int? {
    val host = runCatching { URI(LinkShims.unwrap(url).trim()).host }
        .getOrNull()
        ?.lowercase(Locale.US)
        ?.removePrefix("www.")
        ?: return null
    return sourceNameRes(host)
}

internal fun sourceNameRes(host: String): Int? {
    val normalized = host.lowercase(Locale.US).removePrefix("www.")
    return when {
        normalized == "facebook.com" || normalized.endsWith(".facebook.com") ||
            normalized == "fb.com" || normalized.endsWith(".fb.com") || normalized == "fb.watch" -> R.string.source_facebook
        normalized == "instagram.com" || normalized.endsWith(".instagram.com") || normalized == "instagr.am" ->
            R.string.source_instagram
        normalized == "x.com" || normalized.endsWith(".x.com") ||
            normalized == "twitter.com" || normalized.endsWith(".twitter.com") -> R.string.source_x
        normalized == "reddit.com" || normalized.endsWith(".reddit.com") -> R.string.source_reddit
        normalized == "youtube.com" || normalized.endsWith(".youtube.com") || normalized == "youtu.be" -> R.string.source_youtube
        normalized == "tiktok.com" || normalized.endsWith(".tiktok.com") -> R.string.source_tiktok
        normalized == "threads.net" || normalized.endsWith(".threads.net") -> R.string.source_threads
        else -> null
    }
}

internal fun sourceMarkFor(appNameRes: Int?): Int? = when (appNameRes) {
    R.string.source_facebook -> R.drawable.ic_source_facebook
    R.string.source_instagram -> R.drawable.ic_source_instagram
    R.string.source_x -> R.drawable.ic_source_x
    R.string.source_reddit -> R.drawable.ic_source_reddit
    else -> null
}

internal fun isBrowserPackage(packageName: String): Boolean {
    if (packageName in BROWSER_PACKAGES) return true
    return packageName.contains(".browser") || packageName.endsWith(".chrome")
}

private fun startView(context: Context, url: String, packageName: String): Boolean =
    start(context, viewIntent(url, packageName))

private fun viewIntent(url: String, packageName: String?): Intent =
    Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
        addCategory(Intent.CATEGORY_BROWSABLE)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (packageName != null) setPackage(packageName)
    }

private fun start(context: Context, intent: Intent): Boolean = try {
    context.startActivity(intent)
    true
} catch (_: ActivityNotFoundException) {
    false
} catch (_: SecurityException) {
    false
}

private val BROWSER_PACKAGES = setOf(
    "com.android.chrome",
    "com.chrome.beta",
    "com.chrome.dev",
    "com.google.android.apps.chrome",
    "com.sec.android.app.sbrowser",
    "com.android.browser",
    "com.brave.browser",
    "org.mozilla.firefox",
    "com.microsoft.emmx",
    "com.opera.browser",
    "com.opera.mini.native",
)

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
