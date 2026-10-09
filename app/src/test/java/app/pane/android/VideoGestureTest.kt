package app.pane.android

import app.pane.android.ui.media.CONTROLS_AUTO_HIDE_MS
import app.pane.android.ui.media.FullscreenOrientationLock
import app.pane.android.ui.media.SurfaceGesture
import app.pane.android.ui.media.SurfaceGestureAction
import app.pane.android.ui.media.VideoSurfaceKind
import app.pane.android.ui.media.controlsAutoHide
import app.pane.android.ui.media.fullscreenOrientation
import app.pane.android.ui.media.surfaceGestureAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoGestureTest {
    @Test
    fun singleTapTogglesControlsAndDoesNotPlayOrPause() {
        assertEquals(
            SurfaceGestureAction.ToggleControls,
            surfaceGestureAction(VideoSurfaceKind.Inline, SurfaceGesture.SingleTap),
        )
        assertEquals(
            SurfaceGestureAction.ToggleControls,
            surfaceGestureAction(VideoSurfaceKind.Fullscreen, SurfaceGesture.SingleTap),
        )
        val actions = SurfaceGesture.entries.map { surfaceGestureAction(VideoSurfaceKind.Inline, it) }
        assertFalse(actions.any { it !is SurfaceGestureAction.ToggleControls && it !is SurfaceGestureAction.EnterFullscreen })
    }

    @Test
    fun doubleTapEntersInlineAndExitsFullscreen() {
        assertEquals(
            SurfaceGestureAction.EnterFullscreen,
            surfaceGestureAction(VideoSurfaceKind.Inline, SurfaceGesture.DoubleTap),
        )
        assertEquals(
            SurfaceGestureAction.ExitFullscreen,
            surfaceGestureAction(VideoSurfaceKind.Fullscreen, SurfaceGesture.DoubleTap),
        )
    }

    @Test
    fun controlsStayUpWhilePausedScrubbingOrTalkBack() {
        assertEquals(3_000L, CONTROLS_AUTO_HIDE_MS)
        assertTrue(controlsAutoHide(playing = true, scrubbing = false, touchExplorationEnabled = false))
        assertFalse(controlsAutoHide(playing = false, scrubbing = false, touchExplorationEnabled = false))
        assertFalse(controlsAutoHide(playing = true, scrubbing = true, touchExplorationEnabled = false))
        assertFalse(controlsAutoHide(playing = true, scrubbing = false, touchExplorationEnabled = true))
        assertFalse(controlsAutoHide(playing = false, scrubbing = true, touchExplorationEnabled = true))
    }

    @Test
    fun landscapeVideoUsesSensorLandscapeAndPortraitStaysPortrait() {
        assertEquals(FullscreenOrientationLock.SensorLandscape, fullscreenOrientation(1920f, 1080f))
        assertEquals(FullscreenOrientationLock.Portrait, fullscreenOrientation(1080f, 1920f))
        assertEquals(FullscreenOrientationLock.Portrait, fullscreenOrientation(1080f, 1080f))
        assertEquals(FullscreenOrientationLock.Portrait, fullscreenOrientation(0f, 0f))
    }
}
