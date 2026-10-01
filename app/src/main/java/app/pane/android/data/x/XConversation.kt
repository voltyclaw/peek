package app.pane.android.data.x

/**
 * Replies embedded in the logged-out status page. X renders the public conversation
 * into the HTML as a JavaScript object, ahead of any login wall.
 */
object XConversation {
    fun parseReplies(html: String, focalId: String): List<ParsedXReply> {
        val source = normalize(html)
        if (!source.contains("full_text")) return emptyList()
        val matches = ENTRY_ID.findAll(source).toList()
        val replies = mutableListOf<ParsedXReply>()
        for ((index, match) in matches.withIndex()) {
            val entryId = match.groupValues[1]
            if (entryId.startsWith("cursor")) continue
            val tweetId = TWEET_ID.findAll(entryId).lastOrNull()?.groupValues?.get(1) ?: continue
            if (tweetId == focalId) continue
            val afterEnd = matches.getOrNull(index + 1)?.range?.first ?: minOf(source.length, match.range.last + 12_000)
            val after = source.substring(match.range.last + 1, afterEnd)
            val beforeStart = if (index == 0) 0 else matches[index - 1].range.last + 1
            val before = source.substring(beforeStart, match.range.first)
            val slice = if (after.contains("full_text")) after else before
            val text = firstJsString(slice, "full_text")?.trim().orEmpty()
            if (text.isBlank()) continue
            val author = firstJsString(slice, "name")
                ?: firstJsString(slice, "screen_name")
                ?: "X"
            val created = CREATED.findAll(slice).firstOrNull()?.groupValues?.get(1)?.toLongOrNull() ?: 0L
            replies += ParsedXReply(
                id = tweetId,
                author = author,
                text = text,
                createdAtEpochMillis = created,
            )
            if (replies.size >= MAX_REPLIES) break
        }
        return replies.distinctBy(ParsedXReply::id)
    }

    /**
     * Note tweets store the full body separately from the short `full_text` preview.
     * The longest `text` field after a NoteTweet marker is that body.
     */
    fun parseNoteText(html: String): String? {
        val source = normalize(html)
        var best: String? = null
        var from = 0
        while (from < source.length) {
            val at = source.indexOf("NoteTweet", from)
            if (at < 0) break
            val window = source.substring(at, minOf(source.length, at + 20_000))
            val longest = jsStrings(window, "text").maxByOrNull { it.length }?.trim()
            if (!longest.isNullOrBlank() && longest.length > (best?.length ?: 0)) best = longest
            from = at + "NoteTweet".length
        }
        return best?.takeIf { it.isNotBlank() }
    }

    /** Keep a longer public body when it continues the short preview. */
    fun longerCaption(preview: String, candidate: String?): String {
        val extra = candidate?.trim().orEmpty()
        if (extra.length <= preview.length) return preview
        val head = preview.trim().take(48)
        if (head.length >= 16 && extra.startsWith(head)) return extra
        return preview
    }

    /** Logged-out HTML is sometimes a JSON blob with escaped quotes. Both shapes share one parser. */
    internal fun normalize(html: String): String =
        html.replace("\\u0022", "\"").replace("\\\"", "\"")

    private fun firstJsString(slice: String, key: String): String? {
        var best: String? = null
        var bestAt = Int.MAX_VALUE
        for (marker in listOf("$key:\"", "$key\":\"")) {
            var from = 0
            while (from < slice.length) {
                val start = slice.indexOf(marker, from)
                if (start < 0) break
                val previous = if (start == 0) ' ' else slice[start - 1]
                if (!previous.isLetterOrDigit() && previous != '_') {
                    if (start < bestAt) {
                        bestAt = start
                        best = readJsString(slice, start + marker.length)
                    }
                    break
                }
                from = start + marker.length
            }
        }
        return best
    }

    private fun jsStrings(slice: String, key: String): List<String> {
        val found = ArrayList<String>()
        for (marker in listOf("$key:\"", "$key\":\"")) {
            var from = 0
            while (from < slice.length) {
                val start = slice.indexOf(marker, from)
                if (start < 0) break
                val previous = if (start == 0) ' ' else slice[start - 1]
                if (!previous.isLetterOrDigit() && previous != '_') {
                    found += readJsString(slice, start + marker.length)
                }
                from = start + marker.length
            }
        }
        return found
    }

    private fun readJsString(source: String, start: Int): String {
        val out = StringBuilder()
        var index = start
        while (index < source.length) {
            val char = source[index]
            if (char == '"') break
            if (char == '\\' && index + 1 < source.length) {
                when (val escaped = source[index + 1]) {
                    'n' -> out.append('\n')
                    'r' -> out.append('\r')
                    't' -> out.append('\t')
                    'u' -> {
                        val hex = source.substring(index + 2, minOf(source.length, index + 6))
                        if (hex.length == 4 && hex.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) {
                            out.append(hex.toInt(16).toChar())
                            index += 6
                            continue
                        }
                        out.append(escaped)
                    }
                    else -> out.append(escaped)
                }
                index += 2
                continue
            }
            out.append(char)
            index += 1
        }
        return out.toString()
    }

    private val ENTRY_ID = Regex("""entry_id"?\s*:\s*"([^"]*)"""")
    private val TWEET_ID = Regex("""tweet-(\d+)""")
    private val CREATED = Regex("""created_at_ms"?\s*:\s*"?(\d+)""")
    private const val MAX_REPLIES = 80
}
