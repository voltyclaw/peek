package app.pane.android

import app.pane.android.domain.usecase.ExtractUrlFromTextUseCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExtractUrlFromTextUseCaseTest {
    private val extractUrl = ExtractUrlFromTextUseCase()

    @Test
    fun extractsUrlFromSharedTextAndRemovesSentencePunctuation() {
        assertEquals(
            "https://www.instagram.com/reel/DapVyootsZw/",
            extractUrl("Watch this: https://www.instagram.com/reel/DapVyootsZw/)."),
        )
        assertEquals(
            "https://www.reddit.com/r/pics/comments/abc123/title/",
            extractUrl("Look: https://www.reddit.com/r/pics/comments/abc123/title/."),
        )
    }

    @Test
    fun extractsAnHrefWithoutSwallowingTheMarkup() {
        assertEquals(
            "https://www.facebook.com/share/p/AbCdEf/",
            extractUrl("""<a href="https://www.facebook.com/share/p/AbCdEf/">post</a>"""),
        )
    }

    @Test
    fun acceptsHttpAndHttpsUrlsWithHosts() {
        assertEquals("http://example.com/path", extractUrl("http://example.com/path"))
        assertEquals("https://example.com", extractUrl("https://example.com"))
    }

    @Test
    fun countsEveryWebUrlAndKeepsTheFirst() {
        val text = "https://instagram.com/p/one\nhttps://reddit.com/r/pics/comments/abc/"
        assertEquals(2, extractUrl.count(text))
        assertEquals("https://instagram.com/p/one", extractUrl(text))
    }

    @Test
    fun rejectsMissingInvalidAndNonWebUrls() {
        assertNull(extractUrl(null))
        assertNull(extractUrl("just some text"))
        assertNull(extractUrl("mailto:hello@example.com"))
        assertNull(extractUrl("https://"))
    }
}
