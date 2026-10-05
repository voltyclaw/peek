package app.pane.android

import android.app.role.RoleManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings

internal object BrowserTrampolinePreferences {
    private const val FILE_NAME = "pane_options"
    private const val KEY_BROWSER_TRAMPOLINE = "browser_trampoline"

    fun read(context: Context): Boolean =
        context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_BROWSER_TRAMPOLINE, false)

    fun write(context: Context, enabled: Boolean) {
        context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_BROWSER_TRAMPOLINE, enabled)
            .apply()
        apply(context, enabled)
    }

    fun applyStored(context: Context) {
        apply(context, read(context))
    }

    /**
     * Asks Android to offer Pane as the default browser. The switch alone does not.
     * If Pane already holds the browser role, this does nothing.
     */
    fun requestDefaultBrowser(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roles = context.getSystemService(RoleManager::class.java)
            if (roles != null && roles.isRoleAvailable(RoleManager.ROLE_BROWSER)) {
                if (roles.isRoleHeld(RoleManager.ROLE_BROWSER)) return
                val request = roles.createRequestRoleIntent(RoleManager.ROLE_BROWSER)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (runCatching { context.startActivity(request) }.isSuccess) return
            }
        }
        val settings = Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(settings) }
    }

    private fun apply(context: Context, enabled: Boolean) {
        val state = if (enabled) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        } else {
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        }
        context.packageManager.setComponentEnabledSetting(
            ComponentName(context, BrowserTrampolineActivity::class.java),
            state,
            PackageManager.DONT_KILL_APP,
        )
    }
}
