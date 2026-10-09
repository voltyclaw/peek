package app.pane.android

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import app.pane.android.ui.components.MAX_COMMENT_DEPTH
import app.pane.android.ui.components.autolinkedCaption
import app.pane.android.ui.components.commentBranchExpanded
import app.pane.android.ui.components.commentNestingStepDp
import app.pane.android.ui.components.redditCommentAnnotated
import androidx.compose.ui.text.LinkAnnotation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CommentMarkdownTest {
    private val bodyColor = Color(0xFF1E2A22)
    private val linkColor = Color(0xFF3A8F6A)

    @Test
    fun anExpandedShortLinkShowsTheDisplayTextAndOpensTheTarget() {
        val text = autolinkedCaption(
            "Full disclosures: [example.com/notes](https://example.com/notes).",
            linkColor,
        )
        assertEquals("Full disclosures: example.com/notes.", text.text)
        val link = text.getLinkAnnotations(0, text.length).map { it.item }.filterIsInstance<LinkAnnotation.Clickable>().single()
        assertEquals("https://example.com/notes", link.tag)
    }

    @Test
    fun anEmptyOrPunctuationLabelShowsTheDomain() {
        val empty = autolinkedCaption("see [](https://example.com/notes).", linkColor)
        val dots = redditCommentAnnotated("see [...](https://example.com/notes).", linkColor, bodyColor, FontFamily.Monospace)
        assertEquals("see example.com.", empty.text)
        assertEquals("see example.com.", dots.text)
        val kept = autolinkedCaption("[example.com/notes](https://example.com/notes)", linkColor)
        assertEquals("example.com/notes", kept.text)
    }

    @Test
    fun plainTextIsUnchanged() {
        val text = redditCommentAnnotated("Just a sentence.", linkColor, bodyColor, FontFamily.Monospace)
        assertEquals("Just a sentence.", text.text)
        assertTrue(text.spanStyles.isEmpty())
    }

    @Test
    fun boldItalicStrikeCodeAndLinksAreMarked() {
        val text = redditCommentAnnotated(
            "See **bold** and *slant* plus ~~gone~~ `code` and [docs](https://reddit.com/r/test).",
            linkColor,
            bodyColor,
            FontFamily.Monospace,
        )
        assertEquals("See bold and slant plus gone code and docs.", text.text)
        assertTrue(text.spanStyles.any { it.item.fontWeight == FontWeight.Bold })
        assertTrue(text.spanStyles.any { it.item.fontStyle == FontStyle.Italic })
        assertTrue(text.spanStyles.any { it.item.textDecoration == TextDecoration.LineThrough })
        assertTrue(text.spanStyles.any { it.item.fontFamily == FontFamily.Monospace })
        val link = text.getLinkAnnotations(0, text.length).single().item
        assertTrue(link is LinkAnnotation.Clickable)
        assertEquals("https://reddit.com/r/test", (link as LinkAnnotation.Clickable).tag)
    }

    @Test
    fun underscoresInsideWordsStayLiteral() {
        val text = redditCommentAnnotated("use screen_name here, but _this_ is italic", linkColor, bodyColor, FontFamily.Monospace)
        assertEquals("use screen_name here, but this is italic", text.text)
        assertEquals(1, text.spanStyles.count { it.item.fontStyle == FontStyle.Italic })
    }

    @Test
    fun quoteLinesAndSpoilersKeepTheirText() {
        val text = redditCommentAnnotated("> a quote\nspoiler >!hidden!< end", linkColor, bodyColor, FontFamily.Monospace)
        assertEquals("a quote\nspoiler hidden end", text.text)
        assertTrue(text.spanStyles.any { it.item.color == bodyColor })
    }

    @Test
    fun bareUrlsInCaptionsAndCommentsAreLinks() {
        val source = "Waze https://waze.com/ul/q and maps https://maps.google.com/q=1."
        val caption = autolinkedCaption(source, linkColor)
        val comment = redditCommentAnnotated(source, linkColor, bodyColor, FontFamily.Monospace)
        assertEquals(source, caption.text)
        assertEquals(
            listOf("https://waze.com/ul/q", "https://maps.google.com/q=1"),
            caption.getLinkAnnotations(0, caption.length).map { (it.item as LinkAnnotation.Clickable).tag },
        )
        assertEquals(
            listOf("https://waze.com/ul/q", "https://maps.google.com/q=1"),
            comment.getLinkAnnotations(0, comment.length).map { (it.item as LinkAnnotation.Clickable).tag },
        )
    }

    @Test
    fun aCollapsedBranchHidesThatCommentOnly() {
        assertTrue(commentBranchExpanded(emptySet(), "abc"))
        assertTrue(!commentBranchExpanded(setOf("abc"), "abc"))
        assertTrue(commentBranchExpanded(setOf("abc"), "child"))
    }

    @Test
    fun nestingIndentsOneStepPerLevelAndThenStops() {
        assertEquals(0, commentNestingStepDp(0))
        assertEquals(12, commentNestingStepDp(1))
        assertEquals(12, commentNestingStepDp(MAX_COMMENT_DEPTH))
        assertEquals(0, commentNestingStepDp(MAX_COMMENT_DEPTH + 1))
    }
}
