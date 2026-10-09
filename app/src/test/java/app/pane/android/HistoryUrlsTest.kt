package app.pane.android

import app.pane.android.data.history.HistoryUrls
import app.pane.android.domain.model.SourceApp
import org.junit.Assert.assertEquals
import org.junit.Test

class HistoryUrlsTest {
    @Test
    fun postUrlsCollapseToTheExistingCanonicalForm() {
        assertEquals(
            "https://x.com/i/status/20",
            HistoryUrls.canonical("https://twitter.com/jack/status/20?s=20"),
        )
        assertEquals(
            "https://x.com/i/status/20",
            HistoryUrls.canonical("https://x.com/i/status/20"),
        )
        assertEquals(
            "https://www.reddit.com/comments/abc123/",
            HistoryUrls.canonical("https://www.reddit.com/r/pics/comments/abc123/title/"),
        )
        assertEquals(
            "https://www.reddit.com/comments/abc123/",
            HistoryUrls.canonical("https://redd.it/abc123"),
        )
        assertEquals(
            "https://www.instagram.com/reel/abc123/",
            HistoryUrls.canonical("https://m.instagram.com/reel/abc123/?igsh=1"),
        )
        assertEquals(
            "https://x.com/i/status/20",
            HistoryUrls.canonical("https://l.facebook.com/l.php?u=https%3A%2F%2Fx.com%2Fjack%2Fstatus%2F20"),
        )
    }

    @Test
    fun sourceLabelsFollowTheCanonicalHost() {
        assertEquals("X", HistoryUrls.sourceLabel("https://twitter.com/jack/status/20"))
        assertEquals("Reddit", HistoryUrls.sourceLabel("https://redd.it/abc123"))
        assertEquals("Instagram", HistoryUrls.sourceLabel("https://www.instagram.com/p/abc123/"))
    }

    @Test
    fun sourceAppBackfillMapsTheKnownAppsAndLeavesTheRestOther() {
        assertEquals(SourceApp.X, HistoryUrls.sourceApp("https://twitter.com/ada/status/20"))
        assertEquals(SourceApp.Reddit, HistoryUrls.sourceApp("https://redd.it/abc123"))
        assertEquals(SourceApp.Facebook, HistoryUrls.sourceApp("https://www.facebook.com/zuck/posts/pfbid0123"))
        assertEquals(SourceApp.Instagram, HistoryUrls.sourceApp("https://www.instagram.com/reel/abc123/"))
        assertEquals(SourceApp.Threads, HistoryUrls.sourceApp("https://www.threads.net/@zuck/post/abc"))
        assertEquals(SourceApp.Other, HistoryUrls.sourceApp("https://example.com/story"))
        assertEquals(SourceApp.X, HistoryUrls.sourceApp("https://example.com/x", hint = "Twitter"))
    }
}
