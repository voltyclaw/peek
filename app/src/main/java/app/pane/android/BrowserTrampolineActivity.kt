package app.pane.android

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import app.pane.android.ui.actions.openExternally

/**
 * Catch-all http(s) entry. Disabled until Settings turns the browser trampoline on.
 * Posts Pane already opens, and other Meta links, go to [MainActivity].
 * Every other link is started on a specific other browser component so the VIEW
 * cannot come back into Pane.
 */
class BrowserTrampolineActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent?.getBooleanExtra(EXTRA_HANDED_OFF, false) == true) {
            finish()
            return
        }
        when (val decision = BrowserTrampoline.decide(intent?.dataString, BrowserTrampolinePreferences.read(this))) {
            BrowserTrampoline.Decision.Ignore -> Unit
            is BrowserTrampoline.Decision.OpenInPane -> openInPane(decision.url)
            is BrowserTrampoline.Decision.OpenInSource -> {
                if (!openExternally(this, decision.url, finishAfter = true)) {
                    Toast.makeText(this, R.string.browser_trampoline_no_browser, Toast.LENGTH_SHORT).show()
                }
            }
            is BrowserTrampoline.Decision.HandOff -> {
                if (!handOff(decision.url)) {
                    Toast.makeText(this, R.string.browser_trampoline_no_browser, Toast.LENGTH_SHORT).show()
                }
            }
        }
        finish()
    }

    private fun openInPane(url: String) {
        val forward = Intent(this, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = Uri.parse(url)
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP,
            )
        }
        runCatching { startActivity(forward) }
    }

    private fun handOff(url: String): Boolean {
        val parsed = Uri.parse(url)
        val probe = Intent(Intent.ACTION_VIEW, parsed).apply {
            addCategory(Intent.CATEGORY_BROWSABLE)
        }
        val resolved = packageManager.queryIntentActivities(probe, PackageManager.MATCH_ALL)
            .sortedWith(compareByDescending<ResolveInfo> { it.priority }.thenByDescending { it.preferredOrder })
        val pick = BrowserTrampoline.pickHandoff(
            candidates = resolved.map { info ->
                BrowserTrampoline.Candidate(
                    packageName = info.activityInfo.packageName,
                    activityName = info.activityInfo.name,
                )
            },
            ownPackage = packageName,
            preferredPackage = BrowserTrampolinePreferences.readHandoff(this),
            systemPackage = InstalledBrowsers.systemPackage(this),
        ) ?: return false
        val explicit = Intent(Intent.ACTION_VIEW, parsed).apply {
            component = ComponentName(pick.packageName, pick.activityName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra(EXTRA_HANDED_OFF, true)
        }
        return runCatching { startActivity(explicit) }.isSuccess
    }

    companion object {
        const val EXTRA_HANDED_OFF = "app.pane.android.extra.HANDED_OFF"
    }
}
