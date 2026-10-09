package app.pane.android.ui.home

import android.content.Context

object FirstLaunchPreferences {
    private const val FILE_NAME = "pane_options"
    private const val KEY_DISMISSED = "first_launch_hint_dismissed"

    fun isDismissed(context: Context): Boolean =
        context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_DISMISSED, false)

    fun dismiss(context: Context) {
        context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_DISMISSED, true)
            .apply()
    }
}
