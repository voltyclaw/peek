package com.mustafashakir.peek

import com.mustafashakir.peek.ui.player.playerShowsControlsAfterPageChange
import com.mustafashakir.peek.ui.player.playerShowsControlsOnOpen
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
}
