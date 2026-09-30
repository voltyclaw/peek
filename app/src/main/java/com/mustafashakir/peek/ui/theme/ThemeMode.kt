package com.mustafashakir.peek.ui.theme

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
