package app.pane.android.data.reddit

/**
 * Logged-out document requests for one public post, in the order a phone should try them.
 *
 * User agents, and why:
 * - [CLIENT_USER_AGENT] is a descriptive client id. It does not contain "Android":
 *   Reddit has rejected agents that include that word.
 * - [DESKTOP_USER_AGENT] is a desktop browser agent. The 1.0.1 build sent mobile Chrome,
 *   which Reddit answered with a block page.
 * - [LEGACY_USER_AGENT] is Reddit's older `<platform>:<app>:<version> (by /u/contact)` form.
 *
 * Hosts: `old.reddit.com` before `www`, and the full `/r/{sub}/comments/{id}/…json` path
 * before the short `/comments/{id}.json` form. Tracking query parameters are not copied.
 * The last request is the HTML comments page, used only when every JSON document fails.
 */
object RedditFetchPlan {
    const val CLIENT_USER_AGENT = "Pane/1.0.42 (public post viewer)"
    const val DESKTOP_USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
    const val LEGACY_USER_AGENT = "android:app.pane.android:v1.0.42 (by /u/pane)"

    data class Request(
        val url: String,
        val userAgent: String,
        val accept: String,
    )

    fun requests(pageUrl: String, postId: String): List<Request> {
        val path = RedditUrls.commentsPath(pageUrl)
        val documents = RedditUrls.jsonCandidates(postId, path)
        val agents = listOf(CLIENT_USER_AGENT, DESKTOP_USER_AGENT, LEGACY_USER_AGENT, DESKTOP_USER_AGENT)
        val accepts = listOf(
            "application/json",
            "application/json, text/javascript, */*;q=0.01",
            "application/json",
            "application/json",
        )
        val json = documents.mapIndexed { index, url ->
            Request(
                url = url,
                userAgent = agents[index % agents.size],
                accept = accepts[index % accepts.size],
            )
        }
        val htmlPath = path ?: "/comments/${postId.lowercase()}"
        return json + Request(
            url = "https://www.reddit.com$htmlPath",
            userAgent = DESKTOP_USER_AGENT,
            accept = "text/html,application/xhtml+xml",
        )
    }
}
