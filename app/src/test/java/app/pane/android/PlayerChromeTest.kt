package app.pane.android

import app.pane.android.ui.player.CommentsRaise
import app.pane.android.ui.player.commentsRaiseForSwipe
import app.pane.android.ui.player.formatPlaybackClock
import app.pane.android.ui.player.playbackFraction
import app.pane.android.ui.player.playerShowsControlsAfterPageChange
import app.pane.android.ui.player.playerShowsControlsOnOpen
import app.pane.android.ui.player.seekPositionMs
import app.pane.android.ui.player.spareBelowPeekPx
import app.pane.android.ui.player.swipeRaisesComments
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerChromeTest {
    @Test
    fun scrubberDragJumpsToThatPointInTheVideo() {
        assertEquals(0L, seekPositionMs(durationMs = 0L, fraction = 0.5f))
        assertEquals(5_000L, seekPositionMs(durationMs = 10_000L, fraction = 0.5f))
        assertEquals(10_000L, seekPositionMs(durationMs = 10_000L, fraction = 2f))
        assertEquals(0L, seekPositionMs(durationMs = 10_000L, fraction = -1f))
        assertEquals(0.25f, playbackFraction(positionMs = 2_500L, durationMs = 10_000L), 0.001f)
        assertEquals(0f, playbackFraction(positionMs = 2_500L, durationMs = 0L), 0.001f)
        assertEquals("1:05", formatPlaybackClock(65_000L))
        assertEquals("1:02:03", formatPlaybackClock(3_723_000L))
    }

    @Test
    fun mediaOpensWithTheCommentsSheetLowered() {
        assertFalse(playerShowsControlsOnOpen())
    }

    @Test
    fun swipingOntoAVideoShowsControlsAndAnImageStaysOnThePicture() {
        assertTrue(playerShowsControlsAfterPageChange("https://packaged-media.redd.it/clip.mp4"))
        assertFalse(playerShowsControlsAfterPageChange(null))
    }

    @Test
    fun swipeUpRaisesCommentsAndASidewaysMoveDoesNot() {
        assertTrue(swipeRaisesComments(totalDx = 4f, totalDy = -80f, thresholdPx = 56f))
        assertFalse(swipeRaisesComments(totalDx = 90f, totalDy = -40f, thresholdPx = 56f))
        assertFalse(swipeRaisesComments(totalDx = 0f, totalDy = -20f, thresholdPx = 56f))
        assertFalse(swipeRaisesComments(totalDx = 0f, totalDy = -80f, thresholdPx = 0f))
        assertFalse(swipeRaisesComments(totalDx = 0f, totalDy = 80f, thresholdPx = 56f))
    }

    @Test
    fun wideMediaLeavesTheSpareHeightForTheCaptionAndTallMediaStaysFullBleed() {
        val phoneWidth = 1080f
        val phoneHeight = 2400f
        val landscape = spareBelowPeekPx(phoneWidth, phoneHeight, contentWidthPx = 1920f, contentHeightPx = 1080f)
        val portrait = spareBelowPeekPx(phoneWidth, phoneHeight, contentWidthPx = 1080f, contentHeightPx = 1920f)
        val unknown = spareBelowPeekPx(phoneWidth, phoneHeight, contentWidthPx = 0f, contentHeightPx = 0f)

        assertTrue(landscape > phoneHeight * 0.5f)
        assertEquals(0f, portrait, 0.01f)
        assertEquals(0f, unknown, 0.01f)
    }

    @Test
    fun swipeUpShowsThePeekThenExpandsAndStopsWhenOpen() {
        assertEquals(CommentsRaise.ShowPeek, commentsRaiseForSwipe(commentsLowered = true, sheetExpanded = false))
        assertEquals(CommentsRaise.Expand, commentsRaiseForSwipe(commentsLowered = false, sheetExpanded = false))
        assertEquals(CommentsRaise.None, commentsRaiseForSwipe(commentsLowered = false, sheetExpanded = true))
        assertEquals(CommentsRaise.None, commentsRaiseForSwipe(commentsLowered = true, sheetExpanded = true))
    }
}
