package app.pane.android.ui.navigation

import android.content.Context

/**
 * How a video starts. Muted is the default. Remember last keeps the most recent mute toggle.
 */
enum class SoundMode {
    Muted,
    On,
    RememberLast,
    ;

    val storageValue: String get() = name

    companion object {
        fun fromStorage(raw: String?): SoundMode =
            entries.firstOrNull { it.storageValue == raw } ?: Muted
    }
}

object SoundPreferences {
    private const val FILE_NAME = "pane_options"
    private const val KEY_SOUND_MODE = "sound_mode"
    private const val KEY_LAST_MUTED = "sound_last_muted"

    fun read(context: Context): SoundMode {
        val stored = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
            .getString(KEY_SOUND_MODE, null)
        return SoundMode.fromStorage(stored)
    }

    fun write(context: Context, mode: SoundMode) {
        context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_SOUND_MODE, mode.storageValue)
            .apply()
    }

    fun startMuted(context: Context): Boolean = when (read(context)) {
        SoundMode.Muted -> true
        SoundMode.On -> false
        SoundMode.RememberLast -> context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_LAST_MUTED, true)
    }

    fun rememberMuted(context: Context, muted: Boolean) {
        context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_LAST_MUTED, muted)
            .apply()
    }
}
