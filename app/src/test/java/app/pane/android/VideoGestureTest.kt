package app.pane.android

import android.content.pm.ActivityInfo
import app.pane.android.ui.media.CONTROLS_AUTO_HIDE_MS
import app.pane.android.ui.media.FullscreenOrientationRequest
import app.pane.android.ui.media.ManualOrientationLock
import app.pane.android.ui.media.RotateControlLabel
import app.pane.android.ui.media.SurfaceGesture
import app.pane.android.ui.media.SurfaceGestureAction
import app.pane.android.ui.media.VideoSurfaceKind
import app.pane.android.ui.media.activityOrientation
import app.pane.android.ui.media.controlsAutoHide
import app.pane.android.ui.media.fullscreenEnterRequest
import app.pane.android.ui.media.nextManualLock
import app.pane.android.ui.media.orientationAfterExit
import app.pane.android.ui.media.orientationRequest
import app.pane.android.ui.media.rotateControlLabel
import app.pane.android.ui.media.surfaceGestureAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
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
    fun enteringFullscreenFollowsTheUserAndDoesNotForceLandscape() {
        assertEquals(FullscreenOrientationRequest.FullUser, fullscreenEnterRequest())
        assertEquals(FullscreenOrientationRequest.FullUser, orientationRequest(ManualOrientationLock.None))
        assertEquals(
            ActivityInfo.SCREEN_ORIENTATION_FULL_USER,
            activityOrientation(FullscreenOrientationRequest.FullUser),
        )
        assertEquals(
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,
            activityOrientation(FullscreenOrientationRequest.Portrait),
        )
        assertEquals(
            ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE,
            activityOrientation(FullscreenOrientationRequest.UserLandscape),
        )
        val requested = FullscreenOrientationRequest.entries.map(::activityOrientation)
        assertFalse(requested.contains(ActivityInfo.SCREEN_ORIENTATION_SENSOR))
        assertFalse(requested.contains(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE))
        assertNotEquals(ActivityInfo.SCREEN_ORIENTATION_SENSOR, activityOrientation(fullscreenEnterRequest()))
    }

    @Test
    fun rotateButtonLocksTheOtherSideUntilExit() {
        assertEquals(
            ManualOrientationLock.UserLandscape,
            nextManualLock(ManualOrientationLock.None, deviceLandscape = false),
        )
        assertEquals(
            ManualOrientationLock.Portrait,
            nextManualLock(ManualOrientationLock.None, deviceLandscape = true),
        )
        assertEquals(
            ManualOrientationLock.Portrait,
            nextManualLock(ManualOrientationLock.UserLandscape, deviceLandscape = true),
        )
        assertEquals(
            ManualOrientationLock.UserLandscape,
            nextManualLock(ManualOrientationLock.Portrait, deviceLandscape = false),
        )
        assertEquals(
            RotateControlLabel.ToLandscape,
            rotateControlLabel(ManualOrientationLock.None, deviceLandscape = false),
        )
        assertEquals(
            RotateControlLabel.ToPortrait,
            rotateControlLabel(ManualOrientationLock.None, deviceLandscape = true),
        )
        assertEquals(
            RotateControlLabel.ToPortrait,
            rotateControlLabel(ManualOrientationLock.UserLandscape, deviceLandscape = true),
        )
        val saved = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        assertEquals(saved, orientationAfterExit(saved))
        assertNotEquals(ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE, orientationAfterExit(saved))
    }
}
