package com.mustafashakir.peek

import com.mustafashakir.peek.ui.player.CommentsRaise
import com.mustafashakir.peek.ui.player.commentsRaiseForSwipe
import com.mustafashakir.peek.ui.player.playerShowsControlsAfterPageChange
import com.mustafashakir.peek.ui.player.playerShowsControlsOnOpen
import com.mustafashakir.peek.ui.player.swipeRaisesComments
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerChromeTest {
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
    fun swipeUpShowsThePeekThenExpandsAndStopsWhenOpen() {
        assertEquals(CommentsRaise.ShowPeek, commentsRaiseForSwipe(commentsLowered = true, sheetExpanded = false))
        assertEquals(CommentsRaise.Expand, commentsRaiseForSwipe(commentsLowered = false, sheetExpanded = false))
        assertEquals(CommentsRaise.None, commentsRaiseForSwipe(commentsLowered = false, sheetExpanded = true))
        assertEquals(CommentsRaise.None, commentsRaiseForSwipe(commentsLowered = true, sheetExpanded = true))
    }
}
