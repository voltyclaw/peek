package app.pane.android.ui.media

import android.content.Context
import android.content.pm.ActivityInfo
import android.os.SystemClock
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.accessibility.AccessibilityManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown

/** How long visible controls stay up while playback is moving. */
internal const val CONTROLS_AUTO_HIDE_MS = 3_000L

internal enum class VideoSurfaceKind { Inline, Fullscreen }

internal enum class SurfaceGesture { SingleTap, DoubleTap }

internal sealed class SurfaceGestureAction {
    /** Show or hide the controls. Never play or pause. */
    data object ToggleControls : SurfaceGestureAction()
    data object EnterFullscreen : SurfaceGestureAction()
    data object ExitFullscreen : SurfaceGestureAction()
}

/** Confirmed single-tap toggles controls. Double-tap enters inline and exits fullscreen. */
internal fun surfaceGestureAction(
    surface: VideoSurfaceKind,
    gesture: SurfaceGesture,
): SurfaceGestureAction = when (gesture) {
    SurfaceGesture.SingleTap -> SurfaceGestureAction.ToggleControls
    SurfaceGesture.DoubleTap -> when (surface) {
        VideoSurfaceKind.Inline -> SurfaceGestureAction.EnterFullscreen
        VideoSurfaceKind.Fullscreen -> SurfaceGestureAction.ExitFullscreen
    }
}

/**
 * Auto-hide only while the video is playing.
 * Paused, scrubbing, and touch exploration (TalkBack) keep the controls up.
 */
internal fun controlsAutoHide(
    playing: Boolean,
    scrubbing: Boolean,
    touchExplorationEnabled: Boolean,
): Boolean = playing && !scrubbing && !touchExplorationEnabled

/** What fullscreen asks the activity for. Never sensor or sensor-landscape. */
internal enum class FullscreenOrientationRequest { FullUser, Portrait, UserLandscape }

/** A tap on the rotate control. None follows the user sensor only while fullscreen is open. */
internal enum class ManualOrientationLock { None, Portrait, UserLandscape }

internal enum class RotateControlLabel { ToLandscape, ToPortrait }

/**
 * Entering fullscreen keeps the orientation the phone is already in.
 * [FullscreenOrientationRequest.FullUser] turns only when the phone turns and system auto-rotate is on.
 */
internal fun fullscreenEnterRequest(): FullscreenOrientationRequest = FullscreenOrientationRequest.FullUser

internal fun orientationRequest(lock: ManualOrientationLock): FullscreenOrientationRequest = when (lock) {
    ManualOrientationLock.None -> FullscreenOrientationRequest.FullUser
    ManualOrientationLock.Portrait -> FullscreenOrientationRequest.Portrait
    ManualOrientationLock.UserLandscape -> FullscreenOrientationRequest.UserLandscape
}

/** First tap locks to the other side of the current configuration. Later taps swap portrait and landscape. */
internal fun nextManualLock(current: ManualOrientationLock, deviceLandscape: Boolean): ManualOrientationLock =
    when (current) {
        ManualOrientationLock.None ->
            if (deviceLandscape) ManualOrientationLock.Portrait else ManualOrientationLock.UserLandscape
        ManualOrientationLock.Portrait -> ManualOrientationLock.UserLandscape
        ManualOrientationLock.UserLandscape -> ManualOrientationLock.Portrait
    }

internal fun rotateControlLabel(current: ManualOrientationLock, deviceLandscape: Boolean): RotateControlLabel =
    when (nextManualLock(current, deviceLandscape)) {
        ManualOrientationLock.Portrait -> RotateControlLabel.ToPortrait
        ManualOrientationLock.UserLandscape, ManualOrientationLock.None -> RotateControlLabel.ToLandscape
    }

internal fun activityOrientation(request: FullscreenOrientationRequest): Int = when (request) {
    FullscreenOrientationRequest.FullUser -> ActivityInfo.SCREEN_ORIENTATION_FULL_USER
    FullscreenOrientationRequest.Portrait -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    FullscreenOrientationRequest.UserLandscape -> ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE
}

/** Leaving fullscreen drops a manual lock and puts back the request saved on the way in. */
internal fun orientationAfterExit(savedRequest: Int): Int = savedRequest

@Composable
internal fun rememberTouchExplorationEnabled(): Boolean {
    val context = LocalContext.current
    val manager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
    var enabled by remember {
        mutableStateOf(manager.isEnabled && manager.isTouchExplorationEnabled)
    }
    DisposableEffect(manager) {
        val listener = AccessibilityManager.TouchExplorationStateChangeListener { exploring ->
            enabled = manager.isEnabled && exploring
        }
        manager.addTouchExplorationStateChangeListener(listener)
        onDispose { manager.removeTouchExplorationStateChangeListener(listener) }
    }
    return enabled
}

/**
 * [GestureDetector.onSingleTapConfirmed] so the first half of a double-tap does not toggle controls.
 * A drag past touch slop is cancelled and left for the parent scroll or pager.
 */
internal fun Modifier.confirmedMediaTaps(
    onSingleTapConfirmed: () -> Unit,
    onDoubleTap: () -> Unit,
): Modifier = composed {
    val context = LocalContext.current
    val single = rememberUpdatedState(onSingleTapConfirmed)
    val doubleTap = rememberUpdatedState(onDoubleTap)
    pointerInput(Unit) {
        val detector = GestureDetector(
            context,
            object : GestureDetector.SimpleOnGestureListener() {
                override fun onDown(e: MotionEvent): Boolean = true
                override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                    single.value()
                    return true
                }
                override fun onDoubleTap(e: MotionEvent): Boolean {
                    doubleTap.value()
                    return true
                }
            },
        )
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val pointerId = down.id
            val downTime = SystemClock.uptimeMillis()
            dispatchTouch(detector, motion(MotionEvent.ACTION_DOWN, down.position.x, down.position.y, downTime))
            val origin = down.position
            val slop = viewConfiguration.touchSlop
            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == pointerId } ?: break
                val dx = change.position.x - origin.x
                val dy = change.position.y - origin.y
                if (dx * dx + dy * dy > slop * slop) {
                    dispatchTouch(detector, motion(MotionEvent.ACTION_CANCEL, change.position.x, change.position.y, downTime))
                    break
                }
                if (!change.pressed) {
                    dispatchTouch(detector, motion(MotionEvent.ACTION_UP, change.position.x, change.position.y, downTime))
                    break
                }
            }
        }
    }
}

private fun motion(action: Int, x: Float, y: Float, downTime: Long): MotionEvent =
    MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, x, y, 0)

private fun dispatchTouch(detector: GestureDetector, event: MotionEvent) {
    try {
        detector.onTouchEvent(event)
    } finally {
        event.recycle()
    }
}
