package app.pane.android.ui.viewer

import app.pane.android.R
import app.pane.android.domain.model.StoryUnavailableException
import app.pane.android.ui.model.ViewerUiState
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/** Reasons the error page is allowed to say. Raw messages, HTML, and status codes stay off screen. */
enum class OpenFailureKind {
    Private,
    Expired,
    Network,
    Story,
}

fun openFailureKind(message: String?): OpenFailureKind {
    val lower = message.orEmpty().lowercase()
    if (containsAny(lower, "story unavailable")) return OpenFailureKind.Story
    if (isConnectivityMessage(lower)) return OpenFailureKind.Network
    if (containsAny(lower, "private", "login", "log in", "sign in", "unauthorized", "forbidden", "blocked", "checkpoint")) {
        return OpenFailureKind.Private
    }
    if (containsAny(lower, "expired", "not found", "removed", "deleted", "gone")) {
        return OpenFailureKind.Expired
    }
    if (containsStatus(lower, "401", "403")) return OpenFailureKind.Private
    if (containsStatus(lower, "404", "410")) return OpenFailureKind.Expired
    return OpenFailureKind.Network
}

/** Offline, DNS, and timeouts are recoverable. They are not "not a public post". */
private fun isConnectivityMessage(lower: String): Boolean = containsAny(
    lower,
    "timeout",
    "timed out",
    "offline",
    "unknown host",
    "unable to resolve",
    "failed to connect",
    "connection reset",
    "connection refused",
    "unreachable",
    "no address",
    "network is unreachable",
    "enotfound",
)

/** String resource for a failure the screen is allowed to show. Never the raw message. */
fun failureCopyRes(reason: String): Int {
    val kind = runCatching { OpenFailureKind.valueOf(reason) }.getOrElse { openFailureKind(reason) }
    return when (kind) {
        OpenFailureKind.Private -> R.string.reason_private
        OpenFailureKind.Expired -> R.string.reason_expired
        OpenFailureKind.Network -> R.string.reason_network
        OpenFailureKind.Story -> R.string.reason_story
    }
}

/** Unsupported URLs keep the existing screen. Fetch and parse failures stay separate. */
fun viewerStateFor(url: String, error: Throwable): ViewerUiState = when {
    error is StoryUnavailableException -> ViewerUiState.LoadFailed(
        url = url,
        reason = OpenFailureKind.Story.name,
    )
    error is UnknownHostException || error is SocketTimeoutException || error is ConnectException ->
        ViewerUiState.LoadFailed(url = url, reason = OpenFailureKind.Network.name)
    error is IllegalArgumentException -> ViewerUiState.Unavailable(url)
    error is IOException && isConnectivityMessage(error.message.orEmpty().lowercase()) ->
        ViewerUiState.LoadFailed(url = url, reason = OpenFailureKind.Network.name)
    else -> ViewerUiState.LoadFailed(
        url = url,
        reason = openFailureKind(error.message).name,
    )
}

/** True when the error screen should offer Retry. Classification misses do not. */
fun failureOffersRetry(state: ViewerUiState): Boolean = state is ViewerUiState.LoadFailed

private fun containsAny(text: String, vararg needles: String): Boolean =
    needles.any { it in text }

/** Match a status code as its own token so a long page does not trip on a stray digit run. */
private fun containsStatus(text: String, vararg codes: String): Boolean =
    codes.any { code ->
        val index = text.indexOf(code)
        index >= 0 &&
            (index == 0 || !text[index - 1].isDigit()) &&
            (index + code.length == text.length || !text[index + code.length].isDigit())
    }
