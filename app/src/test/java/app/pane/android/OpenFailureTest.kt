package app.pane.android

import app.pane.android.data.net.httpFailure
import app.pane.android.data.net.loginWallFailure
import app.pane.android.domain.model.SourceFailure
import app.pane.android.ui.model.ViewerUiState
import app.pane.android.ui.viewer.OpenFailureKind
import app.pane.android.ui.viewer.viewerStateFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenFailureTest {
    @Test
    fun httpStatusAndLoginWallsClassifyWithoutReadingTheMessage() {
        assertTrue(httpFailure(401, "Please log in") is SourceFailure.Private)
        assertTrue(httpFailure(403, "HTTP 403") is SourceFailure.Private)
        assertTrue(httpFailure(404, "This post was removed") is SourceFailure.Gone)
        assertTrue(httpFailure(410, "status 410") is SourceFailure.Gone)
        assertTrue(httpFailure(429, "timeout talking to the source") is SourceFailure.Network)
        assertTrue(httpFailure(502, "<html><body>502 Bad Gateway</body></html>") is SourceFailure.Network)
        assertEquals("Please log in to view this post", loginWallFailure("Please log in to view this post").message)
        val login = viewerStateFor("https://www.facebook.com/login", loginWallFailure()) as ViewerUiState.LoadFailed
        assertEquals(OpenFailureKind.Private, login.reason)
        val missing = viewerStateFor("https://example.com/post", httpFailure(404, "removed")) as ViewerUiState.LoadFailed
        assertEquals(OpenFailureKind.Expired, missing.reason)
        val blocked = viewerStateFor("https://example.com/post", httpFailure(403, "blocked")) as ViewerUiState.LoadFailed
        assertEquals(OpenFailureKind.Private, blocked.reason)
    }

    @Test
    fun unsupportedUrlsAreTheOnlyUnavailableScreen() {
        val state = viewerStateFor(
            "https://example.com/not-a-post",
            SourceFailure.Unsupported("https://example.com/not-a-post"),
        )
        assertTrue(state is ViewerUiState.Unavailable)
    }
}
