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
import app.pane.android.domain.text.LinkLabels
import app.pane.android.ui.text.MentionNetwork
import app.pane.android.ui.text.mentionAt

/** How far a nested reply sits past its parent. Depth is capped so a long chain stays readable. */
internal const val MAX_COMMENT_DEPTH = 8

internal fun commentBranchExpanded(collapsedIds: Set<String>, commentId: String): Boolean =
    commentId !in collapsedIds

/** Caption and comment text. Handles, subreddit names, and URLs open through [onOpen]. */
internal fun autolinkedCaption(
    source: String,
    linkColor: Color,
    network: MentionNetwork = MentionNetwork.Other,
    onOpen: (String) -> Unit = {},
    mentionColor: Color? = null,
    mentionPressed: Color? = null,
): AnnotatedString = buildAnnotatedString {
    appendWithAutolinks(source, linkColor, network, onOpen, mentionColor, mentionPressed)
}

internal fun commentNestingStepDp(depth: Int): Int =
    if (depth in 1..MAX_COMMENT_DEPTH) 12 else 0

/**
 * Reddit comment bodies are markdown. This covers the marks that show up in public threads:
 * bold, italic, strike, inline code, links, spoilers, and quote lines.
 */
internal fun redditCommentAnnotated(
    source: String,
    linkColor: Color,
    quoteColor: Color,
    codeFont: FontFamily,
    network: MentionNetwork = MentionNetwork.Reddit,
    onOpen: (String) -> Unit = {},
    mentionColor: Color? = null,
    mentionPressed: Color? = null,
): AnnotatedString = buildAnnotatedString {
    val lines = source.split('\n')
    lines.forEachIndexed { index, line ->
        if (index > 0) append('\n')
        val trimmed = line.trimStart()
        val quote = trimmed.startsWith(">") && !trimmed.startsWith(">!")
        if (quote) {
            withStyle(SpanStyle(color = quoteColor, fontStyle = FontStyle.Italic)) {
                appendMarkdown(trimmed.removePrefix(">").trimStart(), linkColor, codeFont, network, onOpen, mentionColor, mentionPressed)
            }
        } else {
            appendMarkdown(line, linkColor, codeFont, network, onOpen, mentionColor, mentionPressed)
        }
    }
}

private fun AnnotatedString.Builder.appendMarkdown(
    text: String,
    linkColor: Color,
    codeFont: FontFamily,
    network: MentionNetwork,
    onOpen: (String) -> Unit,
    mentionColor: Color?,
    mentionPressed: Color?,
) {
    var index = 0
    while (index < text.length) {
        if (text[index] == '\\' && index + 1 < text.length) {
            append(text[index + 1])
            index += 2
            continue
        }
        val mention = mentionAt(text, index, network)
        if (mention != null) {
            withLink(openLink(mention.url, linkColor, onOpen, mention.kind != app.pane.android.ui.text.MentionKind.Url, mentionColor, mentionPressed)) {
                append(text.substring(mention.start, mention.end))
            }
            index = mention.end
            continue
        }
        if (text.startsWith(">!", index)) {
            val end = text.indexOf("!<", index + 2)
            if (end > index + 2) {
                withStyle(SpanStyle(fontStyle = FontStyle.Italic, background = quoteTint(linkColor))) {
                    appendMarkdown(text.substring(index + 2, end), linkColor, codeFont, network, onOpen, mentionColor, mentionPressed)
                }
                index = end + 2
                continue
            }
        }
        if (text[index] == '[') {
            val labelEnd = text.indexOf("](", index + 1)
            val urlEnd = if (labelEnd > index) text.indexOf(')', labelEnd + 2) else -1
            if (labelEnd >= index + 1 && urlEnd > labelEnd + 2) {
                val url = text.substring(labelEnd + 2, urlEnd)
                val label = LinkLabels.display(text.substring(index + 1, labelEnd), url)
                if (url.startsWith("http://") || url.startsWith("https://")) {
                    withLink(openLink(url, linkColor, onOpen)) {
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
                    appendMarkdown(text.substring(index + 2, end), linkColor, codeFont, network, onOpen, mentionColor, mentionPressed)
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
                    appendMarkdown(text.substring(index + 2, end), linkColor, codeFont, network, onOpen, mentionColor, mentionPressed)
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

private fun AnnotatedString.Builder.appendWithAutolinks(
    text: String,
    linkColor: Color,
    network: MentionNetwork,
    onOpen: (String) -> Unit,
    mentionColor: Color?,
    mentionPressed: Color?,
) {
    var index = 0
    while (index < text.length) {
        if (text[index] == '[') {
            val labelEnd = text.indexOf("](", index + 1)
            val urlEnd = if (labelEnd > index) text.indexOf(')', labelEnd + 2) else -1
            if (labelEnd >= index + 1 && urlEnd > labelEnd + 2) {
                val url = text.substring(labelEnd + 2, urlEnd)
                val label = LinkLabels.display(text.substring(index + 1, labelEnd), url)
                if (url.startsWith("http://") || url.startsWith("https://")) {
                    withLink(openLink(url, linkColor, onOpen)) {
                        append(label)
                    }
                    index = urlEnd + 1
                    continue
                }
            }
        }
        val mention = mentionAt(text, index, network)
        if (mention != null) {
            val account = mention.kind != app.pane.android.ui.text.MentionKind.Url
            withLink(openLink(mention.url, linkColor, onOpen, account, mentionColor, mentionPressed)) {
                append(text.substring(mention.start, mention.end))
            }
            index = mention.end
            continue
        }
        append(text[index])
        index += 1
    }
}

private fun openLink(
    url: String,
    ink: Color,
    onOpen: (String) -> Unit,
    account: Boolean = false,
    mentionColor: Color? = null,
    mentionPressed: Color? = null,
): LinkAnnotation.Clickable {
    val handle = if (account) mentionColor else null
    val pressed = if (account) mentionPressed ?: mentionColor else null
    return LinkAnnotation.Clickable(
        tag = url,
        styles = if (handle != null && pressed != null) {
            TextLinkStyles(
                style = SpanStyle(color = handle, fontWeight = FontWeight.Medium),
                pressedStyle = SpanStyle(
                    color = pressed,
                    fontWeight = FontWeight.Medium,
                    background = handle.copy(alpha = 0.16f),
                ),
                focusedStyle = SpanStyle(
                    color = pressed,
                    fontWeight = FontWeight.Medium,
                    background = handle.copy(alpha = 0.16f),
                ),
            )
        } else {
            TextLinkStyles(
                style = SpanStyle(fontWeight = FontWeight.Medium),
                pressedStyle = SpanStyle(
                    fontWeight = FontWeight.Medium,
                    textDecoration = TextDecoration.Underline,
                    background = ink.copy(alpha = 0.08f),
                ),
            )
        },
        linkInteractionListener = { onOpen(url) },
    )
}

private fun quoteTint(color: Color): Color = color.copy(alpha = 0.12f)
