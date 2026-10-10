package app.pane.android.ui.components

import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.unit.LayoutDirection
import app.pane.android.domain.model.HistoryGesture
import kotlin.math.abs

/**
 * Which physical edge a Remove swipe travels toward.
 * [MIRRORED] keeps LTR as a physical-left Remove and, in RTL, treats a swipe
 * toward the end edge as Remove. Flip this one value if Lead picks physical.
 */
enum class SwipeRemoveEdge { PHYSICAL, MIRRORED }

/**
 * A swipe commits on release. The row must travel about 45% of its width,
 * or a fling in the same direction must already be past about 25%.
 */
object LedgerSwipe {
    const val COMMIT_FRACTION = 0.45f
    const val FLING_FRACTION = 0.25f
    const val FLING_DP_PER_SEC = 800f

    /** Q8. Default is mirrored: toward the end edge removes. LTR stays left = Remove. */
    val REMOVE_EDGE: SwipeRemoveEdge = SwipeRemoveEdge.MIRRORED

    /** True when a physical-left offset is the Remove direction in [direction]. */
    fun removeIsPhysicalLeft(direction: LayoutDirection): Boolean = when (REMOVE_EDGE) {
        SwipeRemoveEdge.PHYSICAL -> true
        SwipeRemoveEdge.MIRRORED -> direction == LayoutDirection.Ltr
    }

    /**
     * True when [offsetPx] (negative = physical left) is the Remove direction.
     * A resting row is not a Remove swipe.
     */
    fun isRemoveDirection(offsetPx: Float, direction: LayoutDirection): Boolean {
        if (offsetPx == 0f) return false
        val physicalLeft = offsetPx < 0f
        return if (removeIsPhysicalLeft(direction)) physicalLeft else !physicalLeft
    }

    fun commits(offsetPx: Float, widthPx: Float, velocityPxPerSec: Float, density: Float): Boolean {
        if (widthPx <= 0f || density <= 0f) return false
        val fraction = abs(offsetPx) / widthPx
        if (fraction >= COMMIT_FRACTION) return true
        val velocityDp = abs(velocityPxPerSec) / density
        val sameDirection = offsetPx * velocityPxPerSec > 0f
        return sameDirection && velocityDp >= FLING_DP_PER_SEC && fraction >= FLING_FRACTION
    }
}

val LedgerSwipeFraction = SemanticsPropertyKey<Float>("LedgerSwipeFraction")
val LedgerSwipePast = SemanticsPropertyKey<Boolean>("LedgerSwipePast")
val LedgerSwipeKind = SemanticsPropertyKey<HistoryGesture>("LedgerSwipeKind")
