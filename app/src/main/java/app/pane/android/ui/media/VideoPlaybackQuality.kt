package app.pane.android.ui.media

import app.pane.android.ui.model.VideoSourceUiModel

/** Saved streaming preference. Auto follows an adaptive stream, or a middle file. */
enum class VideoQuality(val storageValue: String) {
    Auto("auto"),
    High("high"),
    Medium("medium"),
    Low("low"),
    ;

    companion object {
        fun fromStorage(value: String?): VideoQuality =
            entries.firstOrNull { it.storageValue == value } ?: Auto
    }
}

data class VideoQualityOption(
    val quality: VideoQuality,
    val url: String,
)

/**
 * Picks a real source URL. Empty [options] means every preference plays the same URL,
 * so the quality control stays hidden.
 */
object VideoPlaybackQuality {
    fun options(sources: List<VideoSourceUiModel>): List<VideoQualityOption> {
        val resolved = VideoQuality.entries.mapNotNull { quality ->
            pick(sources, quality)?.let { VideoQualityOption(quality, it) }
        }
        if (resolved.map { it.url }.distinct().size < 2) return emptyList()
        return resolved.distinctBy { it.url }
    }

    fun urlFor(
        sources: List<VideoSourceUiModel>,
        quality: VideoQuality,
        fallback: String?,
    ): String? = pick(sources, quality) ?: fallback?.takeIf { it.isNotBlank() }

    fun pick(sources: List<VideoSourceUiModel>, quality: VideoQuality): String? {
        val distinct = sources.filter { it.url.isNotBlank() }.distinctBy { it.url }
        val progressive = distinct.filter { !it.adaptive }.sortedByDescending { rank(it) }
        val adaptive = distinct.firstOrNull { it.adaptive }?.url
        return when (quality) {
            VideoQuality.Auto -> adaptive ?: progressive.getOrNull(progressive.size / 2)?.url
            VideoQuality.High -> progressive.firstOrNull()?.url ?: adaptive
            VideoQuality.Medium -> progressive.takeIf { it.size >= 3 }?.let { it[it.size / 2].url }
            VideoQuality.Low -> progressive.takeIf { it.size >= 2 }?.last()?.url
        }
    }

    private fun rank(source: VideoSourceUiModel): Long {
        val bitrate = source.bitrate?.takeIf { it > 0 }?.toLong()
        if (bitrate != null) return bitrate
        return (source.width ?: 0).toLong() * (source.height ?: 0).toLong()
    }
}
