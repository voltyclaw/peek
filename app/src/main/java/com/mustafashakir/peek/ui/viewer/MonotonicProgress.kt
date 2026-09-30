package com.mustafashakir.peek.ui.viewer

import com.mustafashakir.peek.domain.model.LoadProgress

/**
 * One load should not walk the bar backward.
 * A fallback resolver that starts again near zero continues through the range still left.
 */
internal class MonotonicProgress {
    private var displayed = 0f
    private var anchorDisplayed = 0f
    private var anchorRaw = 0f
    private var lastRaw = 0f

    fun advance(raw: Float): Float {
        val fraction = raw.coerceIn(0f, 1f)
        if (fraction + RESET_EPSILON < lastRaw) {
            anchorDisplayed = displayed
            anchorRaw = fraction
        }
        val span = (1f - anchorRaw).coerceAtLeast(MIN_SPAN)
        val traveled = ((fraction - anchorRaw) / span).coerceIn(0f, 1f)
        val mapped = anchorDisplayed + (1f - anchorDisplayed) * traveled
        if (mapped > displayed) displayed = mapped
        lastRaw = fraction
        return displayed
    }

    fun apply(progress: LoadProgress): LoadProgress =
        progress.copy(fraction = advance(progress.fraction))

    private companion object {
        const val RESET_EPSILON = 0.02f
        const val MIN_SPAN = 0.001f
    }
}
