package app.pane.android

import app.pane.android.ui.player.mediaFillsPortrait
import app.pane.android.ui.player.swipeRevealsPost
import app.pane.android.ui.viewer.VideoAutoplay
import app.pane.android.ui.viewer.isDirectPictureUrl
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoAutoplayTest {
    private val phoneWidth = 1080f
    private val phoneHeight = 2400f

    @Test
    fun tallMediaOpensImmersiveUntilTheReaderComesBack() {
        assertTrue(
            VideoAutoplay.shouldOpen(
                alreadyOpened = false,
                contentWidthPx = 1080f,
                contentHeightPx = 1920f,
                viewportWidthPx = phoneWidth,
                viewportHeightPx = phoneHeight,
            ),
        )
        assertFalse(
            VideoAutoplay.shouldOpen(
                alreadyOpened = true,
                contentWidthPx = 1080f,
                contentHeightPx = 1920f,
                viewportWidthPx = phoneWidth,
                viewportHeightPx = phoneHeight,
            ),
        )
        assertTrue(mediaFillsPortrait(phoneWidth, phoneHeight, 1080f, 1920f))
    }

    @Test
    fun wideMediaStaysOnTheFramedScreen() {
        assertFalse(
            VideoAutoplay.shouldOpen(
                alreadyOpened = false,
                contentWidthPx = 1920f,
                contentHeightPx = 1080f,
                viewportWidthPx = phoneWidth,
                viewportHeightPx = phoneHeight,
            ),
        )
        assertFalse(mediaFillsPortrait(phoneWidth, phoneHeight, 1920f, 1080f))
    }

    @Test
    fun unknownSizeStaysFramed() {
        assertFalse(
            VideoAutoplay.shouldOpen(
                alreadyOpened = false,
                contentWidthPx = 0f,
                contentHeightPx = 0f,
                viewportWidthPx = phoneWidth,
                viewportHeightPx = phoneHeight,
            ),
        )
    }

    @Test
    fun swipeDownLeavesImmersiveAndASidewaysMoveDoesNot() {
        assertTrue(swipeRevealsPost(totalDx = 4f, totalDy = 80f, thresholdPx = 56f))
        assertFalse(swipeRevealsPost(totalDx = 90f, totalDy = 40f, thresholdPx = 56f))
        assertFalse(swipeRevealsPost(totalDx = 0f, totalDy = 20f, thresholdPx = 56f))
        assertFalse(swipeRevealsPost(totalDx = 0f, totalDy = -80f, thresholdPx = 56f))
    }

    @Test
    fun aGalleryPageUrlIsNotAPicture() {
        assertFalse(isDirectPictureUrl("https://www.reddit.com/gallery/1wu8cmd"))
        assertTrue(isDirectPictureUrl("https://preview.redd.it/panel.jpg?width=1080"))
        assertTrue(isDirectPictureUrl("https://scontent.cdninstagram.com/photo.jpg"))
    }
}
