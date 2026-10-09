package app.pane.android

import app.pane.android.ui.text.MentionKind
import app.pane.android.ui.text.MentionNetwork
import app.pane.android.ui.text.mentionHits
import app.pane.android.ui.text.mentionSpoken
import app.pane.android.ui.text.profileSpoken
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class MentionSpanTest {
    @Test
    fun handlesMentionsUrlsAndSkipsHashtagsEmailsAndTrailingPunctuation() {
        val x = mentionHits("Hey @jack, see #pane and foo@bar.com. Also https://example.com/a.", MentionNetwork.X)
        assertEquals(listOf("jack", "https://example.com/a"), x.map { if (it.kind == MentionKind.Url) it.url else it.token })
        assertEquals("https://x.com/jack", x.first().url)
        assertEquals(MentionKind.XHandle, x.first().kind)
        assertTrue(x.none { it.token.contains("#") || it.token.contains("bar.com") })
        assertEquals(5, x.first().end - x.first().start)

        val reddit = mentionHits("from u/spez in r/technology.", MentionNetwork.Reddit)
        assertEquals("https://www.reddit.com/user/spez", reddit[0].url)
        assertEquals("https://www.reddit.com/r/technology", reddit[1].url)
        assertEquals(MentionKind.RedditSub, reddit[1].kind)
        assertFalseEndsWithPunctuation(reddit[1].token)

        val instagram = mentionHits("photo by @natgeo.", MentionNetwork.Instagram)
        assertEquals("https://www.instagram.com/natgeo/", instagram.single().url)
        assertEquals("natgeo", instagram.single().token)
    }

    @Test
    fun spokenLabelsStayPlain() {
        assertEquals("Open @jack on X", mentionSpoken(MentionKind.XHandle, "jack", opensInApp = true))
        assertEquals("Open @jack in browser", mentionSpoken(MentionKind.XHandle, "jack", opensInApp = false))
        assertEquals("Open u/spez on Reddit", mentionSpoken(MentionKind.RedditUser, "spez", opensInApp = true))
        assertEquals("Open r/technology on Reddit", mentionSpoken(MentionKind.RedditSub, "technology", opensInApp = true))
        assertEquals("Open profile on X", profileSpoken("X"))
        val xml = stringsXml()
        assertTrue(xml.contains("<string name=\"pane_cant_show\">This one won\\'t fit in Pane</string>"))
        assertTrue(xml.contains("<string name=\"open_mention_x\">Open @%1\$s on X</string>"))
        assertTrue(xml.contains("<string name=\"open_mention_reddit_sub\">Open r/%1\$s on Reddit</string>"))
        assertTrue(xml.contains("<string name=\"open_profile_on_source\">Open profile on %1\$s</string>"))
        assertFalse(xml.contains("pane_cant_show\">This one won\\'t fit in Pane.</string>"))
    }

    private fun assertFalseEndsWithPunctuation(token: String) {
        assertTrue(token.isNotEmpty() && token.last().isLetterOrDigit())
    }

    private fun stringsXml(): String {
        val root = File(System.getProperty("user.dir").orEmpty())
        val file = listOf(
            File(root, "src/main/res/values/strings.xml"),
            File(root, "app/src/main/res/values/strings.xml"),
        ).first { it.isFile }
        return file.readText()
    }
}
