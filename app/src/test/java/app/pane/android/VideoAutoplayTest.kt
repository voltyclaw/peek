package app.pane.android

import app.pane.android.ui.viewer.VideoAutoplay
import app.pane.android.ui.viewer.isDirectPictureUrl
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoAutoplayTest {
    @Test
    fun visualPostsStayOnTheFramedScreen() {
        assertFalse(VideoAutoplay.shouldOpen(alreadyOpened = false, hasVisualMedia = true))
        assertFalse(VideoAutoplay.shouldOpen(alreadyOpened = true, hasVisualMedia = true))
    }

    @Test
    fun aTextPostStaysOnThePreview() {
        assertFalse(VideoAutoplay.shouldOpen(alreadyOpened = false, hasVisualMedia = false))
        assertFalse(VideoAutoplay.shouldOpen(alreadyOpened = true, hasVisualMedia = false))
    }

    @Test
    fun aGalleryPageUrlIsNotAPicture() {
        assertFalse(isDirectPictureUrl("https://www.reddit.com/gallery/1wu8cmd"))
        assertTrue(isDirectPictureUrl("https://preview.redd.it/panel.jpg?width=1080"))
        assertTrue(isDirectPictureUrl("https://scontent.cdninstagram.com/photo.jpg"))
    }
}
