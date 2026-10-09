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
        assertEquals("see example.com/story", XRichText.visible(text))
        assertTrue(text.contains("](https://example.com/story)"))
        assertFalse(text.contains("t.co"))
    }

    @Test
    fun aTextLinkInTheMiddleOrEndOfASentenceStaysExpanded() {
        val entities = listOf(
            entity("https://t.co/Middle01", "https://example.com/report", "example.com/report"),
            entity("https://t.co/End00001", "https://example.com/notes", "example.com/notes"),
        )
        val middle = XRichText.expand(
            "See the report https://t.co/Middle01 today.",
            entities,
            emptySet(),
        )
        val end = XRichText.expand(
            "Full disclosures: https://t.co/End00001.",
            entities,
            emptySet(),
        )
        assertEquals("See the report [example.com/report](https://example.com/report) today.", middle)
        assertEquals("Full disclosures: [example.com/notes](https://example.com/notes).", end)
        assertFalse(middle.contains("t.co"))
        assertFalse(end.contains("t.co"))
        assertFalse(end.endsWith(": .") || end.endsWith(":."))
    }

    @Test
    fun aMediaShortLinkIsStrippedWithoutLeavingPunctuation() {
        val entities = listOf(
            entity("https://t.co/Media0001", "https://x.com/ada/status/20/video/1", "x.com/ada/status/20/video/1"),
            entity("https://t.co/Photo0001", "https://pic.x.com/abcd", "pic.x.com/abcd"),
        )
        val video = XRichText.expand(
            "Watch the full interview: https://t.co/Media0001",
            entities,
            emptySet(),
            stripMedia = true,
        )
        val photo = XRichText.expand(
            "Full disclosures: https://t.co/Photo0001.",
            entities,
            emptySet(),
            stripMedia = true,
        )
        assertEquals("Watch the full interview", video)
        assertEquals("Full disclosures", photo)
        assertFalse(video.contains(":") || video.contains("t.co"))
        assertFalse(photo.contains("t.co") || photo.endsWith("."))
    }

    @Test
    fun aQuoteShortLinkIsStrippedOnlyWhenTheQuoteCardRenders() {
        val rich = XRichText.present(Json.parseToJsonElement(QUOTE).jsonObject)
        assertEquals("worth reading", XRichText.visible(rich.text))
        assertEquals("https://x.com/i/status/99", rich.quote?.url)

        val kept = XRichText.expandShortLinks(
            "worth reading https://t.co/QuoteLink1",
            """{"url":"https://t.co/QuoteLink1","expanded_url":"https://x.com/ada/status/99","display_url":"x.com/ada/status/99"}""",
        )
        assertEquals("worth reading [x.com/ada/status/99](https://x.com/ada/status/99)", kept)
    }

    @Test
    fun aCardShortLinkIsStrippedOnlyWhenTheCardRenders() {
        val rich = XRichText.present(Json.parseToJsonElement(LINK).jsonObject)
        assertEquals("", rich.text)
        assertEquals("example.com/story", rich.links.single().label)
        assertEquals("https://example.com/story", rich.links.single().url)

        val sentence = XRichText.present(
            Json.parseToJsonElement(
                """
                {
                  "text": "Read the notes https://t.co/LinkCard01 today.",
                  "entities": {"urls": [{
                    "url": "https://t.co/LinkCard01",
                    "expanded_url": "https://example.com/story",
                    "display_url": "example.com/story"
                  }]}
                }
                """.trimIndent(),
            ).jsonObject,
        )
        assertTrue(sentence.links.isEmpty())
        assertEquals(
            "Read the notes [example.com/story](https://example.com/story) today.",
            sentence.text,
        )
    }

    @Test
    fun anUnknownShortLinkIsKeptRatherThanLeavingADanglingColon() {
        val text = XRichText.expandShortLinks("Full disclosures: https://t.co/NoEntity1.")
        assertEquals("Full disclosures: https://t.co/NoEntity1.", text)
    }

    private fun entity(url: String, expanded: String, display: String) =
        app.pane.android.data.x.XUrlEntity(url, expanded, display)

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
