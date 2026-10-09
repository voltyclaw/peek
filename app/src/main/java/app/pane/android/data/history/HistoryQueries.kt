package app.pane.android.data.history

import app.pane.android.domain.model.HistoryEntry
import app.pane.android.domain.model.HistoryQuery
import app.pane.android.domain.model.HistoryScope
import app.pane.android.domain.model.SourceApp

/**
 * All/Starred AND the app set. Tag and date stay P3: add their clauses in [where] beside these.
 * An empty app set does not constrain the app.
 */
internal object HistoryQueries {
    fun filter(rows: List<HistoryEntry>, query: HistoryQuery): List<HistoryEntry> =
        rows.filter { matches(it, query) }

    fun matches(row: HistoryEntry, query: HistoryQuery): Boolean {
        if (query.scope == HistoryScope.Starred && row.starredAt == null) return false
        if (query.apps.isNotEmpty() && row.sourceApp !in query.apps) return false
        return true
    }

    fun distinctApps(rows: List<HistoryEntry>): List<SourceApp> {
        val present = rows.map { it.sourceApp }.toSet()
        return SourceApp.entries.filter { it in present }
    }

    /** SQL for the same filter. The composite index is (source_app, starred_at, last_viewed_at). */
    fun where(query: HistoryQuery): String {
        val clauses = mutableListOf<String>()
        if (query.scope == HistoryScope.Starred) clauses += "starred_at IS NOT NULL"
        if (query.apps.isNotEmpty()) {
            val list = query.apps.joinToString(",") { "'${it.name}'" }
            clauses += "source_app IN ($list)"
        }
        // P3: clauses += tag join, clauses += date window on last_viewed_at
        return clauses.joinToString(" AND ")
    }
}
