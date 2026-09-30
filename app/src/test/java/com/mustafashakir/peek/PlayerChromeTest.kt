package com.mustafashakir.peek

import com.mustafashakir.peek.ui.player.playerShowsControlsOnOpen
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerChromeTest {
    @Test
    fun videoOpensWithTheCommentsSheetLowered() {
        assertFalse(playerShowsControlsOnOpen("https://packaged-media.redd.it/clip.mp4"))
    }

    @Test
    fun aPhotoOpensWithCommentsVisible() {
        assertTrue(playerShowsControlsOnOpen(null))
    }
}
