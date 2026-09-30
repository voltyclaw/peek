package app.pane.android

import app.pane.android.ui.model.ViewerUiState
import app.pane.android.ui.viewer.viewerStateFor
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ViewerFailureTest {
    @Test
    fun unsupportedUrlsStayOnTheUnsupportedScreen() {
        val state = viewerStateFor(
            "https://www.reddit.com/r/interestingasfuck",
            IllegalArgumentException("Unsupported link"),
        )

        assertTrue(state is ViewerUiState.Unavailable)
    }

    @Test
    fun aBlockedRedditFetchIsNotReportedAsUnsupported() {
        val url = "https://www.reddit.com/r/interestingasfuck/comments/1w3fcl7/in_1960_david_latimer_planted_a_garden_inside_of/"
        val state = viewerStateFor(url, IOException("Reddit blocked the request (HTTP 403)"))

        val failed = state as ViewerUiState.LoadFailed
        assertEquals(url, failed.url)
        assertEquals("Reddit blocked the request (HTTP 403)", failed.reason)
    }
}
