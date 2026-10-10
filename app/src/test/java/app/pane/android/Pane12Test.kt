package app.pane.android

import app.pane.android.R
import app.pane.android.data.facebook.FacebookGroups
import app.pane.android.data.x.XThreadMedia
import app.pane.android.data.youtube.YouTubeWebOrigins
import app.pane.android.domain.model.PrivateGroupException
import app.pane.android.domain.model.SourceApp
import app.pane.android.domain.model.keepText
import app.pane.android.domain.model.looksLikeUrl
import app.pane.android.domain.source.ConsentLine
import app.pane.android.domain.source.OpenRoute
import app.pane.android.domain.source.SourceSwitches
import app.pane.android.domain.source.hasSourceDetail
import app.pane.android.domain.source.openRoute
import app.pane.android.domain.source.secondLine
import app.pane.android.domain.text.LinkLabels
import app.pane.android.domain.tiktok.TikTokWebOrigins
import app.pane.android.ui.components.LedgerSwipe
import app.pane.android.ui.media.MediaGrid
import app.pane.android.ui.media.SessionMute
import app.pane.android.ui.media.ThreadMedia
import app.pane.android.ui.media.ThreadVisibility
import app.pane.android.ui.media.VisibleSlice
import app.pane.android.ui.text.BidiText
import app.pane.android.ui.viewer.OpenFailureKind
import app.pane.android.ui.viewer.failureCopyRes
import app.pane.android.ui.viewer.failureOffersRetry
import app.pane.android.ui.viewer.viewerStateFor
import app.pane.android.domain.model.OtherTitles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Pane12Test {
    @Test
    fun aspectCapsAndGridsFollowTheThreadRules() {
        assertEquals(ThreadMedia.MIN_RATIO, ThreadMedia.aspectCap(9, 16), 0.001f)
        assertEquals(ThreadMedia.MIN_RATIO, ThreadMedia.aspectCap(4, 5), 0.001f)
        assertEquals(1f, ThreadMedia.aspectCap(100, 100), 0.001f)
        assertEquals(1.5f, ThreadMedia.aspectCap(3, 2), 0.001f)
        assertEquals(ThreadMedia.MAX_RATIO, ThreadMedia.aspectCap(16, 9), 0.001f)
        assertEquals(ThreadMedia.MAX_RATIO, ThreadMedia.aspectCap(21, 9), 0.001f)
        assertEquals(1f, ThreadMedia.aspectCap(0, 10), 0.001f)
        assertEquals(MediaGrid.None, ThreadMedia.grid(0))
        assertEquals(MediaGrid.Single, ThreadMedia.grid(1))
        assertEquals(MediaGrid.Pair, ThreadMedia.grid(2))
        assertEquals(MediaGrid.Feature, ThreadMedia.grid(3))
        assertEquals(MediaGrid.Quad, ThreadMedia.grid(4))
        assertEquals(MediaGrid.Quad, ThreadMedia.grid(9))
        assertEquals(4, ThreadMedia.shownCount(9))
        assertEquals(3, ThreadMedia.shownCount(3))
    }

    @Test
    fun theMostVisibleItemPlaysAndAManualTapHoldsUntilItLeaves() {
        val low = VisibleSlice("a", 0.2f, 0)
        val high = VisibleSlice("b", 0.8f, 40)
        val tied = VisibleSlice("c", 0.8f, 10)
        assertNull(ThreadVisibility.winner(listOf(low), null))
        assertEquals("c", ThreadVisibility.winner(listOf(high, tied), null))
        assertEquals("a", ThreadVisibility.winner(listOf(VisibleSlice("a", 0.6f, 0), high), "a"))
        assertEquals("b", ThreadVisibility.winner(listOf(VisibleSlice("a", 0.4f, 0), high), "a"))
        assertEquals(false, SessionMute(true).muted.let { SessionMute(it).toggle().muted })
    }

    @Test
    fun emptyOrPunctuationLinkTextUsesTheDomain() {
        assertEquals("example.com", LinkLabels.display("", "https://example.com/notes"))
        assertEquals("example.com", LinkLabels.display("...", "https://www.example.com/notes"))
        assertEquals("example.com/notes", LinkLabels.display("example.com/notes", "https://example.com/notes"))
        assertTrue(LinkLabels.punctuationOnly("."))
        assertFalse(LinkLabels.punctuationOnly("notes"))
    }

    @Test
    fun hebrewPunctuationStaysInsideItsIsolate() {
        val sentence = BidiText.isolate("שלום.")
        assertEquals("${BidiText.FSI}שלום.${BidiText.PDI}", sentence)
        val mixed = BidiText.join(listOf("@pane", "שלום."))
        assertTrue(mixed.startsWith(BidiText.FSI))
        assertTrue(mixed.contains("@pane"))
        assertTrue(mixed.contains("שלום."))
        assertTrue(mixed.endsWith(BidiText.PDI))
    }

    @Test
    fun aSwipeCommitsOnReleaseOnlyPastTheThresholdOrAFling() {
        val width = 1000f
        val density = 2f
        assertFalse(LedgerSwipe.commits(440f, width, 0f, density))
        assertTrue(LedgerSwipe.commits(450f, width, 0f, density))
        assertTrue(LedgerSwipe.commits(-250f, width, -800f * density, density))
        assertFalse(LedgerSwipe.commits(-250f, width, 800f * density, density))
        assertFalse(LedgerSwipe.commits(240f, width, 2_000f, density))
        assertFalse(LedgerSwipe.commits(100f, width, 10f, density))
    }

    @Test
    fun showInPaneStartsOnAndOffHandsTheLinkOut() {
        assertTrue(SourceSwitches.shown(emptyMap(), SourceApp.YouTube))
        assertFalse(SourceSwitches.handsOff(SourceApp.TikTok, emptyMap()))
        val off = mapOf(SourceSwitches.key(SourceApp.X) to false)
        assertTrue(SourceSwitches.handsOff(SourceApp.X, off))
        assertEquals(OpenRoute.Handoff, openRoute(SourceApp.X, off, profile = false))
        assertEquals(OpenRoute.Viewer, openRoute(SourceApp.X, emptyMap(), profile = false))
        assertEquals(OpenRoute.Handoff, openRoute(SourceApp.Facebook, emptyMap(), profile = true))
        val stored = mutableMapOf<String, Any?>("policy_accepted_version" to "2026-10-09", "youtube_in_pane" to false)
        SourceSwitches.migrate(stored)
        assertEquals("2026-10-09", stored["policy_accepted_version"])
        assertFalse(stored.containsKey("youtube_in_pane"))
        SourceSwitches.rows.forEach { assertEquals(true, stored[SourceSwitches.key(it)]) }
    }

    @Test
    fun embedSecondLinesUseAgreedAllowedOrAsks() {
        assertEquals(ConsentLine.Asks, secondLine(SourceApp.YouTube, null, null)?.status)
        assertEquals(ConsentLine.Agreed, secondLine(SourceApp.YouTube, 1_700_000_000_000L, null)?.status)
        assertEquals(ConsentLine.Asks, secondLine(SourceApp.TikTok, 0L, null)?.status)
        assertEquals(ConsentLine.Allowed, secondLine(SourceApp.Instagram, null, 50L)?.status)
        assertEquals(ConsentLine.Asks, secondLine(SourceApp.Instagram, null, null)?.status)
        assertNull(secondLine(SourceApp.X, 10L, 10L))
        assertNull(secondLine(SourceApp.Facebook, 10L, 10L))
        assertTrue(hasSourceDetail(SourceApp.YouTube))
        assertFalse(hasSourceDetail(SourceApp.X))
    }

    @Test
    fun withdrawOriginsStayDisjointAndDoNotClearEveryWebView() {
        assertFalse(YouTubeWebOrigins.CLEARS_ALL_WEBVIEW_STORAGE)
        assertFalse(TikTokWebOrigins.CLEARS_ALL_WEBVIEW_STORAGE)
        assertTrue(YouTubeWebOrigins.PAGES.contains("https://www.youtube.com"))
        assertTrue(TikTokWebOrigins.PAGES.contains("https://www.tiktok.com"))
        assertTrue(YouTubeWebOrigins.PAGES.intersect(TikTokWebOrigins.PAGES.toSet()).isEmpty())
    }

    @Test
    fun starringKeepsARealTitleWhenTheIncomingCopyIsAUrl() {
        assertTrue(looksLikeUrl("https://example.com/a"))
        assertEquals("A real title", keepText("https://example.com/a", "A real title"))
        assertEquals("A real title", keepText("", "A real title"))
        assertEquals("Kept", keepText("Kept", "Old"))
        assertEquals("example.com", OtherTitles.displayTitle("https://example.com/a", "", "https://example.com/a"))
        assertEquals(
            "https://www.google.com/s2/favicons?domain=example.com&sz=64",
            OtherTitles.faviconUrl("https://www.example.com/a"),
        )
    }

    @Test
    fun aGroupAboutPageIsAStandInAndAPublicPostIsNot() {
        val requested = "https://www.facebook.com/share/p/14tZoPM9kTY/"
        val group = """
            <html><head>
            <meta property="og:url" content="https://www.facebook.com/groups/12345">
            <meta property="og:type" content="website">
            <meta property="og:description" content="the group blurb">
            <link rel="canonical" href="https://www.facebook.com/groups/12345">
            </head></html>
        """.trimIndent()
        assertTrue(FacebookGroups.isStandIn(group, requested, "https://www.facebook.com/groups/12345"))
        val publicPost = """
            <html><head>
            <meta property="og:url" content="https://www.facebook.com/groups/12345/permalink/999888">
            <meta property="og:type" content="article">
            </head></html>
        """.trimIndent()
        assertFalse(
            FacebookGroups.isStandIn(
                publicPost,
                "https://www.facebook.com/groups/12345/permalink/999888",
                "https://www.facebook.com/groups/12345/permalink/999888",
            ),
        )
        val pagePost = """
            <html><head>
            <meta property="og:url" content="https://www.facebook.com/somepage/posts/999888">
            <meta property="og:type" content="article">
            </head></html>
        """.trimIndent()
        assertFalse(FacebookGroups.isStandIn(pagePost, "https://www.facebook.com/somepage/posts/999888", pagePost))
        val login = "https://www.facebook.com/login/?next=" +
            "https%3A%2F%2Fwww.facebook.com%2Fgroups%2F12345%2Fpermalink%2F999888%2F"
        assertTrue(FacebookGroups.isStandIn("<html>login_form</html>", requested, login))
    }

    @Test
    fun aPrivateGroupFailureDoesNotOfferRetry() {
        val state = viewerStateFor("https://www.facebook.com/share/p/14tZoPM9kTY/", PrivateGroupException())
        assertFalse(failureOffersRetry(state))
        assertEquals(R.string.private_group_title, failureCopyRes(OpenFailureKind.PrivateGroup))
    }

    @Test
    fun threadHtmlKeepsPostMediaAndSkipsProfileImages() {
        val slice = """
            {"media_url_https":"https://pbs.twimg.com/profile_images/a.jpg","media_url_https":"https://pbs.twimg.com/media/photo.jpg","original_info":{"width":1200,"height":800}}
        """.trimIndent()
        val items = XThreadMedia.parse(slice)
        assertEquals(1, items.size)
        assertEquals("https://pbs.twimg.com/media/photo.jpg", items.single().imageUrl)
        assertEquals(1200, items.single().width)
        assertEquals(800, items.single().height)
    }
}
