package app.pane.android

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.net.Uri

/**
 * Browsers the device can actually open an http(s) link with.
 * Labels come from the installed package. Nothing is added by name.
 */
internal object InstalledBrowsers {
    data class Browser(
        val packageName: String,
        val activityName: String,
        val label: String,
    )

    fun list(context: Context): List<Browser> {
        val manager = context.packageManager
        val best = linkedMapOf<String, ResolveInfo>()
        for (info in manager.queryIntentActivities(probe(), PackageManager.MATCH_ALL)) {
            val packageName = info.activityInfo?.packageName ?: continue
            if (!BrowserTrampoline.acceptsPackage(packageName, context.packageName)) continue
            val current = best[packageName]
            if (current == null || info.priority > current.priority ||
                (info.priority == current.priority && info.preferredOrder > current.preferredOrder)
            ) {
                best[packageName] = info
            }
        }
        val browsers = best.map { (packageName, info) ->
            val label = info.loadLabel(manager).toString().takeIf { it.isNotBlank() } ?: packageName
            Browser(
                packageName = packageName,
                activityName = info.activityInfo.name,
                label = label,
            )
        }
        return BrowserTrampoline.orderBrowsers(browsers, { it.packageName }, { it.label })
    }

    /** Opens [url] in the handoff browser. Never uses a generic VIEW, so Pane does not catch it again. */
    fun open(context: Context, url: String): Boolean {
        val pick = BrowserTrampoline.pickHandoff(
            candidates = list(context).map { browser ->
                BrowserTrampoline.Candidate(browser.packageName, browser.activityName)
            },
            ownPackage = context.packageName,
            preferredPackage = BrowserTrampolinePreferences.readHandoff(context),
            systemPackage = systemPackage(context),
        ) ?: return false
        val explicit = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            component = ComponentName(pick.packageName, pick.activityName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching { context.startActivity(explicit) }.isSuccess
    }

    fun systemPackage(context: Context): String? {
        val resolved = context.packageManager.resolveActivity(probe(), PackageManager.MATCH_DEFAULT_ONLY)
        val packageName = resolved?.activityInfo?.packageName ?: return null
        if (!BrowserTrampoline.acceptsPackage(packageName, context.packageName)) return null
        return packageName
    }

    private fun probe(): Intent =
        Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com")).apply {
            addCategory(Intent.CATEGORY_BROWSABLE)
        }
}
