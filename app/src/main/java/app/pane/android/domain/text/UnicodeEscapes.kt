package app.pane.android.domain.text

/**
 * Turns JSON `\uXXXX`, a doubled `\\uXXXX`, and a bare `uXXXX` remnant into characters.
 * Bare escapes decode only above U+007F, and only when a letter does not sit in front,
 * except the second half of a surrogate pair (`uD83DuDE00`).
 */
object UnicodeEscapes {
    fun decode(text: String): String {
        if (text.length < 5) return text
        val out = StringBuilder(text.length)
        var index = 0
        var justDecoded = false
        var changed = false
        while (index < text.length) {
            val parsed = parseAt(text, index, justDecoded)
            if (parsed == null) {
                out.append(text[index])
                index += 1
                justDecoded = false
                continue
            }
            val combined = combine(text, parsed)
            out.appendCodePoint(combined.codePoint)
            index = combined.nextIndex
            justDecoded = true
            changed = true
        }
        return if (changed) out.toString() else text
    }

    private class Escape(val codePoint: Int, val nextIndex: Int)

    private fun parseAt(text: String, index: Int, justDecoded: Boolean): Escape? {
        if (startsWith(text, index, "\\\\u") || startsWith(text, index, "\\\\U")) {
            val hex = hex4(text, index + 3) ?: return null
            return Escape(hex, index + 7)
        }
        if (index + 5 < text.length && text[index] == '\\' && (text[index + 1] == 'u' || text[index + 1] == 'U')) {
            val hex = hex4(text, index + 2) ?: return null
            return Escape(hex, index + 6)
        }
        if (text[index] == 'u' || text[index] == 'U') {
            val hex = hex4(text, index + 1) ?: return null
            if (hex <= 0x7F) return null
            val precededByLetter = index > 0 && text[index - 1].isLetter()
            if (precededByLetter && !justDecoded) return null
            return Escape(hex, index + 5)
        }
        return null
    }

    private class Combined(val codePoint: Int, val nextIndex: Int)

    private fun combine(text: String, first: Escape): Combined {
        if (first.codePoint !in 0xD800..0xDBFF) return Combined(first.codePoint, first.nextIndex)
        val second = parseAt(text, first.nextIndex, justDecoded = true) ?: return Combined(first.codePoint, first.nextIndex)
        if (second.codePoint !in 0xDC00..0xDFFF) return Combined(first.codePoint, first.nextIndex)
        val point = Character.toCodePoint(first.codePoint.toChar(), second.codePoint.toChar())
        return Combined(point, second.nextIndex)
    }

    private fun hex4(text: String, start: Int): Int? {
        if (start + 4 > text.length) return null
        for (offset in 0 until 4) {
            val char = text[start + offset]
            val hex = char in '0'..'9' || char in 'a'..'f' || char in 'A'..'F'
            if (!hex) return null
        }
        return text.substring(start, start + 4).toInt(16)
    }

    private fun startsWith(text: String, index: Int, literal: String): Boolean {
        if (index + literal.length > text.length) return false
        for (offset in literal.indices) {
            if (text[index + offset] != literal[offset]) return false
        }
        return true
    }
}
