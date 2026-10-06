package app.pane.android.data.sample

import android.content.Context

/** Debug-only preference for seeding the hub with offline samples. */
object SampleRecentsPreferences {
    private const val PREFS = "pane_samples"
    private const val SHOW = "show_in_recents"

    fun read(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(SHOW, false)

    fun write(context: Context, show: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(SHOW, show).apply()
    }
}
