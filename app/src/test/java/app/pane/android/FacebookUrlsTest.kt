package app.pane.android

import app.pane.android.data.facebook.FacebookDocument
import app.pane.android.data.facebook.FacebookUrls
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FacebookUrlsTest {
    @Test
    fun recognizesCommonPublicPostShapes() {
        assertEquals(
            "pfbid0123",
            FacebookUrls.parse("https://www.facebook.com/nasa/posts/pfbid0123?ref=share")?.id,
        )
        assertEquals(
            "https://www.facebook.com/share/p/AbCdEf",
            FacebookUrls.parse("https://m.facebook.com/share/p/AbCdEf/")?.canonicalUrl,
        )
        assertEquals(FacebookUrls.Kind.Reel, FacebookUrls.parse("https://www.facebook.com/reel/1234567890")?.kind)
        assertEquals(FacebookUrls.Kind.Reel, FacebookUrls.parse("https://www.facebook.com/share/r/ReelId1")?.kind)
        assertEquals("9988", FacebookUrls.parse("https://www.facebook.com/watch/?v=9988")?.id)
        assertEquals("abc123", FacebookUrls.parse("https://fb.watch/abc123")?.id)
        assertEquals("555", FacebookUrls.parse("https://www.facebook.com/permalink.php?story_fbid=555&id=9")?.id)
        assertEquals("777", FacebookUrls.parse("https://facebook.com/photo.php?fbid=777")?.id)
        assertEquals("42", FacebookUrls.parse("https://www.facebook.com/groups/99/permalink/42/")?.id)
        assertEquals(
            "https://www.facebook.com/page/videos/321",
            FacebookUrls.parse("https://mbasic.facebook.com/page/videos/321")?.canonicalUrl,
        )
        assertTrue(FacebookUrls.supports("http://fb.com/share/v/ClipId9"))
    }

    @Test
    fun profilesFeedsAndLoginAreNotPosts() {
        assertNull(FacebookUrls.parse("https://www.facebook.com/nasa"))
        assertNull(FacebookUrls.parse("https://www.facebook.com/"))
        assertNull(FacebookUrls.parse("https://www.facebook.com/marketplace"))
        assertNull(FacebookUrls.parse("https://www.facebook.com/login.php"))
        assertNull(FacebookUrls.parse("https://www.facebook.com/stories/123456789"))
        assertNull(FacebookUrls.parse("https://www.facebook.com/watch/"))
        assertNull(FacebookUrls.parse("https://example.com/share/p/abc"))
    }
}
