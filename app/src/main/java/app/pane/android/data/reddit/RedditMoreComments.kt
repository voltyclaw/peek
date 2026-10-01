package app.pane.android.data.reddit

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Loads the next public page of top-level Reddit comments.
 * `morechildren` needs no OAuth. Commas in the children list stay literal.
 */
class RedditMoreComments(
    private val parser: RedditJsonParser = RedditJsonParser(),
    private val connectionFactory: (String) -> HttpURLConnection = { url ->
        URI(url).toURL().openConnection() as HttpURLConnection
    },
) {
    suspend fun fetch(postId: String, childIds: List<String>): MoreComments {
        if (!CHILD_ID.matches(postId)) throw IOException("Unsupported Reddit post id")
        val ids = batchIds(childIds)
        if (ids.isEmpty()) return MoreComments(emptyList(), emptyList())
        val body = get(moreChildrenUrl(postId, ids))
        return parser.parseMoreChildren(body)
    }

    internal fun moreChildrenUrl(postId: String, childIds: List<String>): String {
        val ids = batchIds(childIds)
        return "https://www.reddit.com/api/morechildren.json" +
            "?api_type=json&raw_json=1&sort=confidence&limit_children=false" +
            "&link_id=t3_$postId&children=${ids.joinToString(",")}"
    }

    private suspend fun get(url: String): String = withContext(Dispatchers.IO) {
        val connection = connectionFactory(url)
        try {
            connection.instanceFollowRedirects = true
            connection.requestMethod = "GET"
            connection.connectTimeout = CONNECT_TIMEOUT_MILLIS
            connection.readTimeout = READ_TIMEOUT_MILLIS
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("User-Agent", RedditFetchPlan.DESKTOP_USER_AGENT)
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader(StandardCharsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (status !in 200..299) throw IOException("Reddit returned HTTP $status")
            if (body.isBlank()) throw IOException("Reddit returned an empty response")
            body
        } finally {
            connection.disconnect()
        }
    }

    private fun batchIds(childIds: List<String>): List<String> =
        childIds.filter { CHILD_ID.matches(it) }.distinct().take(BATCH)

    private companion object {
        const val BATCH = 100
        const val CONNECT_TIMEOUT_MILLIS = 8_000
        const val READ_TIMEOUT_MILLIS = 12_000
        val CHILD_ID = Regex("""^[A-Za-z0-9]{1,12}$""")
    }
}
