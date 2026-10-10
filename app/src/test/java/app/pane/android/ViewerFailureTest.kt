package app.pane.android

import app.pane.android.domain.model.BlueskyPostException
import app.pane.android.domain.model.OfflineException
import app.pane.android.domain.model.PrivateGroupException
import app.pane.android.domain.model.SourceFailure
import app.pane.android.domain.model.StoryUnavailableException
import app.pane.android.ui.model.ViewerUiState
import app.pane.android.ui.viewer.OpenFailureKind
import app.pane.android.ui.viewer.failureCopyRes
import app.pane.android.ui.viewer.failureOffersRetry
import app.pane.android.ui.viewer.viewerStateFor
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ViewerFailureTest {
    @Test
    fun eachSourceFailureMapsToCopyAndRetry() {
        val url = "https://www.reddit.com/r/pics/comments/abc/title/"
        val cases = listOf(
            SourceFailure.Unsupported(url) to null,
            SourceFailure.Private() to OpenFailureKind.Private,
            SourceFailure.Private(SourceFailure.PrivateReason.Group) to OpenFailureKind.PrivateGroup,
            SourceFailure.Gone() to OpenFailureKind.Expired,
            SourceFailure.Gone(bluesky = SourceFailure.BlueskyKind.Gone) to OpenFailureKind.BlueskyGone,
            SourceFailure.Gone(bluesky = SourceFailure.BlueskyKind.Hidden) to OpenFailureKind.BlueskyHidden,
            SourceFailure.Gone(bluesky = SourceFailure.BlueskyKind.SignedIn) to OpenFailureKind.BlueskySignedIn,
            SourceFailure.StoryUnavailable to OpenFailureKind.Story,
            SourceFailure.Offline() to OpenFailureKind.Offline,
            SourceFailure.Network() to OpenFailureKind.Network,
            SourceFailure.Parse() to OpenFailureKind.Network,
        )
        cases.forEach { (failure, kind) ->
            val state = viewerStateFor(url, failure)
            if (kind == null) {
                assertTrue(state is ViewerUiState.Unavailable)
                assertFalse(failureOffersRetry(state))
            } else {
                val failed = state as ViewerUiState.LoadFailed
                assertEquals(kind, failed.reason)
                assertEquals(kind == OpenFailureKind.Private || kind == OpenFailureKind.Expired ||
                    kind == OpenFailureKind.Story || kind == OpenFailureKind.Network ||
                    kind == OpenFailureKind.Offline, failureOffersRetry(state))
                failureCopyRes(kind)
            }
        }
    }

    @Test
    fun anUnclassifiedIllegalArgumentExceptionIsARetryableNetworkError() {
        val state = viewerStateFor(
            "https://www.reddit.com/r/interestingasfuck",
            IllegalArgumentException("Unsupported link"),
        )
        val failed = state as ViewerUiState.LoadFailed
        assertEquals(OpenFailureKind.Network, failed.reason)
        assertEquals(R.string.reason_network, failureCopyRes(failed.reason))
        assertTrue(failureOffersRetry(state))
    }

    @Test
    fun aNetworkFailureWhoseMessageSaysRemovedOrPrivateStaysNetwork() {
        val removed = viewerStateFor("https://example.com/post", SourceFailure.Network(message = "This post was removed"))
        val priv = viewerStateFor("https://example.com/post", SourceFailure.Network(message = "private and blocked"))
        assertEquals(OpenFailureKind.Network, (removed as ViewerUiState.LoadFailed).reason)
        assertEquals(OpenFailureKind.Network, (priv as ViewerUiState.LoadFailed).reason)
        assertTrue(failureOffersRetry(removed))
    }

    @Test
    fun platformExceptionsMapByType() {
        val url = "https://www.reddit.com/r/hiking/comments/samplehike/first_solo/"
        assertEquals(
            OpenFailureKind.Offline,
            (viewerStateFor(url, UnknownHostException("no address")) as ViewerUiState.LoadFailed).reason,
        )
        assertEquals(
            OpenFailureKind.Offline,
            (viewerStateFor(url, ConnectException("connection refused")) as ViewerUiState.LoadFailed).reason,
        )
        assertEquals(
            OpenFailureKind.Network,
            (viewerStateFor(url, SocketTimeoutException("timed out")) as ViewerUiState.LoadFailed).reason,
        )
        assertEquals(
            OpenFailureKind.Network,
            (viewerStateFor(url, IOException("Reddit blocked the request (HTTP 403)")) as ViewerUiState.LoadFailed).reason,
        )
        assertEquals(
            OpenFailureKind.Network,
            (viewerStateFor(url, RuntimeException("<html>removed private 500</html>")) as ViewerUiState.LoadFailed).reason,
        )
    }

    @Test
    fun existingTypedExceptionsKeepTheirCopy() {
        val url = "https://www.facebook.com/share/p/14tZoPM9kTY/"
        assertEquals(OpenFailureKind.Offline, (viewerStateFor(url, OfflineException()) as ViewerUiState.LoadFailed).reason)
        assertEquals(OpenFailureKind.Story, (viewerStateFor(url, StoryUnavailableException()) as ViewerUiState.LoadFailed).reason)
        assertEquals(OpenFailureKind.PrivateGroup, (viewerStateFor(url, PrivateGroupException()) as ViewerUiState.LoadFailed).reason)
        assertEquals(
            OpenFailureKind.BlueskyGone,
            (viewerStateFor(url, BlueskyPostException(BlueskyPostException.Kind.Gone)) as ViewerUiState.LoadFailed).reason,
        )
        assertEquals(
            OpenFailureKind.BlueskyHidden,
            (viewerStateFor(url, BlueskyPostException(BlueskyPostException.Kind.Hidden)) as ViewerUiState.LoadFailed).reason,
        )
        assertEquals(
            OpenFailureKind.BlueskySignedIn,
            (viewerStateFor(url, BlueskyPostException(BlueskyPostException.Kind.SignedIn)) as ViewerUiState.LoadFailed).reason,
        )
        assertEquals(R.string.youre_offline, failureCopyRes(OpenFailureKind.Offline))
        assertEquals(R.string.reason_story, failureCopyRes(OpenFailureKind.Story))
        assertEquals(R.string.private_group_title, failureCopyRes(OpenFailureKind.PrivateGroup))
        assertEquals(R.string.bs_gone_title, failureCopyRes(OpenFailureKind.BlueskyGone))
        assertEquals(R.string.bs_hidden_title, failureCopyRes(OpenFailureKind.BlueskyHidden))
        assertEquals(R.string.bs_signed_in_title, failureCopyRes(OpenFailureKind.BlueskySignedIn))
        assertEquals(R.string.reason_private, failureCopyRes(OpenFailureKind.Private))
        assertEquals(R.string.reason_expired, failureCopyRes(OpenFailureKind.Expired))
        assertFalse(failureOffersRetry(viewerStateFor(url, PrivateGroupException())))
        assertFalse(failureOffersRetry(viewerStateFor(url, BlueskyPostException(BlueskyPostException.Kind.Gone))))
    }
}
