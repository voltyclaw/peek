package app.pane.android

import app.pane.android.domain.model.StoryUnavailableException
import app.pane.android.ui.model.ViewerUiState
import app.pane.android.ui.viewer.OpenFailureKind
import app.pane.android.ui.viewer.openFailureKind
import app.pane.android.ui.viewer.viewerStateFor
import app.pane.android.R
import java.net.UnknownHostException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenFailureTest {
    @Test
    fun mapsLoginWallsExpiredLinksAndEverythingElse() {
        assertEquals(OpenFailureKind.Private, openFailureKind("Please log in to view this post"))
        assertEquals(OpenFailureKind.Private, openFailureKind("HTTP 403"))
        assertEquals(OpenFailureKind.Expired, openFailureKind("This post was removed"))
        assertEquals(OpenFailureKind.Expired, openFailureKind("status 404"))
        assertEquals(OpenFailureKind.Network, openFailureKind("timeout talking to the source"))
        assertEquals(OpenFailureKind.Network, openFailureKind("<html><body>502 Bad Gateway</body></html>"))
        assertEquals(OpenFailureKind.Story, openFailureKind("Story unavailable"))
    }

    @Test
    fun aStoryThatCannotBeFetchedKeepsTheStoryReason() {
        val state = viewerStateFor(
            "https://www.facebook.com/stories/1/2",
            StoryUnavailableException(),
        ) as ViewerUiState.LoadFailed
        assertEquals(OpenFailureKind.Story.name, state.reason)
        assertEquals(R.string.reason_story, app.pane.android.ui.viewer.failureCopyRes(state.reason))
    }

    @Test
    fun loadFailedStateKeepsTheFriendlyKindAndDropsTheRawMessage() {
        val html = "<!DOCTYPE html><title>Error 500</title>"
        val state = viewerStateFor("https://example.com/post", RuntimeException(html)) as ViewerUiState.LoadFailed
        assertEquals(OpenFailureKind.Network.name, state.reason)
        assertFalse(state.reason.contains("<"))
        assertFalse(state.reason.contains("500"))
    }

    @Test
    fun anOfflineKnownPostIsARetryableNetworkFailure() {
        val url = "https://www.reddit.com/r/hiking/comments/samplehike/first_solo/"
        val state = viewerStateFor(
            url,
            UnknownHostException("Unable to resolve host \"www.reddit.com\": No address associated with hostname"),
        ) as ViewerUiState.LoadFailed
        assertEquals(OpenFailureKind.Network.name, state.reason)
        assertEquals(R.string.reason_network, app.pane.android.ui.viewer.failureCopyRes(state.reason))
        assertTrue(app.pane.android.ui.viewer.failureOffersRetry(state))
        assertFalse(state.reason.contains("single"))
    }

    @Test
    fun aClassificationMissIsNotARetryableNetworkFailure() {
        val state = viewerStateFor(
            "https://example.com/not-a-post",
            IllegalArgumentException("Unsupported link"),
        )
        assertTrue(state is ViewerUiState.Unavailable)
        assertFalse(app.pane.android.ui.viewer.failureOffersRetry(state))
    }
}
