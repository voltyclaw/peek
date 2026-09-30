package com.mustafashakir.peek

import com.mustafashakir.peek.ui.viewer.VideoAutoplay
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoAutoplayTest {
    @Test
    fun aVideoPostOpensThePlayerOnce() {
        assertTrue(VideoAutoplay.shouldOpen(alreadyOpened = false, isVideo = true))
        assertFalse(VideoAutoplay.shouldOpen(alreadyOpened = true, isVideo = true))
    }

    @Test
    fun aPhotoPostStaysOnThePreview() {
        assertFalse(VideoAutoplay.shouldOpen(alreadyOpened = false, isVideo = false))
        assertFalse(VideoAutoplay.shouldOpen(alreadyOpened = true, isVideo = false))
    }
}
