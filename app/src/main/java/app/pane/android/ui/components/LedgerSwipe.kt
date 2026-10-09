package app.pane.android.ui.components

import kotlin.math.abs

/**
 * A swipe commits on release. The row must travel about 45% of its width,
 * or a fling in the same direction must already be past about 25%.
 */
object LedgerSwipe {
    const val COMMIT_FRACTION = 0.45f
    const val FLING_FRACTION = 0.25f
    const val FLING_DP_PER_SEC = 800f

    fun commits(offsetPx: Float, widthPx: Float, velocityPxPerSec: Float, density: Float): Boolean {
        if (widthPx <= 0f || density <= 0f) return false
        val fraction = abs(offsetPx) / widthPx
        if (fraction >= COMMIT_FRACTION) return true
        val velocityDp = abs(velocityPxPerSec) / density
        val sameDirection = offsetPx * velocityPxPerSec > 0f
        return sameDirection && velocityDp >= FLING_DP_PER_SEC && fraction >= FLING_FRACTION
    }
}
