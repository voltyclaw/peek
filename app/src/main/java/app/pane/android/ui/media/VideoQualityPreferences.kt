package app.pane.android.ui.media

import android.content.Context

object VideoQualityPreferences {
    private const val FILE_NAME = "pane_options"
    private const val KEY_VIDEO_QUALITY = "video_quality"

    fun read(context: Context): VideoQuality {
        val stored = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
            .getString(KEY_VIDEO_QUALITY, null)
        return VideoQuality.fromStorage(stored)
    }

    fun write(context: Context, quality: VideoQuality) {
        context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_VIDEO_QUALITY, quality.storageValue)
            .apply()
    }
}
