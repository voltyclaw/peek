package app.pane.android.data.history

import android.content.Context
import app.pane.android.domain.model.HistoryFilterCatalog
import app.pane.android.domain.model.HistoryQuery
import app.pane.android.domain.model.HistoryScope
import app.pane.android.domain.model.SourceApp

/** Keys for History prefs. The swipe picker is a P3 stub: the key and default exist, and nothing reads a custom value yet. */
internal object HistoryPreferenceKeys {
    const val FILE = "pane_history"
    const val SCOPE = "history_scope"
    /** Legacy multi-app set. 1.4.0 reads it once, then drops it. */
    const val APPS = "history_apps"
    /** Single Option C app. Empty means All. */
    const val APP = "history_app"
    const val MIGRATED = "history_apps_migrated"
    const val SWIPE_ACTION = "history_swipe_action"
    const val DEFAULT_SCOPE = "all"
    const val DEFAULT_APPS = ""
    const val DEFAULT_SWIPE = "unstar_then_remove"
}

internal object HistoryPreferenceCodec {
    fun encode(query: HistoryQuery): Pair<String, String> {
        val scope = if (query.scope == HistoryScope.Starred) "starred" else HistoryPreferenceKeys.DEFAULT_SCOPE
        val app = HistoryFilterCatalog.selectedApp(query.apps)?.name.orEmpty()
        return scope to app
    }

    /** Restores one app from [HistoryPreferenceKeys.APP]. An empty value is All. */
    fun decode(scope: String?, app: String?): HistoryQuery = HistoryQuery(
        scope = if (scope == "starred") HistoryScope.Starred else HistoryScope.All,
        apps = app.orEmpty()
            .takeIf { it.isNotBlank() && !it.contains(',') }
            ?.let { token -> SourceApp.entries.firstOrNull { it.name == token } }
            ?.let { setOf(it) }
            ?: emptySet(),
    )

    /**
     * The old `history_apps` value can name several apps. 1.4.0 drops that set.
     * Starred scope is kept.
     */
    fun migrateLegacy(scope: String?): HistoryQuery = HistoryQuery(
        scope = if (scope == "starred") HistoryScope.Starred else HistoryScope.All,
        apps = emptySet(),
    )

    fun swipeAction(stored: String?): String =
        stored?.takeIf { it.isNotBlank() } ?: HistoryPreferenceKeys.DEFAULT_SWIPE
}

internal class MemoryHistoryPreferences {
    private val values = mutableMapOf<String, String>()
    private var migrated = false

    /** Plants the pre-1.4.0 multi-app pref. The next [readFilter] drops the app set. */
    fun seedLegacy(scope: String, apps: String) {
        values[HistoryPreferenceKeys.SCOPE] = scope
        values[HistoryPreferenceKeys.APPS] = apps
        values.remove(HistoryPreferenceKeys.APP)
        migrated = false
    }

    fun readFilter(): HistoryQuery {
        if (!migrated) {
            val query = HistoryPreferenceCodec.migrateLegacy(values[HistoryPreferenceKeys.SCOPE])
            writeFilter(query)
            return query
        }
        return HistoryPreferenceCodec.decode(
            values[HistoryPreferenceKeys.SCOPE],
            values[HistoryPreferenceKeys.APP],
        )
    }

    fun writeFilter(query: HistoryQuery) {
        val (scope, app) = HistoryPreferenceCodec.encode(query)
        values[HistoryPreferenceKeys.SCOPE] = scope
        values[HistoryPreferenceKeys.APP] = app
        values.remove(HistoryPreferenceKeys.APPS)
        migrated = true
    }

    fun readSwipeAction(): String = HistoryPreferenceCodec.swipeAction(values[HistoryPreferenceKeys.SWIPE_ACTION])
}

internal class AndroidHistoryPreferences(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(
        HistoryPreferenceKeys.FILE,
        Context.MODE_PRIVATE,
    )

    fun readFilter(): HistoryQuery {
        if (!prefs.getBoolean(HistoryPreferenceKeys.MIGRATED, false)) {
            val query = HistoryPreferenceCodec.migrateLegacy(
                prefs.getString(HistoryPreferenceKeys.SCOPE, HistoryPreferenceKeys.DEFAULT_SCOPE),
            )
            writeFilter(query)
            return query
        }
        return HistoryPreferenceCodec.decode(
            prefs.getString(HistoryPreferenceKeys.SCOPE, HistoryPreferenceKeys.DEFAULT_SCOPE),
            prefs.getString(HistoryPreferenceKeys.APP, HistoryPreferenceKeys.DEFAULT_APPS),
        )
    }

    fun writeFilter(query: HistoryQuery) {
        val (scope, app) = HistoryPreferenceCodec.encode(query)
        prefs.edit()
            .putString(HistoryPreferenceKeys.SCOPE, scope)
            .putString(HistoryPreferenceKeys.APP, app)
            .remove(HistoryPreferenceKeys.APPS)
            .putBoolean(HistoryPreferenceKeys.MIGRATED, true)
            .apply()
    }

    fun readSwipeAction(): String =
        HistoryPreferenceCodec.swipeAction(prefs.getString(HistoryPreferenceKeys.SWIPE_ACTION, null))
}
