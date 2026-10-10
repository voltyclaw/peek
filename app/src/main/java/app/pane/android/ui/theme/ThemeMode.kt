package app.pane.android.ui.theme

/**
 * Dark is the only theme this build ships. The Light palette and [ThemeMode] values stay
 * so a later build can show the setting again by flipping this flag.
 */
internal const val DARK_THEME_ONLY = true

/** The mode this build actually applies. Light and System become Dark while [DARK_THEME_ONLY]. */
internal fun ThemeMode.forCurrentBuild(): ThemeMode = if (DARK_THEME_ONLY) ThemeMode.Dark else this

enum class ThemeMode {
    Light,
    Dark,
    System,
    ;

    fun resolve(systemDark: Boolean): Boolean = when (this) {
        Light -> false
        Dark -> true
        System -> systemDark
    }

    val storageValue: String get() = name

    companion object {
        fun fromStorage(raw: String?): ThemeMode =
            entries.firstOrNull { it.storageValue == raw } ?: System
    }
}
