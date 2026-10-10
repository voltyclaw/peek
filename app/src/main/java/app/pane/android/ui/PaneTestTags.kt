package app.pane.android.ui

import app.pane.android.domain.model.SourceApp

/** Stable tags for Compose UI tests. History rows, swipes, and Option C filters live here. */
object PaneTestTags {
    fun row(url: String) = "ledger_row:$url"
    fun rowBody(url: String) = "ledger_body:$url"

    const val SWIPE_BG = "ledger_swipe_bg"
    const val SWIPE_ICON = "ledger_swipe_icon"
    const val VIEWER_STAR = "viewer_star"
    const val VIEWER_CAPSULE = "viewer_star_refresh"
    const val VIEWER_REFRESH = "viewer_refresh"
    const val PLAYER_ENTER_FULLSCREEN = "player_enter_fullscreen"
    const val PLAYER_EXIT_FULLSCREEN = "player_exit_fullscreen"
    const val PLAYER_ROTATE = "player_rotate"
    const val SNACKBAR = "pane_snackbar"
    const val RECENTS = "hub_recents"
    const val HISTORY_LIST = "history_list"
    const val CHIP_ALL = "history_chip_all"
    const val CHIP_STARRED = "history_chip_starred"
    const val CHIP_FILTERS = "history_chip_filters"
    const val CHIP_APP = "history_chip_app"
    const val CHIP_APP_CLEAR = "history_chip_app_clear"
    const val FILTER_HAIRLINE = "history_filter_hairline"
    const val FILTER_SHEET = "history_filter_sheet"
    const val SHEET_CLEAR = "history_sheet_clear"
    const val SHEET_SHOW = "history_sheet_show"
    const val RESULTS_ANNOUNCER = "history_results_live"

    fun appTile(app: SourceApp) = "history_app_tile:${app.name}"

    /** Older Option C names. They point at the same nodes as the chip tags. */
    const val HistoryFilterAll = CHIP_ALL
    const val HistoryFilterStarred = CHIP_STARRED
    const val HistoryFilterFilters = CHIP_FILTERS
    const val HistoryFilterAppChip = CHIP_APP
    const val HistoryFilterSheet = FILTER_SHEET
    const val HistoryFilterClear = SHEET_CLEAR
    const val HistoryFilterShow = SHEET_SHOW

    fun historyFilterTile(appName: String): String = "history_app_tile:$appName"
}
