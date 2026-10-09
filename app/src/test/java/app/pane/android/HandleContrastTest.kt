package app.pane.android

import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import org.junit.Assert.assertTrue
import org.junit.Test

/** L1 slate on the M2w night surfaces. Weight 500 is the non-colour cue against ink. */
class HandleContrastTest {
    @Test
    fun slateHandleMeetsBodyContrastOnNightAndRaised() {
        val night = contrast(0x9DB4CC, 0x0E0B0A)
        val raised = contrast(0x9DB4CC, 0x191412)
        val pressed = contrast(0xC3D2E1, blend(0x0E0B0A, 0x9DB4CC, 0.16f))
        val pressedRaised = contrast(0xC3D2E1, blend(0x191412, 0x9DB4CC, 0.16f))
        val handleVsInk = contrast(0x9DB4CC, 0xF4EFEA)

        assertTrue("night $night", night in 9.0..9.4)
        assertTrue("raised $raised", raised in 8.3..8.8)
        assertTrue("pressed $pressed", pressed in 9.5..10.1)
        assertTrue("pressed raised $pressedRaised", pressedRaised in 8.5..9.1)
        assertTrue(night >= 4.5)
        assertTrue(raised >= 4.5)
        assertTrue(pressed >= 4.5)
        assertTrue(pressedRaised >= 4.5)
        assertTrue("handle vs ink $handleVsInk", handleVsInk < 3.0)
    }

    private fun blend(base: Int, over: Int, alpha: Float): Int {
        fun channel(color: Int, shift: Int) = (color shr shift) and 0xFF
        fun mix(shift: Int) = (channel(base, shift) * (1f - alpha) + channel(over, shift) * alpha).toInt()
        return (mix(16) shl 16) or (mix(8) shl 8) or mix(0)
    }

    private fun contrast(foreground: Int, background: Int): Double {
        val lighter = max(luminance(foreground), luminance(background))
        val darker = min(luminance(foreground), luminance(background))
        return (lighter + 0.05) / (darker + 0.05)
    }

    private fun luminance(color: Int): Double {
        fun channel(shift: Int): Double {
            val value = ((color shr shift) and 0xFF) / 255.0
            return if (value <= 0.04045) value / 12.92 else ((value + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
    }
}
