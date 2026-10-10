package app.pane.android.ui.theme

import android.content.Context

object ThemePreferences {
    private const val FILE_NAME = "pane_options"
    private const val KEY_THEME_MODE = "theme_mode"

    fun read(context: Context): ThemeMode {
        val stored = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
            .getString(KEY_THEME_MODE, null)
        val parsed = ThemeMode.fromStorage(stored)
        val applied = parsed.forCurrentBuild()
        if (applied != parsed) write(context, applied)
        return applied
    }

    fun write(context: Context, mode: ThemeMode) {
        context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_THEME_MODE, mode.storageValue)
            .apply()
    }
}
