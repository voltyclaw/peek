package app.pane.android

import app.pane.android.data.x.XRichText
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class XRichTextTest {
    @Test
    fun anArticleReplacesTheBareShortLinkWithACard() {
        val rich = XRichText.present(Json.parseToJsonElement(ARTICLE).jsonObject)
        assertFalse(rich.text.contains("t.co"))
        assertEquals("the market pays billions for nothing. \$nil is priced like nothing", rich.article?.title)
        assertTrue(rich.article?.preview?.startsWith("disclosure up top") == true)
        assertEquals("https://pbs.twimg.com/media/HUHQMe5acAAnYSy.png", rich.article?.coverUrl)
        assertTrue(rich.article?.body?.contains("full article body") == true)
        assertEquals("https://x.com/i/article/2108194886424088577", rich.article?.url)
        assertTrue(rich.text.isBlank() || !rich.text.contains("t.co"))
    }

    @Test
    fun aQuoteDropsItsTrailingShortLinkAndKeepsTheQuotedPost() {
        val rich = XRichText.present(Json.parseToJsonElement(QUOTE).jsonObject)
        assertFalse(rich.text.contains("t.co"))
        assertTrue(rich.text.contains("worth reading"))
        assertEquals("Ada", rich.quote?.authorName)
        assertEquals("ada", rich.quote?.handle)
        assertEquals("the quoted line", rich.quote?.text)
        assertEquals("https://x.com/i/status/99", rich.quote?.url)
    }

    @Test
    fun aLinkCardExpandsTheShortLink() {
        val rich = XRichText.present(Json.parseToJsonElement(LINK).jsonObject)
        assertFalse(rich.text.contains("t.co"))
        assertEquals("example.com/story", rich.links.single().label)
        assertEquals("https://example.com/story", rich.links.single().url)
    }

    @Test
    fun repliesExpandShortLinksFromNearbyEntities() {
        val text = XRichText.expandShortLinks(
            "see https://t.co/AbCdEf12",
            """{"url":"https://t.co/AbCdEf12","expanded_url":"https://example.com/story","display_url":"example.com/story"}""",
        )
        assertEquals("see example.com/story", text)
        assertFalse(text.contains("t.co"))
    }

    private companion object {
        val ARTICLE = """
            {
              "text": "https://t.co/ZZ89hgP5SU",
              "user": {"name": "Nik", "screen_name": "nikshepsvn"},
              "article": {
                "id": "2108194886424088577",
                "title": "the market pays billions for nothing. ${'$'}nil is priced like nothing",
                "preview_text": "disclosure up top: i hold a massive long position",
                "cover_media": {"media_info": {"original_img_url": "https://pbs.twimg.com/media/HUHQMe5acAAnYSy.png"}},
                "content": {"blocks": [{"text": "full article body"}]}
              },
              "entities": {
                "urls": [{
                  "url": "https://t.co/ZZ89hgP5SU",
                  "expanded_url": "https://x.com/i/article/2108194886424088577",
                  "display_url": "x.com/i/article/2108…"
                }]
              }
            }
        """.trimIndent()

        val QUOTE = """
            {
              "text": "worth reading https://t.co/QuoteLink1",
              "quote": {
                "id": "99",
                "text": "the quoted line",
                "author": {"name": "Ada", "screen_name": "ada"}
              },
              "entities": {
                "urls": [{
                  "url": "https://t.co/QuoteLink1",
                  "expanded_url": "https://x.com/ada/status/99",
                  "display_url": "x.com/ada/status/99"
                }]
              }
            }
        """.trimIndent()

        val LINK = """
            {
              "text": "https://t.co/LinkCard01",
              "entities": {
                "urls": [{
                  "url": "https://t.co/LinkCard01",
                  "expanded_url": "https://example.com/story",
                  "display_url": "example.com/story"
                }]
              }
            }
        """.trimIndent()
    }
}
