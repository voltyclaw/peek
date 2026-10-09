package app.pane.android.ui.media

import app.pane.android.ui.model.VideoSourceUiModel
import kotlin.math.abs
import kotlin.math.min

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
 * Picks a real source URL. The cog stays hidden when every preference plays the same URL.
 *
 * A post can hand back a dozen progressive files (DASH representations, Facebook
 * `playable_url` copies, Instagram `video_versions`) with no dimensions. Those collapse
 * to Auto / High / Medium / Low. When both sides of the frame are known, the menu is
 * Auto plus at most five heights, highest first, labeled from the short side.
 */
object VideoPlaybackQuality {
    private val STANDARD_HEIGHTS = intArrayOf(2160, 1440, 1080, 720, 480, 360, 240)
    private const val MAX_RESOLUTIONS = 5

    fun options(sources: List<VideoSourceUiModel>): List<VideoQualityOption> {
        val resolved = VideoQuality.entries.mapNotNull { quality ->
            pick(sources, quality)?.let { VideoQualityOption(quality, it) }
        }
        if (resolved.map { it.url }.distinct().size < 2) return emptyList()
        return resolved.distinctBy { it.url }
    }

    /** Player menu. Empty when there is nothing to choose. */
    fun renditions(sources: List<VideoSourceUiModel>): List<VideoQualityOption> {
        val prepared = prepare(sources)
        val rungs = ladder(prepared)
        if (rungs.isEmpty()) return emptyList()
        val autoUrl = pickPrepared(prepared, VideoQuality.Auto) ?: return emptyList()
        val rows = ArrayList<VideoQualityOption>(rungs.size + 1)
        rows += VideoQualityOption(VideoQuality.Auto, autoUrl, "Auto", auto = true)
        rungs.forEach { rung ->
            rows += VideoQualityOption(rung.quality, rung.url, rung.label)
        }
        if (rows.map { it.url }.distinct().size < 2) return emptyList()
        if (rows.map { it.label }.distinct().size != rows.size) return rows.distinctBy { it.label }
        return rows
    }

    /**
     * Label for a known short side, in pixels. Standard sizes stay names such as 1080p.
     * A size that is not one of those is left as its own number. Null stays null.
     */
    fun resolutionLabel(pixels: Int?): String? {
        val px = pixels?.takeIf { it > 0 } ?: return null
        val snapped = STANDARD_HEIGHTS.firstOrNull { standard ->
            val slack = (standard * 0.04).toInt().coerceAtLeast(8)
            abs(standard - px) <= slack
        }
        return "${snapped ?: px}p"
    }

    fun urlFor(
        sources: List<VideoSourceUiModel>,
        quality: VideoQuality,
        fallback: String?,
    ): String? = pick(sources, quality) ?: fallback?.takeIf { it.isNotBlank() }

    fun pick(sources: List<VideoSourceUiModel>, quality: VideoQuality): String? =
        pickPrepared(prepare(sources), quality)

    private fun pickPrepared(prepared: Prepared, quality: VideoQuality): String? {
        val rungs = ladder(prepared)
        return when (quality) {
            VideoQuality.Auto -> prepared.adaptiveUrl ?: rungs.getOrNull(rungs.size / 2)?.url
            VideoQuality.High -> rungs.firstOrNull()?.url ?: prepared.adaptiveUrl
            VideoQuality.Medium -> rungs.takeIf { it.size >= 3 }?.let { it[it.size / 2].url }
                ?: rungs.takeIf { it.size == 2 }?.last()?.url
            VideoQuality.Low -> rungs.takeIf { it.size >= 2 }?.last()?.url
        }
    }

    private data class Rung(
        val quality: VideoQuality,
        val url: String,
        val label: String,
        val pixels: Int,
    )

    private data class Prepared(
        val progressive: List<VideoSourceUiModel>,
        val adaptiveUrl: String?,
    )

    private fun prepare(sources: List<VideoSourceUiModel>): Prepared {
        val playable = sources
            .filter { it.url.isNotBlank() && !isAudioOnly(it.url) }
            .distinctBy { it.url.substringBefore('?').substringBefore('#') }
        val progressive = playable.filter { !it.adaptive }.sortedByDescending { rank(it) }
        val adaptiveUrl = playable.firstOrNull { it.adaptive }?.url
        return Prepared(progressive, adaptiveUrl)
    }

