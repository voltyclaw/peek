package app.pane.android

import app.pane.android.data.facebook.FacebookUrls
import app.pane.android.domain.model.LinkShims
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IncomingLinkTest {
    @Test
    fun sharePlainTextOpensThePostInsideTheCaption() {
        assertEquals(
            "https://www.instagram.com/reel/DapVyootsZw/",
            IncomingLink.urlFrom(
                action = "android.intent.action.SEND",
                dataString = null,
                extraText = "Watch this https://www.instagram.com/reel/DapVyootsZw/.",
                extraHtml = null,
            ),
        )
    }

    @Test
    fun shareHtmlUsesTheHrefWhenThereIsNoPlainText() {
        assertEquals(
            "https://www.facebook.com/share/p/AbCdEf/",
            IncomingLink.urlFrom(
                action = "android.intent.action.SEND",
                dataString = null,
                extraText = null,
                extraHtml = """<a href="https://www.facebook.com/share/p/AbCdEf/">post</a>""",
            ),
        )
    }

    @Test
    fun viewOpensTheAddressAndUnwrapsMetaRedirectHosts() {
        val wrapped = "https://l.facebook.com/l.php?u=" +
            "https%3A%2F%2Fwww.facebook.com%2Fshare%2Fp%2FAbCdEf%2F&h=AT0"
        val url = IncomingLink.urlFrom(
            action = "android.intent.action.VIEW",
            dataString = wrapped,
            extraText = null,
            extraHtml = null,
        )
        assertEquals("https://www.facebook.com/share/p/AbCdEf/", url)
        assertEquals("AbCdEf", FacebookUrls.parse(url!!)?.id)
    }

    @Test
    fun mobileFacebookAndInstagramShimsUnwrapTheSameWay() {
        assertEquals(
            "https://www.instagram.com/reel/DapVyootsZw/",
            LinkShims.unwrap(
                "https://lm.facebook.com/l.php?u=https%3A%2F%2Fl.instagram.com%2F%3Fu%3Dhttps%253A%252F%252Fwww.instagram.com%252Freel%252FDapVyootsZw%252F",
            ),
        )
        assertEquals(
            "https://www.instagram.com/p/abc123/",
            LinkShims.unwrap("https://www.instagram.com/p/abc123/"),
        )
    }

    @Test
    fun shareWithoutALinkStaysOnHome() {
        assertNull(
            IncomingLink.urlFrom(
                action = "android.intent.action.SEND",
                dataString = null,
                extraText = "no address here",
                extraHtml = null,
            ),
        )
        assertNull(IncomingLink.urlFrom(action = null, dataString = null, extraText = null, extraHtml = null))
    }
}
