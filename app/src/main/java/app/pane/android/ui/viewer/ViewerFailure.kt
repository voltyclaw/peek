package app.pane.android.ui.viewer

import app.pane.android.R
import app.pane.android.domain.model.BlueskyPostException
import app.pane.android.domain.model.OfflineException
import app.pane.android.domain.model.PrivateGroupException
import app.pane.android.domain.model.SourceFailure
import app.pane.android.domain.model.StoryUnavailableException
import app.pane.android.ui.model.ViewerUiState
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/** Reasons the error page is allowed to say. Raw messages, HTML, and status codes stay off screen. */
enum class OpenFailureKind {
    Private,
    Expired,
    Network,
    Offline,
    Story,
    PrivateGroup,
    BlueskyGone,
    BlueskyHidden,
    BlueskySignedIn,
}

/** String resource for a failure the screen is allowed to show. Never the raw message. */
fun failureCopyRes(reason: OpenFailureKind): Int = when (reason) {
    OpenFailureKind.Private -> R.string.reason_private
    OpenFailureKind.Expired -> R.string.reason_expired
    OpenFailureKind.Network -> R.string.reason_network
    OpenFailureKind.Offline -> R.string.youre_offline
    OpenFailureKind.Story -> R.string.reason_story
    OpenFailureKind.PrivateGroup -> R.string.private_group_title
    OpenFailureKind.BlueskyGone -> R.string.bs_gone_title
    OpenFailureKind.BlueskyHidden -> R.string.bs_hidden_title
    OpenFailureKind.BlueskySignedIn -> R.string.bs_signed_in_title
}

/**
 * Maps a thrown error onto a screen.
 * Platform exceptions are classified by type. Anything else is a retryable network error.
 * [ViewerUiState.Unavailable] is only [SourceFailure.Unsupported].
 */
fun viewerStateFor(url: String, error: Throwable): ViewerUiState =
    viewerStateFor(url, error.asSourceFailure())

fun viewerStateFor(url: String, error: SourceFailure): ViewerUiState = when (error) {
    is SourceFailure.Unsupported -> ViewerUiState.Unavailable(error.url.ifBlank { url })
    is SourceFailure.Private -> ViewerUiState.LoadFailed(
        url = url,
        reason = if (error.reason == SourceFailure.PrivateReason.Group) {
            OpenFailureKind.PrivateGroup
        } else {
            OpenFailureKind.Private
        },
    )
    is SourceFailure.Gone -> ViewerUiState.LoadFailed(
        url = url,
        reason = when (error.bluesky) {
            null -> OpenFailureKind.Expired
            SourceFailure.BlueskyKind.Gone -> OpenFailureKind.BlueskyGone
            SourceFailure.BlueskyKind.Hidden -> OpenFailureKind.BlueskyHidden
            SourceFailure.BlueskyKind.SignedIn -> OpenFailureKind.BlueskySignedIn
        },
    )
    SourceFailure.StoryUnavailable -> ViewerUiState.LoadFailed(url = url, reason = OpenFailureKind.Story)
    is SourceFailure.Offline -> ViewerUiState.LoadFailed(url = url, reason = OpenFailureKind.Offline)
    is SourceFailure.Network -> ViewerUiState.LoadFailed(url = url, reason = OpenFailureKind.Network)
    is SourceFailure.Parse -> ViewerUiState.LoadFailed(url = url, reason = OpenFailureKind.Network)
}

/** True when the error screen should offer Retry. */
fun failureOffersRetry(state: ViewerUiState): Boolean =
    state is ViewerUiState.LoadFailed &&
        state.reason != OpenFailureKind.PrivateGroup &&
        state.reason != OpenFailureKind.BlueskyGone &&
        state.reason != OpenFailureKind.BlueskyHidden &&
        state.reason != OpenFailureKind.BlueskySignedIn

internal fun Throwable.asSourceFailure(): SourceFailure = when (this) {
    is SourceFailure -> this
    is OfflineException -> SourceFailure.Offline(this)
    is BlueskyPostException -> SourceFailure.Gone(
        bluesky = when (kind) {
            BlueskyPostException.Kind.Gone -> SourceFailure.BlueskyKind.Gone
            BlueskyPostException.Kind.Hidden -> SourceFailure.BlueskyKind.Hidden
            BlueskyPostException.Kind.SignedIn -> SourceFailure.BlueskyKind.SignedIn
        },
    )
    is PrivateGroupException -> SourceFailure.Private(SourceFailure.PrivateReason.Group)
    is StoryUnavailableException -> SourceFailure.StoryUnavailable
    is UnknownHostException, is ConnectException -> SourceFailure.Offline(this)
    is SocketTimeoutException -> SourceFailure.Network(this)
    else -> SourceFailure.Network(this)
}
