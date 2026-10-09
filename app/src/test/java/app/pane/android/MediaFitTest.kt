package app.pane.android

import app.pane.android.ui.media.VIDEO_FALLBACK_ASPECT
import app.pane.android.ui.media.contentAspectRatio
import app.pane.android.ui.media.displayVideoSize
import app.pane.android.ui.media.fittedContentPx
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MediaFitTest {
    @Test
    fun unknownSizeUsesTheFallbackAspect() {
        assertEquals(VIDEO_FALLBACK_ASPECT, contentAspectRatio(0f, 0f, VIDEO_FALLBACK_ASPECT), 0.001f)
        assertEquals(16f / 9f, contentAspectRatio(1920f, 1080f, 1f), 0.001f)
    }

    @Test
    fun landscapeFitsTheWidthAndPortraitFitsTheHeight() {
        val landscape = fittedContentPx(1080f, 2400f, 1920f, 1080f)
        assertEquals(1080f, landscape.widthPx, 0.01f)
        assertEquals(607.5f, landscape.heightPx, 0.01f)

        val portrait = fittedContentPx(1080f, 1920f, 1080f, 1920f)
        assertEquals(1080f, portrait.widthPx, 0.01f)
        assertEquals(1920f, portrait.heightPx, 0.01f)
    }

    @Test
    fun pixelAspectWidensAnamorphicFrames() {
        val widened = displayVideoSize(1440, 1080, 4f / 3f)
        assertEquals(1920f, widened?.first ?: 0f, 0.5f)
        assertEquals(1080f, widened?.second ?: 0f, 0.5f)
        assertNull(displayVideoSize(0, 1080, 1f))
    }
}