    private fun ladder(prepared: Prepared): List<Rung> {
        val progressive = prepared.progressive
        val measured = measuredRungs(progressive)
        val everyFileMeasured = progressive.isNotEmpty() && progressive.all { shortSide(it) != null }
        if (measured.size >= 2 || (everyFileMeasured && measured.isNotEmpty())) return measured
        return relativeRungs(progressive)
    }

    private fun measuredRungs(progressive: List<VideoSourceUiModel>): List<Rung> {
        val byLabel = LinkedHashMap<String, VideoSourceUiModel>()
        progressive.forEach { source ->
            val pixels = shortSide(source) ?: return@forEach
            val label = resolutionLabel(pixels) ?: return@forEach
            val current = byLabel[label]
            if (current == null || rank(source) > rank(current)) byLabel[label] = source
        }
        val sorted = byLabel.entries.sortedByDescending { shortSide(it.value) ?: 0 }
        val spread = spreadResolutions(sorted)
        return spread.map { (label, source) ->
            Rung(
                quality = VideoQuality.High,
                url = source.url,
                label = label,
                pixels = shortSide(source) ?: 0,
            )
        }
    }

    private fun relativeRungs(progressive: List<VideoSourceUiModel>): List<Rung> {
        if (progressive.isEmpty()) return emptyList()
        val chosen = when {
            progressive.size >= 3 -> listOf(
                progressive.first() to ("High" to VideoQuality.High),
                progressive[progressive.size / 2] to ("Medium" to VideoQuality.Medium),
                progressive.last() to ("Low" to VideoQuality.Low),
            )
            progressive.size == 2 -> listOf(
                progressive.first() to ("High" to VideoQuality.High),
                progressive.last() to ("Low" to VideoQuality.Low),
            )
            else -> listOf(progressive.first() to ("High" to VideoQuality.High))
        }
        return chosen.distinctBy { it.first.url }.map { (source, named) ->
            Rung(quality = named.second, url = source.url, label = named.first, pixels = 0)
        }
    }

    /**
     * Keeps the tallest and shortest, then fills toward 1080p / 720p / 480p
     * so a long representation list does not become a second menu of duplicates.
     */
    private fun spreadResolutions(
        sorted: List<Map.Entry<String, VideoSourceUiModel>>,
    ): List<Map.Entry<String, VideoSourceUiModel>> {
        if (sorted.size <= MAX_RESOLUTIONS) return sorted
        val chosen = LinkedHashMap<String, Map.Entry<String, VideoSourceUiModel>>()
        fun add(entry: Map.Entry<String, VideoSourceUiModel>) {
            chosen.putIfAbsent(entry.key, entry)
        }
        add(sorted.first())
        val lowest = sorted.last()
        val middleSlots = MAX_RESOLUTIONS - 2
        STANDARD_HEIGHTS.forEach { standard ->
            if (chosen.size >= middleSlots + 1) return@forEach
            val match = sorted.firstOrNull { entry ->
                entry.key == resolutionLabel(standard) && entry.key != lowest.key
            }
            if (match != null) add(match)
        }
        sorted.forEach { entry ->
            if (chosen.size >= middleSlots + 1) return@forEach
            if (entry.key != lowest.key) add(entry)
        }
        add(lowest)
        return chosen.values.sortedByDescending { shortSide(it.value) ?: 0 }
    }

    /** Portrait 1080×1920 is 1080p. A single side is not enough to know which one is short. */
    private fun shortSide(source: VideoSourceUiModel): Int? {
        val width = source.width?.takeIf { it >= 144 } ?: return null
        val height = source.height?.takeIf { it >= 144 } ?: return null
        if (width > 8_192 || height > 8_192) return null
        return min(width, height)
    }

    private fun isAudioOnly(url: String): Boolean {
        val lower = url.lowercase()
        return ".m4a" in lower || "/audio/" in lower || "audio_only" in lower
    }

    private fun rank(source: VideoSourceUiModel): Long {
        val bitrate = source.bitrate?.takeIf { it > 0 }?.toLong()
        if (bitrate != null) return bitrate
        return (source.width ?: 0).toLong() * (source.height ?: 0).toLong()
    }
}
