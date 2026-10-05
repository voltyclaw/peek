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
    val label: String = "",
    val auto: Boolean = false,
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

    /**
     * Player menu. Real heights become labels such as 1080p. Files with no dimensions
     * stay High, Medium, and Low. A single URL hides the control.
     */
    fun renditions(sources: List<VideoSourceUiModel>): List<VideoQualityOption> {
        val distinct = sources.filter { it.url.isNotBlank() }.distinctBy { it.url }
        if (distinct.map { it.url }.distinct().size < 2) return emptyList()
        val progressive = distinct.filter { !it.adaptive }.sortedByDescending { rank(it) }
        val autoUrl = pick(sources, VideoQuality.Auto) ?: distinct.first().url
        val rows = mutableListOf(VideoQualityOption(VideoQuality.Auto, autoUrl, "Auto", auto = true))
        val labeled = progressive.mapNotNull { source ->
            resolutionLabel(source.height)?.let { label -> source to label }
        }
        val adaptive = distinct.any { it.adaptive }
        when {
            labeled.isNotEmpty() -> {
                val seen = mutableSetOf<String>()
                labeled.sortedByDescending { it.first.height ?: 0 }.forEach { (source, label) ->
                    if (seen.add(label)) {
                        rows += VideoQualityOption(VideoQuality.High, source.url, label)
                    }
                }
                progressive.filter { resolutionLabel(it.height) == null }.forEach { source ->
                    if (rows.none { it.url == source.url }) {
                        rows += VideoQualityOption(VideoQuality.Low, source.url, "Low")
                    }
                }
            }
            progressive.size >= 2 -> {
                progressive.forEachIndexed { index, source ->
                    val (quality, label) = when (index) {
                        0 -> VideoQuality.High to "High"
                        progressive.lastIndex -> VideoQuality.Low to "Low"
                        else -> VideoQuality.Medium to "Medium"
                    }
                    rows += VideoQualityOption(quality, source.url, label)
                }
            }
            progressive.size == 1 && adaptive -> {
                val source = progressive.first()
                val label = resolutionLabel(source.height) ?: "High"
                rows += VideoQualityOption(VideoQuality.High, source.url, label)
            }
        }
        return rows
    }

    /** Exact height from the source. Never rounded up to a taller label. */
    fun resolutionLabel(height: Int?): String? {
        val px = height?.takeIf { it > 0 } ?: return null
        return "${px}p"
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
