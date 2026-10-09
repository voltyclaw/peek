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
import app.pane.android.data.links.isXHost
import app.pane.android.data.links.profileLink
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
    val profile = profileLink(unwrapped)
    if (profile != null) {
        return profile.packages.map { packageName -> OpenInAppTarget(profile.url, packageName) }
    }
    if (isInstagramUrl(unwrapped)) {
        val https = instagramHttps(unwrapped)
        return listOf(
            OpenInAppTarget(https, "com.instagram.android"),
            OpenInAppTarget(https, null),
        )
    }
    if (isXHost(unwrapped)) {
        val https = xHttps(unwrapped)
        return listOf(
            OpenInAppTarget(https, "com.twitter.android"),
            OpenInAppTarget(https, null),
        )
    }
    return listOf(OpenInAppTarget(unwrapped, null))
}

fun openPostInApp(context: Context, url: String): Boolean = openExternally(context, url)

internal enum class ExternalStart { Started, NotFound, Security }

internal data class ExternalLaunchAttempt(val url: String, val packageName: String)

internal data class ExternalLaunchOutcome(
    val started: Boolean,
    val usedPackage: String?,
    val fellBackToBrowser: Boolean,
    val logAppLabelFallback: Boolean,
)

/** Packaged VIEW attempts, in order. A null package is never launched from here. */
internal fun packagedAttempts(url: String): List<ExternalLaunchAttempt> =
    openInAppTargets(url).mapNotNull { target ->
        val packageName = target.packageName ?: return@mapNotNull null
        ExternalLaunchAttempt(target.uri, packageName)
    }

internal data class OpenAffordance(
    val opensInApp: Boolean,
    val appNameRes: Int?,
    val markRes: Int?,
    val useGlobe: Boolean,
)

/** Installed state comes from package info or a launcher intent, not from resolveActivity. */
internal fun openAffordance(url: String, installed: (String) -> Boolean): OpenAffordance {
    val appNameRes = sourceAppNameRes(url)
    val opensInApp = appNameRes != null && openInAppPackages(url).any(installed)
    return OpenAffordance(
        opensInApp = opensInApp,
        appNameRes = appNameRes,
        markRes = if (opensInApp) sourceMarkFor(appNameRes) else null,
        useGlobe = !opensInApp,
    )
}

/**
 * Try each known source package with setPackage before any browser.
 * ActivityNotFoundException and SecurityException move to the next package.
 */
internal fun performExternalLaunch(
    url: String,
    installed: (String) -> Boolean,
    start: (ExternalLaunchAttempt) -> ExternalStart,
    openBrowser: (String) -> Boolean,
    openChooser: (String) -> Boolean,
    allowChooser: Boolean = profileLink(url) == null,
): ExternalLaunchOutcome {
    val attempts = packagedAttempts(url)
    val openUrl = attempts.firstOrNull()?.url ?: externalOpenUrl(url)
    val labeledInApp = openAffordance(url, installed).opensInApp
    for (attempt in attempts) {
        if (attempt.packageName.isBlank()) continue
        if (start(attempt) == ExternalStart.Started) {
            return ExternalLaunchOutcome(
                started = true,
                usedPackage = attempt.packageName,
                fellBackToBrowser = false,
                logAppLabelFallback = false,
            )
        }
    }
    val openedBrowser = openBrowser(openUrl)
    val started = openedBrowser || (allowChooser && openChooser(openUrl))
    return ExternalLaunchOutcome(
        started = started,
        usedPackage = null,
        fellBackToBrowser = true,
        logAppLabelFallback = labeledInApp,
    )
}

/**
 * One Open path for the unloadable button, the coral tile, and the thread hard-wall tile.
 * A known source package is started directly. The launch is not gated on resolveActivity
 * or getPackageInfo. Only a missing activity falls through to the next package, then the
 * handoff browser, then the chooser. A package-less VIEW is never started.
 */
/**
 * [finishAfter] is the caller's finish decision. The launch itself never starts a
 * package-less VIEW. A profile skips the chooser so Pane cannot catch the handoff.
 * Call [shouldFinishAfterExternalOpen] before leaving the current screen.
 */
fun openExternally(context: Context, url: String, finishAfter: Boolean = true): Boolean {
    if (url.isBlank()) return false
    val outcome = performExternalLaunch(
        url = url,
        installed = { packageName -> packageInstalled(context, packageName) },
        start = { attempt -> startPackaged(context, attempt) },
        openBrowser = { openUrl ->
            val browser = handoffBrowserIntent(context, openUrl)
            browser != null && start(context, browser)
        },
        openChooser = { openUrl ->
            val chooser = Intent.createChooser(viewIntent(openUrl, null), null)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            start(context, chooser)
        },
    )
    if (outcome.logAppLabelFallback) {
        Log.w("PaneOpen", "label said app but launch fell back to browser url=$url")
    }
    if (BuildConfig.DEBUG) {
        Log.i(
            "PaneOpen",
            "open incoming=$url package=${outcome.usedPackage} fallback=${outcome.fellBackToBrowser} finish=$finishAfter",
        )
    }
    return outcome.started
}

/** Share-in and the Open button finish. A mention or hub profile tap does not. */
internal fun shouldFinishAfterExternalOpen(started: Boolean, finishAfter: Boolean): Boolean =
    started && finishAfter

/** Best-effort install check once package visibility includes the source app. */
internal fun packageInstalled(context: Context, packageName: String): Boolean {
    val manager = context.packageManager
    if (runCatching { manager.getPackageInfo(packageName, 0) }.getOrNull() != null) return true
    return manager.getLaunchIntentForPackage(packageName) != null
}

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

internal fun recoveryPresentation(
    url: String,
    reason: RecoveryReason = RecoveryReason.Unloadable,
    installed: (String) -> Boolean = { false },
): RecoveryPresentation {
    val affordance = openAffordance(url, installed)
    val network = reason == RecoveryReason.Offline || reason == RecoveryReason.Timeout
    val headline = when (reason) {
        RecoveryReason.Offline -> RecoveryHeadline.Offline
        RecoveryReason.Timeout -> RecoveryHeadline.CouldntLoad
        RecoveryReason.Unloadable, RecoveryReason.Other ->
            if (affordance.appNameRes != null) RecoveryHeadline.NotPublic else RecoveryHeadline.CantShow
    }
    val body = when {
        network -> RecoveryBody.CheckConnection
        affordance.opensInApp -> RecoveryBody.NamedApp
        else -> RecoveryBody.Browser
    }
    return RecoveryPresentation(
        headline = headline,
        body = body,
        retryPrimary = network,
        opensInApp = affordance.opensInApp,
        appNameRes = affordance.appNameRes,
        openUrl = externalOpenUrl(url),
        markRes = affordance.markRes,
    )
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

private fun startPackaged(context: Context, attempt: ExternalLaunchAttempt): ExternalStart = try {
    context.startActivity(viewIntent(attempt.url, attempt.packageName))
    ExternalStart.Started
} catch (_: ActivityNotFoundException) {
    ExternalStart.NotFound
} catch (_: SecurityException) {
    ExternalStart.Security
}

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

private fun xHttps(url: String): String {
    val uri = runCatching { URI(url) }.getOrNull() ?: return url
    val path = uri.rawPath?.takeIf { it.isNotBlank() } ?: "/"
    val query = uri.rawQuery?.let { "?$it" }.orEmpty()
    return "https://x.com$path$query"
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
