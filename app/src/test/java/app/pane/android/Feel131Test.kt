package app.pane.android

import app.pane.android.data.x.XRichText
import app.pane.android.data.x.XSyndication
import app.pane.android.domain.model.HistoryEntry
import app.pane.android.domain.model.HistoryLedger
import app.pane.android.domain.model.HistoryQuery
import app.pane.android.domain.model.NetworkSignals
import app.pane.android.domain.model.OfflineException
import app.pane.android.domain.model.RowTitles
import app.pane.android.domain.model.SourceApp
import app.pane.android.domain.model.YouTubeRowCopy
import app.pane.android.ui.history.HistoryPresenter
import app.pane.android.ui.history.sourceMark
import app.pane.android.ui.viewer.OpenFailureKind
import app.pane.android.ui.viewer.failureCopyRes
import app.pane.android.ui.viewer.viewerStateFor
import java.time.ZoneOffset
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Feel131Test {
    @Test
    fun nikshepArticleDropsTheBareShortLinkAboveTheCard() {
        val post = XSyndication.parseJson(ARTICLE, STATUS, CANONICAL)
        assertEquals("https://t.co/ZZ89hgP5SU", post?.text)
        val shown = XRichText.displayText(
            text = post!!.text,
            entities = post.urlEntities,
            articleUrl = post.article?.url,
            quoteUrl = post.quote?.url,
            cardUrls = post.linkCards.map { it.url },
        )
        assertFalse(shown.contains("t.co"))
        assertTrue(shown.isBlank())
        assertEquals("the market pays billions for nothing. \$nil is priced like nothing", post.article?.title)
        val title = shown.ifBlank { post.article?.title.orEmpty() }
        assertFalse(title.contains("t.co") || title.startsWith("http"))
    }

    @Test
    fun anArticleOnTheWebPathIsStrippedTheSameWay() {
        val rich = XRichText.present(Json.parseToJsonElement(WEB_ARTICLE).jsonObject)
        assertFalse(rich.text.contains("t.co"))
        assertTrue(rich.text.isBlank())
        assertEquals("the market pays billions for nothing. \$nil is priced like nothing", rich.article?.title)
    }

    @Test
    fun recentsTitleNeverKeepsARawShortLink() {
        val title = RowTitles.display(
            title = "https://t.co/ZZ89hgP5SU",
            caption = "https://t.co/ZZ89hgP5SU",
            pageUrl = CANONICAL,
            named = "the market pays billions for nothing. \$nil is priced like nothing",
        )
        assertEquals("the market pays billions for nothing. \$nil is priced like nothing", title)
        assertEquals("x.com", RowTitles.display("https://t.co/ZZ89hgP5SU", "", CANONICAL))
        assertFalse(title.contains("t.co"))
    }

    @Test
    fun youtubeRowsUseTheChannelAndARealTitle() {
        val entry = HistoryEntry(
            url = "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
            source = "YouTube",
            sourceApp = SourceApp.YouTube,
            title = "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
            authorName = "",
            handle = "",
            caption = "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
            thumbUrl = null,
            pfpUrl = null,
            pinnedThumbPath = null,
            pinnedPfpPath = null,
            mediaType = HistoryLedger.NONE,
            note = null,
            firstViewedAt = 1L,
            lastViewedAt = 1L,
            viewCount = 1,
            starredAt = null,
        )
        val row = HistoryPresenter.present(listOf(entry), HistoryQuery(), 1L, ZoneOffset.UTC)
            .sections.single().rows.single()
        assertEquals("YouTube video dQw4w9WgXcQ", row.title)
        assertEquals("", row.identity)
        assertTrue(row.markAsAvatar)
        assertNull(row.pfp)
        assertEquals(R.drawable.ic_source_youtube, sourceMark(SourceApp.YouTube))
        assertEquals("youtube.com · youtu.be/dQw4w9WgXcQ", YouTubeRowCopy.hostLine("dQw4w9WgXcQ"))

        val named = entry.copy(title = "Never Gonna Give You Up", authorName = "Rick Astley", pfpUrl = "https://example.com/a.jpg")
        val known = HistoryPresenter.present(listOf(named), HistoryQuery(), 1L, ZoneOffset.UTC)
            .sections.single().rows.single()
        assertEquals("Never Gonna Give You Up", known.title)
        assertEquals("Rick Astley", known.identity)
        assertFalse(known.markAsAvatar)
        assertFalse(known.title.contains("·") && known.identity.isBlank())
    }

    @Test
    fun noValidatedNetworkIsOffline() {
        assertFalse(NetworkSignals.online(hasDefaultNetwork = false, internet = false, validated = false))
        assertFalse(NetworkSignals.online(hasDefaultNetwork = true, internet = true, validated = false))
        assertTrue(NetworkSignals.online(hasDefaultNetwork = true, internet = true, validated = true))
        assertEquals(OpenFailureKind.Offline, viewerStateFor("https://www.reddit.com/r/x", OfflineException()).let {
            (it as app.pane.android.ui.model.ViewerUiState.LoadFailed).reason
        })
        assertEquals(R.string.youre_offline, failureCopyRes(OpenFailureKind.Offline))
    }

    private companion object {
        const val STATUS = "2108198153271038284"
        const val CANONICAL = "https://x.com/nikshepsvn/status/2108198153271038284"
        val ARTICLE = """
            {
              "id_str": "$STATUS",
              "text": "https://t.co/ZZ89hgP5SU",
              "user": {"name": "Nik", "screen_name": "nikshepsvn"},
              "article": {
                "id": "2108194886424088577",
                "title": "the market pays billions for nothing. ${'$'}nil is priced like nothing",
                "preview_text": "disclosure up top: i hold a massive long position",
                "cover_media": {"media_info": {"original_img_url": "https://pbs.twimg.com/media/HUHQMe5acAAnYSy.png"}},
                "content": {"blocks": [{"text": "full article body"}]}
              },
              "entities": {
                "urls": [{
                  "url": "https://t.co/ZZ89hgP5SU",
                  "expanded_url": "https://x.com/i/article/2108194886424088577",
                  "display_url": "x.com/i/article/2108…"
                }]
              }
            }
        """.trimIndent()

        val WEB_ARTICLE = ARTICLE.replace(
            "https://x.com/i/article/2108194886424088577",
            "https://x.com/i/web/status/$STATUS",
        )
    }
}
