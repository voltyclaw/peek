package app.pane.android.data.bluesky

import app.pane.android.domain.model.BskyTextSpan

internal data class BskyFacetMark(
    val byteStart: Int,
    val byteEnd: Int,
    val url: String?,
)

internal object BskyFacets {
    /** UTF-8 byte offset to a UTF-16 index. Emoji that take two UTF-16 units stay intact. */
    fun utf8ToUtf16(text: String, byteOffset: Int): Int {
        if (byteOffset <= 0) return 0
        var bytes = 0
        var utf16 = 0
        var index = 0
        while (index < text.length) {
            if (bytes >= byteOffset) return utf16
            val code = text.codePointAt(index)
            val chars = Character.charCount(code)
            bytes += utf8Length(code)
            utf16 += chars
            index += chars
        }
        return text.length
    }

    fun spans(text: String, marks: List<BskyFacetMark>): List<BskyTextSpan> {
        return marks.mapNotNull { mark ->
            val start = utf8ToUtf16(text, mark.byteStart).coerceIn(0, text.length)
            val end = utf8ToUtf16(text, mark.byteEnd).coerceIn(start, text.length)
            if (end <= start && mark.url == null) return@mapNotNull null
            val slice = text.substring(start, end)
            val url = mark.url
            val shown = if (url != null && (slice.isBlank() || slice.all { it.isWhitespace() || isPunctuation(it) })) {
                domainOf(url)
            } else {
                null
            }
            if (shown != null && shown.isNotBlank() && shown != slice) {
                BskyTextSpan(start, end, url)
            } else {
                BskyTextSpan(start, end, url)
            }
        }.filter { it.end > it.start || !it.url.isNullOrBlank() }
    }

    /** Display label for a link whose slice is empty or punctuation. */
    fun linkLabel(text: String, span: BskyTextSpan): String {
        val slice = text.substring(span.start.coerceIn(0, text.length), span.end.coerceIn(0, text.length))
        if (slice.isNotBlank() && !slice.all { it.isWhitespace() || isPunctuation(it) }) return slice
        return span.url?.let(::domainOf).orEmpty().ifBlank { slice }
    }

    fun domainOf(url: String): String {
        val host = runCatching { java.net.URI(url).host }.getOrNull()?.removePrefix("www.").orEmpty()
        return host.ifBlank { url }
    }

    private fun isPunctuation(char: Char): Boolean =
        char in ".,;:!?\"'()[]{}-–—/\\…·" || char.isISOControl()

    private fun utf8Length(codePoint: Int): Int = when {
        codePoint <= 0x7F -> 1
        codePoint <= 0x7FF -> 2
        codePoint <= 0xFFFF -> 3
        else -> 4
    }
}
