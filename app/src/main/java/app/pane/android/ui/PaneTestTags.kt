package app.pane.android.ui

/** Stable tags for Compose UI tests and Maestro. Resource ids via testTagsAsResourceId. */
object PaneTestTags {
    const val HistoryDoor = "history_door"
    const val HistoryFilterAll = "history_filter_all"
    const val HistoryFilterStarred = "history_filter_starred"
    const val HistoryFilterFilters = "history_filter_filters"
    const val HistoryFilterAppChip = "history_filter_app_chip"
    const val HistoryFilterSheet = "history_filter_sheet"
    const val HistoryFilterClear = "history_filter_clear"
    const val HistoryFilterShow = "history_filter_show"
    const val ViewerDone = "viewer_done"
    const val ViewerMedia = "viewer_media"
    const val ViewerFullscreen = "viewer_fullscreen"
    const val ViewerPlayerSurface = "viewer_player_surface"
    const val ViewerRotate = "viewer_rotate"
    const val ViewerExitFullscreen = "viewer_exit_fullscreen"

    fun historyFilterTile(appName: String): String = "history_filter_tile_$appName"

    fun historyRow(index: Int): String = "history_row_$index"
}
