package app.pane.android.ui.home

import android.content.Context
import app.pane.android.domain.model.SourceApp
import app.pane.android.domain.source.SourceSwitches

/** Show-in-Pane flags. Missing keys are ON. Consent files are a different preference file. */
class SourceSwitchStore(context: Context) {
    private val preferences = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun migrateOnce() {
        if (preferences.getBoolean(MIGRATED, false)) return
        val editor = preferences.edit()
        SourceSwitches.legacyKeys.forEach { editor.remove(it) }
        SourceSwitches.rows.forEach { app -> editor.putBoolean(SourceSwitches.key(app), true) }
        editor.putBoolean(MIGRATED, true)
        editor.apply()
    }

    fun shown(app: SourceApp): Boolean =
        preferences.getBoolean(SourceSwitches.key(app), true)

    fun set(app: SourceApp, on: Boolean) {
        preferences.edit().putBoolean(SourceSwitches.key(app), on).apply()
    }

    fun snapshot(): Map<SourceApp, Boolean> = SourceSwitches.rows.associateWith { shown(it) }

    private companion object {
        const val FILE = "pane_sources"
        const val MIGRATED = "migrated_1_2_0"
    }
}
