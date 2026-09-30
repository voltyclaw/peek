package com.mustafashakir.peek

import com.mustafashakir.peek.data.x.XUrls
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class XUrlsTest {
    @Test
    fun recognizesPublicStatusLinks() {
        assertEquals(
            "20",
            XUrls.parse("https://twitter.com/jack/status/20")?.id,
        )
        assertEquals(
            "https://x.com/i/status/1050118621198921728",
            XUrls.parse("https://x.com/someuser/status/1050118621198921728?s=20")?.canonicalUrl,
        )
        assertEquals(
            "1770000000000000000",
            XUrls.parse("https://mobile.twitter.com/i/web/status/1770000000000000000")?.id,
        )
        assertTrue(XUrls.supports("https://www.x.com/i/status/99/photo/1"))
        assertEquals("42", XUrls.parse("http://mobile.x.com/a/status/42")?.id)
    }

    @Test
    fun profilesAndHomeAreNotPosts() {
        assertNull(XUrls.parse("https://x.com/nasa"))
        assertNull(XUrls.parse("https://x.com/explore"))
        assertNull(XUrls.parse("https://twitter.com/search?q=hello"))
        assertNull(XUrls.parse("https://x.com/i/status/"))
        assertNull(XUrls.parse("https://example.com/user/status/20"))
    }
}
