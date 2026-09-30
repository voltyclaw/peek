package com.mustafashakir.peek.ui.navigation

import android.content.Context

/**
 * Where Back goes after a link was opened from another app.
 * Stored beside the theme choice. A later brand or applicationId change does not need to move this key.
 */
enum class BackBehavior {
    ClosePeek,
    GoHome,
    ;

    val storageValue: String get() = name

    companion object {
        fun fromStorage(raw: String?): BackBehavior =
            entries.firstOrNull { it.storageValue == raw } ?: ClosePeek
    }
}

object BackPreferences {
    private const val FILE_NAME = "peek_options"
    private const val KEY_BACK_BEHAVIOR = "back_behavior"

    fun read(context: Context): BackBehavior {
        val stored = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
            .getString(KEY_BACK_BEHAVIOR, null)
        return BackBehavior.fromStorage(stored)
    }

    fun write(context: Context, behavior: BackBehavior) {
        context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_BACK_BEHAVIOR, behavior.storageValue)
            .apply()
    }
}
