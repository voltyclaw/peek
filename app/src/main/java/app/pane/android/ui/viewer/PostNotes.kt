package app.pane.android.ui.viewer

import android.content.Context

/** A private note for one post, stored on this device and keyed by its URL. */
object PostNotes {
    private const val FILE_NAME = "pane_notes"

    fun read(context: Context, url: String): String =
        context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
            .getString(key(url), "")
            .orEmpty()

    fun write(context: Context, url: String, note: String) {
        context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(key(url), note.trim())
            .apply()
    }

    private fun key(url: String): String = url.trim()
}
