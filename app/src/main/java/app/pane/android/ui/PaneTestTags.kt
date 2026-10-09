package app.pane.android.ui

/** Stable tags for Compose UI tests. Option C history filters live here. */
object PaneTestTags {
    const val HistoryFilterAll = "history_filter_all"
    const val HistoryFilterStarred = "history_filter_starred"
    const val HistoryFilterFilters = "history_filter_filters"
    const val HistoryFilterAppChip = "history_filter_app_chip"
    const val HistoryFilterSheet = "history_filter_sheet"
    const val HistoryFilterClear = "history_filter_clear"
    const val HistoryFilterShow = "history_filter_show"

    fun historyFilterTile(appName: String): String = "history_filter_tile_$appName"
}
