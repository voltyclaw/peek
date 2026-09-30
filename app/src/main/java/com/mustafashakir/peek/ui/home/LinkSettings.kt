package com.mustafashakir.peek.ui.home

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings

/**
 * Opens this app's system "Open by default" screen. Android does not let an app
 * claim Instagram or Reddit links silently. The same screen is where the user
 * turns those domains on, or clears them to restore another app.
 */
object LinkSettings {
    fun action(sdkInt: Int): String =
        if (sdkInt >= Build.VERSION_CODES.S) {
            Settings.ACTION_APP_OPEN_BY_DEFAULT_SETTINGS
        } else {
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS
        }

    fun open(context: Context) {
        val packageUri = Uri.parse("package:${context.packageName}")
        val primary = Intent(action(Build.VERSION.SDK_INT), packageUri)
        val fallback = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri)
        runCatching { context.startActivity(primary) }
            .onFailure { context.startActivity(fallback) }
    }
}
