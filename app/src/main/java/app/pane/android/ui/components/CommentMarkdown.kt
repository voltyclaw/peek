package app.pane.android.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle

/** How far a nested reply sits past its parent. Depth is capped so a long chain stays readable. */
internal const val MAX_COMMENT_DEPTH = 8

internal fun commentBranchExpanded(collapsedIds: Set<String>, commentId: String): Boolean =
    commentId !in collapsedIds

/** Caption and comment text with bare http(s) URLs marked as links. */
internal fun autolinkedCaption(source: String, linkColor: Color): AnnotatedString =
    buildAnnotatedString { appendWithAutolinks(source, linkColor) }

internal fun commentNestingStepDp(depth: Int): Int =
    if (depth in 1..MAX_COMMENT_DEPTH) 32 else 0

/**
 * Reddit comment bodies are markdown. This covers the marks that show up in public threads:
 * bold, italic, strike, inline code, links, spoilers, and quote lines.
 */
internal fun redditCommentAnnotated(
    source: String,
    linkColor: Color,
    quoteColor: Color,
    codeFont: FontFamily,
): AnnotatedString = buildAnnotatedString {
    val lines = source.split('\n')
    lines.forEachIndexed { index, line ->
        if (index > 0) append('\n')
        val trimmed = line.trimStart()
        val quote = trimmed.startsWith(">") && !trimmed.startsWith(">!")
        if (quote) {
            withStyle(SpanStyle(color = quoteColor, fontStyle = FontStyle.Italic)) {
                appendMarkdown(trimmed.removePrefix(">").trimStart(), linkColor, codeFont)
            }
        } else {
            appendMarkdown(line, linkColor, codeFont)
        }
    }
}

private fun AnnotatedString.Builder.appendMarkdown(
    text: String,
    linkColor: Color,
    codeFont: FontFamily,
) {
    var index = 0
    while (index < text.length) {
        if (text[index] == '\\' && index + 1 < text.length) {
            append(text[index + 1])
            index += 2
            continue
        }
        val url = plainUrlAt(text, index)
        if (url != null) {
            withLink(
                LinkAnnotation.Url(
                    url,
                    TextLinkStyles(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)),
                ),
            ) {
                append(url)
            }
            index += url.length
            continue
        }
        if (text.startsWith(">!", index)) {
            val end = text.indexOf("!<", index + 2)
            if (end > index + 2) {
                withStyle(SpanStyle(fontStyle = FontStyle.Italic, background = quoteTint(linkColor))) {
                    appendMarkdown(text.substring(index + 2, end), linkColor, codeFont)
                }
                index = end + 2
                continue
            }
        }
        if (text[index] == '[') {
            val labelEnd = text.indexOf("](", index + 1)
            val urlEnd = if (labelEnd > index) text.indexOf(')', labelEnd + 2) else -1
            if (labelEnd > index + 1 && urlEnd > labelEnd + 2) {
                val label = text.substring(index + 1, labelEnd)
                val url = text.substring(labelEnd + 2, urlEnd)
                if (url.startsWith("http://") || url.startsWith("https://")) {
                    withLink(
                        LinkAnnotation.Url(
                            url,
                            TextLinkStyles(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)),
                        ),
                    ) {
                        append(label)
                    }
                    index = urlEnd + 1
                    continue
                }
            }
        }
        if (text[index] == '`') {
            val end = text.indexOf('`', index + 1)
            if (end > index + 1) {
                withStyle(SpanStyle(fontFamily = codeFont, background = quoteTint(linkColor))) {
                    append(text.substring(index + 1, end))
                }
                index = end + 1
                continue
            }
        }
        if (text.startsWith("~~", index)) {
            val end = text.indexOf("~~", index + 2)
            if (end > index + 2) {
                withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) {
                    appendMarkdown(text.substring(index + 2, end), linkColor, codeFont)
                }
                index = end + 2
                continue
            }
        }
        val boldMarker = when {
            text.startsWith("**", index) -> "**"
            text.startsWith("__", index) -> "__"
            else -> null
        }
        if (boldMarker != null) {
            val end = text.indexOf(boldMarker, index + 2)
            if (end > index + 2) {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                    appendMarkdown(text.substring(index + 2, end), linkColor, codeFont)
                }
                index = end + 2
                continue
            }
        }
        if (text[index] == '*' && text.getOrNull(index + 1) != '*') {
            val end = text.indexOf('*', index + 1)
            if (end > index + 1 && text.getOrNull(end + 1) != '*') {
                withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                    append(text.substring(index + 1, end))
                }
                index = end + 1
                continue
            }
        }
        if (text[index] == '_' && isItalicBoundary(text, index)) {
            val end = closingItalic(text, index + 1)
            if (end > index + 1) {
                withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                    append(text.substring(index + 1, end))
                }
                index = end + 1
                continue
            }
        }
        append(text[index])
        index += 1
    }
}

private fun isItalicBoundary(text: String, index: Int): Boolean {
    val previous = text.getOrNull(index - 1)
    val next = text.getOrNull(index + 1)
    return (previous == null || !previous.isLetterOrDigit()) && next != null && next != '_' && !next.isWhitespace()
}

private fun closingItalic(text: String, from: Int): Int {
    var index = from
    while (index < text.length) {
        if (text[index] == '_' && text.getOrNull(index - 1)?.isWhitespace() != true) {
            val next = text.getOrNull(index + 1)
            if (next == null || !next.isLetterOrDigit()) return index
        }
        index += 1
    }
    return -1
}

private fun AnnotatedString.Builder.appendWithAutolinks(text: String, linkColor: Color) {
    var index = 0
    while (index < text.length) {
        val url = plainUrlAt(text, index)
        if (url != null) {
            withLink(
                LinkAnnotation.Url(
                    url,
                    TextLinkStyles(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)),
                ),
            ) {
                append(url)
            }
            index += url.length
            continue
        }
        append(text[index])
        index += 1
    }
}

private fun plainUrlAt(text: String, index: Int): String? {
    val rest = text.substring(index)
    val marker = when {
        rest.startsWith("https://", ignoreCase = true) -> "https://"
        rest.startsWith("http://", ignoreCase = true) -> "http://"
        else -> return null
    }
    if (index > 0) {
        val previous = text[index - 1]
        if (previous.isLetterOrDigit() || previous == '@' || previous == '/') return null
        if (previous == '(' && text.getOrNull(index - 2) == ']') return null
    }
    val end = rest.indexOfAny(charArrayOf(' ', '\n', '\t', '<', '>', '"', '\'')).let { if (it < 0) rest.length else it }
    var url = rest.substring(0, end)
    while (url.length > marker.length && url.last() in TRAILING_URL_PUNCTUATION) {
        url = url.dropLast(1)
    }
    if (url.length <= marker.length) return null
    return url
}

private const val TRAILING_URL_PUNCTUATION = ".,);:!?]}"

private fun quoteTint(color: Color): Color = color.copy(alpha = 0.12f)
