package app.pane.android.data.x

/**
 * Public conversation embedded in the logged-out status page.
 *
 * Replies from other people are a short list ahead of the login wall. That document’s
 * conversation cursor is null, and the guest TweetDetail query id in the page does not
 * resolve, so there is no further logged-out reply page to request.
 *
 * An author thread is different: the same page lists the account’s own chain in order.
 * Posts before the opened status use display type Tweet. The opened status and later
 * posts use SelfThread. Each block’s first rest id is that post, and reply_to_results
 * points at its parent when it has one.
 */
object XConversation {
    fun parseAuthorThread(html: String, focalId: String): ParsedAuthorThread {
        val source = normalize(html)
        val markers = DISPLAY_TYPE.findAll(source).toList()
        if (markers.isEmpty()) return ParsedAuthorThread()
        val blocks = markers.mapIndexedNotNull { index, match ->
            val next = markers.getOrNull(index + 1)?.range?.first ?: source.length
            val end = minOf(next, match.range.last + 1 + BLOCK_WINDOW)
            parseThreadBlock(source.substring(match.range.last + 1, end))
        }
        val focal = blocks.firstOrNull { it.id == focalId } ?: return ParsedAuthorThread()
        if (focal.screenName.isBlank()) return ParsedAuthorThread()
        val posts = blocks
            .filter { it.screenName.equals(focal.screenName, ignoreCase = true) }
            .distinctBy { it.id }
        if (posts.size < 2 || posts.none { it.id == focalId }) return ParsedAuthorThread()
        val ids = posts.map { it.id }.toSet()
        val first = posts.first()
        val parent = first.parentId
        val parentScreen = first.parentScreenName
        val parentIsThisAuthor = parentScreen == null ||
            parentScreen.equals(focal.screenName, ignoreCase = true)
        val partial = !parent.isNullOrBlank() && parent !in ids && parentIsThisAuthor
        return ParsedAuthorThread(
            posts = posts.map { block ->
                ParsedXThreadPost(
                    id = block.id,
                    author = block.author,
                    screenName = block.screenName,
                    text = block.text,
                )
            },
            partial = partial,
        )
    }

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

    private fun parseThreadBlock(slice: String): ThreadBlock? {
        val id = REST_ID.find(slice)?.groupValues?.get(1) ?: return null
        val screenName = firstJsString(slice, "screen_name")?.trim().orEmpty()
        if (screenName.isBlank()) return null
        val preview = firstJsString(slice, "full_text")?.trim().orEmpty()
        val note = parseNoteText(slice)
        val text = longerCaption(preview, note).ifBlank { note?.trim().orEmpty() }
        if (text.isBlank()) return null
        return ThreadBlock(
            id = id,
            author = authorBeside(slice, screenName),
            screenName = screenName,
            text = text,
            parentId = parentRestId(slice),
            parentScreenName = parentScreenName(slice),
        )
    }

    private fun authorBeside(slice: String, screenName: String): String {
        val at = slice.indexOf("screen_name:\"$screenName\"")
        if (at < 0) return screenName
        val before = slice.substring(maxOf(0, at - 160), at)
        return jsStrings(before, "name").lastOrNull()?.trim()?.takeIf { it.isNotBlank() } ?: screenName
    }

    private fun parentRestId(slice: String): String? {
        val at = slice.indexOf("reply_to_results")
        if (at < 0) return null
        val window = slice.substring(at, minOf(slice.length, at + 500))
        return REST_ID.find(window)?.groupValues?.get(1)
    }

    private fun parentScreenName(slice: String): String? {
        val at = slice.indexOf("reply_to_user_results")
        if (at < 0) return null
        val window = slice.substring(at, minOf(slice.length, at + 500))
        return firstJsString(window, "screen_name")?.trim()?.takeIf { it.isNotBlank() }
    }

    private data class ThreadBlock(
        val id: String,
        val author: String,
        val screenName: String,
        val text: String,
        val parentId: String?,
        val parentScreenName: String?,
    )

    private val ENTRY_ID = Regex("""entry_id"?\s*:\s*"([^"]*)"""")
    private val TWEET_ID = Regex("""tweet-(\d+)""")
    private val CREATED = Regex("""created_at_ms"?\s*:\s*"?(\d+)""")
    private val DISPLAY_TYPE = Regex("""display_type"?\s*:\s*"(Tweet|SelfThread)"""")
    private val REST_ID = Regex("""rest_id"?\s*:\s*"(\d+)"""")
    private const val MAX_REPLIES = 200
    private const val BLOCK_WINDOW = 24_000
}
