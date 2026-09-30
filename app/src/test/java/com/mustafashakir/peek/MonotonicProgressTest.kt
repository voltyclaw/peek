package com.mustafashakir.peek

import com.mustafashakir.peek.ui.viewer.MonotonicProgress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MonotonicProgressTest {
    @Test
    fun aSingleSourceMovesForward() {
        val progress = MonotonicProgress()
        assertEquals(0.05f, progress.advance(0.05f), 0.001f)
        assertEquals(0.35f, progress.advance(0.35f), 0.001f)
        assertEquals(1f, progress.advance(1f), 0.001f)
    }

    @Test
    fun aFallbackThatRestartsDoesNotJumpBackward() {
        val progress = MonotonicProgress()
        progress.advance(0.05f)
        progress.advance(0.35f)
        val restarted = progress.advance(0.02f)
        val midway = progress.advance(0.5f)
        val done = progress.advance(1f)

        assertTrue(restarted >= 0.35f)
        assertTrue(midway > restarted)
        assertEquals(1f, done, 0.001f)
        assertTrue(midway < done)
    }
}
