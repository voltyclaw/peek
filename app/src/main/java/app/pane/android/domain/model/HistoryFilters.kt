package app.pane.android.domain.model

/**
 * Option C app tiles. Order is fixed: disabled apps stay in place.
 * Threads is not a tile. A post from anywhere else lands on [SourceApp.Other].
 */
object HistoryFilterCatalog {
    val order: List<SourceApp> = listOf(
        SourceApp.X,
        SourceApp.Reddit,
        SourceApp.Facebook,
        SourceApp.Instagram,
        SourceApp.YouTube,
        SourceApp.TikTok,
        SourceApp.Bluesky,
        SourceApp.Other,
    )

    /** At most one app. A legacy multi-app set keeps the first tile in [order]. */
    fun selectedApp(apps: Set<SourceApp>): SourceApp? = order.firstOrNull { it in apps }

    fun counts(rows: List<HistoryEntry>, scope: HistoryScope): List<HistoryAppCount> {
        val pool = if (scope == HistoryScope.Starred) rows.filter { it.starredAt != null } else rows
        val tallies = pool.groupingBy { it.sourceApp }.eachCount()
        return order.map { app -> HistoryAppCount(app, tallies[app] ?: 0) }
    }

    fun scopeCount(rows: List<HistoryEntry>, scope: HistoryScope): Int =
        if (scope == HistoryScope.Starred) rows.count { it.starredAt != null } else rows.size
}

data class HistoryAppCount(
    val app: SourceApp,
    val count: Int,
)
