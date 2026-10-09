package app.pane.android.data.history

import android.content.Context
import app.pane.android.domain.model.HistoryQuery
import app.pane.android.domain.model.HistoryScope
import app.pane.android.domain.model.SourceApp

/** Keys for History prefs. The swipe picker is a P3 stub: the key and default exist, and nothing reads a custom value yet. */
internal object HistoryPreferenceKeys {
    const val FILE = "pane_history"
    const val SCOPE = "history_scope"
    const val APPS = "history_apps"
    const val SWIPE_ACTION = "history_swipe_action"
    const val DEFAULT_SCOPE = "all"
    const val DEFAULT_APPS = ""
    const val DEFAULT_SWIPE = "unstar_then_remove"
}

internal object HistoryPreferenceCodec {
    fun encode(query: HistoryQuery): Pair<String, String> {
        val scope = if (query.scope == HistoryScope.Starred) "starred" else HistoryPreferenceKeys.DEFAULT_SCOPE
        val apps = query.apps.map { it.name }.sorted().joinToString(",")
        return scope to apps
    }

    fun decode(scope: String?, apps: String?): HistoryQuery = HistoryQuery(
        scope = if (scope == "starred") HistoryScope.Starred else HistoryScope.All,
        apps = apps.orEmpty()
            .split(',')
            .mapNotNull { token -> SourceApp.entries.firstOrNull { it.name == token } }
            .toSet(),
    )

    fun swipeAction(stored: String?): String =
        stored?.takeIf { it.isNotBlank() } ?: HistoryPreferenceKeys.DEFAULT_SWIPE
}

internal class MemoryHistoryPreferences {
    private val values = mutableMapOf<String, String>()

    fun readFilter(): HistoryQuery = HistoryPreferenceCodec.decode(
        values[HistoryPreferenceKeys.SCOPE],
        values[HistoryPreferenceKeys.APPS],
    )

    fun writeFilter(query: HistoryQuery) {
        val (scope, apps) = HistoryPreferenceCodec.encode(query)
        values[HistoryPreferenceKeys.SCOPE] = scope
        values[HistoryPreferenceKeys.APPS] = apps
    }

    fun readSwipeAction(): String = HistoryPreferenceCodec.swipeAction(values[HistoryPreferenceKeys.SWIPE_ACTION])
}

internal class AndroidHistoryPreferences(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(
        HistoryPreferenceKeys.FILE,
        Context.MODE_PRIVATE,
    )

    fun readFilter(): HistoryQuery = HistoryPreferenceCodec.decode(
        prefs.getString(HistoryPreferenceKeys.SCOPE, HistoryPreferenceKeys.DEFAULT_SCOPE),
        prefs.getString(HistoryPreferenceKeys.APPS, HistoryPreferenceKeys.DEFAULT_APPS),
    )

    fun writeFilter(query: HistoryQuery) {
        val (scope, apps) = HistoryPreferenceCodec.encode(query)
        prefs.edit()
            .putString(HistoryPreferenceKeys.SCOPE, scope)
            .putString(HistoryPreferenceKeys.APPS, apps)
            .apply()
    }

    fun readSwipeAction(): String =
        HistoryPreferenceCodec.swipeAction(prefs.getString(HistoryPreferenceKeys.SWIPE_ACTION, null))
}
