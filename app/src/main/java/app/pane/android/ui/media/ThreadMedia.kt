package app.pane.android.ui.media

enum class MediaGrid { None, Single, Pair, Feature, Quad }

object ThreadMedia {
    const val MIN_RATIO = 4f / 5f
    const val MAX_RATIO = 16f / 9f
    const val VISIBLE = 0.5f

    fun aspectCap(width: Int, height: Int): Float {
        if (width <= 0 || height <= 0) return 1f
        return (width.toFloat() / height.toFloat()).coerceIn(MIN_RATIO, MAX_RATIO)
    }

    fun grid(count: Int): MediaGrid = when {
        count <= 0 -> MediaGrid.None
        count == 1 -> MediaGrid.Single
        count == 2 -> MediaGrid.Pair
        count == 3 -> MediaGrid.Feature
        else -> MediaGrid.Quad
    }

    fun shownCount(count: Int): Int = when (grid(count)) {
        MediaGrid.None -> 0
        MediaGrid.Quad -> 4
        else -> count
    }
}

data class VisibleSlice(val id: String, val fraction: Float, val top: Int)

object ThreadVisibility {
    fun winner(slices: List<VisibleSlice>, manualId: String?): String? {
        val eligible = slices.filter { it.fraction >= ThreadMedia.VISIBLE }
        if (manualId != null && eligible.any { it.id == manualId }) return manualId
        return eligible.maxWithOrNull(
            compareBy<VisibleSlice> { it.fraction }.thenBy { -it.top },
        )?.id
    }
}

/** Mute follows the viewer session, not each item. The settings default is the starting value. */
data class SessionMute(val muted: Boolean) {
    fun toggle(): SessionMute = copy(muted = !muted)
}
