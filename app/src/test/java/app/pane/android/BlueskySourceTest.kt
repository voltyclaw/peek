package app.pane.android

import app.pane.android.data.bluesky.BskyFacetMark
import app.pane.android.data.bluesky.BskyFacets
import app.pane.android.data.bluesky.BskyHttp
import app.pane.android.data.bluesky.BskyLabels
import app.pane.android.data.bluesky.BskyLabel
import app.pane.android.data.bluesky.BskyMediaGate
import app.pane.android.data.bluesky.BskyPostGate
import app.pane.android.data.bluesky.BskyThread
import app.pane.android.data.bluesky.BskyTransport
import app.pane.android.data.bluesky.BskyUrls
import app.pane.android.data.bluesky.BlueskyLinkContentRepository
import app.pane.android.data.webview.SiteClearPath
import app.pane.android.data.webview.siteClearPath
import app.pane.android.data.webview.withdrawClearsAllSiteData
import app.pane.android.domain.model.BlueskyMetadata
import app.pane.android.domain.model.BlueskyPostException
import app.pane.android.domain.model.LinkSource
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BlueskySourceTest {
    @Test
    fun parsesPostUrls() {
        val https = BskyUrls.parsePost("https://bsky.app/profile/nytimes.com/post/abc123?utm=1")
        assertEquals("nytimes.com", https?.actor)
        assertEquals("abc123", https?.rkey)
        assertEquals("at://nytimes.com/app.bsky.feed.post/abc123", https?.atUri)
        val did = BskyUrls.parsePost("https://bsky.app/profile/did:plc:abcd/post/rkey1")
        assertEquals("did:plc:abcd", did?.actor)
        assertEquals("https://bsky.app/profile/did:plc:abcd/post/rkey1", did?.https())
        val at = BskyUrls.parsePost("at://did:plc:abcd/app.bsky.feed.post/rkey1")
        assertEquals("rkey1", at?.rkey)
        val embed = BskyUrls.parsePost("https://embed.bsky.app/embed/did:plc:abcd/app.bsky.feed.post/rkey1")
        assertEquals("did:plc:abcd", embed?.actor)
        assertNull(BskyUrls.parsePost("https://bsky.app/profile/nytimes.com"))
        assertTrue(BskyUrls.isProfile("https://bsky.app/profile/nytimes.com"))
        assertFalse(BskyUrls.isProfile("https://bsky.app/profile/nytimes.com/post/abc123"))
    }

    @Test
    fun facetByteOffsetsSurviveEmoji() {
        val text = "hi 👍 x"
        val thumbsUp = text.indexOf("👍")
        assertEquals(2, Character.charCount(text.codePointAt(thumbsUp)))
        val after = BskyFacets.utf8ToUtf16(text, 3 + 4)
        assertEquals(thumbsUp + 2, after)
        val spans = BskyFacets.spans(text, listOf(BskyFacetMark(afterBytes(text, "x"), afterBytes(text, "x") + 1, "https://example.com/a")))
        assertEquals("x", text.substring(spans.single().start, spans.single().end))
        assertEquals("example.com", BskyFacets.linkLabel(".", app.pane.android.domain.model.BskyTextSpan(0, 1, "https://example.com/a")))
    }

    @Test
    fun labelsHideAdultMediaAndRequireSignIn() {
        val adult = BskyLabels.decide(listOf(BskyLabel("porn")), emptyList(), "did:plc:a")
        assertEquals(BskyMediaGate.HideAdult, adult.media)
        assertEquals(BskyPostGate.Show, adult.post)
        val graphic = BskyLabels.decide(listOf(BskyLabel("graphic-media")), emptyList(), "did:plc:a")
        assertEquals(BskyMediaGate.HideGraphic, graphic.media)
        val nudity = BskyLabels.decide(listOf(BskyLabel("nudity")), emptyList(), "did:plc:a")
        assertEquals(BskyMediaGate.Blur, nudity.media)
        val signedOut = BskyLabels.decide(
            emptyList(),
            listOf(BskyLabel("!no-unauthenticated", src = "did:plc:a")),
            "did:plc:a",
        )
        assertEquals(BskyPostGate.SignedOut, signedOut.post)
        val otherSrc = BskyLabels.decide(
            emptyList(),
            listOf(BskyLabel("!no-unauthenticated", src = "did:plc:other")),
            "did:plc:a",
        )
        assertEquals(BskyPostGate.Show, otherSrc.post)
        val negated = BskyLabels.decide(listOf(BskyLabel("porn", neg = true)), emptyList(), "did:plc:a")
        assertEquals(BskyMediaGate.Show, negated.media)
        val hidden = BskyLabels.decide(listOf(BskyLabel("!hide")), emptyList(), "did:plc:a")
        assertEquals(BskyPostGate.Hide, hidden.post)
        val avatar = BskyLabels.decide(emptyList(), listOf(BskyLabel("sexual")), "did:plc:a")
        assertTrue(avatar.avatarHidden)
        val custom = BskyLabels.decide(listOf(BskyLabel("custom-label")), emptyList(), "did:plc:a")
        assertEquals(BskyPostGate.Show, custom.post)
    }

    @Test
    fun parsesVideoGifQuoteAndDropsAuthorReply() {
        val body = """
            {
              "thread": {
                "${'$'}type": "app.bsky.feed.defs#threadViewPost",
                "post": ${post("did:plc:root", "ada.bsky.social", "rootkey", "Hello", 4, """
                  "embed": {
                    "${'$'}type": "app.bsky.embed.video#view",
                    "playlist": "https://video.bsky.app/watch/playlist.m3u8",
                    "thumbnail": "https://video.bsky.app/thumb.jpg",
                    "alt": "clip",
                    "aspectRatio": { "width": 16, "height": 9 },
                    "presentation": "gif"
                  },
                """)},
                "replies": [
                  { "post": ${post("did:plc:root", "ada.bsky.social", "self1", "continued", 0, "", """ "reply": {"parent": {"uri": "at://did:plc:root/app.bsky.feed.post/rootkey"}} """)} },
                  { "post": ${post("did:plc:other", "bob.bsky.social", "reply1", "nice", 0, "")} },
                  { "${'$'}type": "app.bsky.feed.defs#notFoundPost" }
                ]
              }
            }
        """.trimIndent()
        val thread = BskyThread.parse(body)!!
        assertEquals("playlist.m3u8", thread.post.media.single().videoUrl?.substringAfterLast('/'))
        assertTrue(thread.post.media.single().gif)
        assertTrue(thread.post.media.single().videos.single().adaptive)
        assertEquals(2, thread.replies.size)
        val repo = BlueskyLinkContentRepository(BskyTransport { BskyHttp(200, body) }, sleep = {})
        val page = runBlocking { repo.resolve("https://bsky.app/profile/ada.bsky.social/post/rootkey").getOrThrow() }
        assertEquals(LinkSource.Bluesky, page.source)
        assertEquals("https://bsky.app/profile/did:plc:root/post/rootkey", page.url)
        val meta = page.sourceMetadata as BlueskyMetadata
        assertEquals("https://bsky.app/profile/ada.bsky.social/post/rootkey", meta.shareUrl)
        assertEquals(1, page.comments.size)
        assertEquals("nice", page.comments.single().body)
        assertTrue(meta.authorThread.any { it.text == "continued" })
    }

    @Test
    fun tenorIsAGifAndNotFoundStrips() {
        val body = """
            { "thread": { "post": ${post("did:plc:root", "ada.bsky.social", "rootkey", "gif", 0, """
              "embed": {
                "${'$'}type": "app.bsky.embed.external#view",
                "external": {
                  "uri": "https://media.tenor.com/abc.gif",
                  "title": "wave",
                  "thumb": "https://media.tenor.com/abc-thumb.jpg"
                }
              },
            """)} } }
        """.trimIndent()
        val media = BskyThread.parse(body)!!.post.media.single()
        assertTrue(media.gif)
        assertNull(BskyThread.parse(body)!!.post.linkCard)
        val repo = BlueskyLinkContentRepository(
            BskyTransport { BskyHttp(400, """{"error":"NotFound"}""") },
            sleep = {},
        )
        val error = runBlocking { repo.resolve("https://bsky.app/profile/ada.bsky.social/post/rootkey") }.exceptionOrNull()
        assertTrue(error is BlueskyPostException)
        assertEquals(BlueskyPostException.Kind.Gone, (error as BlueskyPostException).kind)
    }

    @Test
    fun profilePathClearsAllSiteDataAndOriginsDoNot() {
        assertEquals(SiteClearPath.Profile, siteClearPath(true))
        assertEquals(SiteClearPath.OriginsPartial, siteClearPath(false))
        assertTrue(withdrawClearsAllSiteData(SiteClearPath.Profile, profileDeleted = true))
        assertFalse(withdrawClearsAllSiteData(SiteClearPath.Profile, profileDeleted = false))
        assertFalse(withdrawClearsAllSiteData(SiteClearPath.OriginsPartial, profileDeleted = true))
        assertFalse(withdrawClearsAllSiteData(SiteClearPath.OriginsFull, profileDeleted = false, idbVerified = true))
        assertEquals(119, app.pane.android.data.webview.EmbedWebProfiles.MIN_WEBVIEW_MAJOR)
    }

    private fun afterBytes(text: String, needle: String): Int {
        val index = text.indexOf(needle)
        var bytes = 0
        var i = 0
        while (i < index) {
            val cp = text.codePointAt(i)
            bytes += when {
                cp <= 0x7F -> 1
                cp <= 0x7FF -> 2
                cp <= 0xFFFF -> 3
                else -> 4
            }
            i += Character.charCount(cp)
        }
        return bytes
    }

    private fun post(
        did: String,
        handle: String,
        rkey: String,
        text: String,
        replies: Int,
        extra: String,
        recordExtra: String = "",
    ): String {
        val recordTail = if (recordExtra.isBlank()) "" else ", $recordExtra"
        return """
        {
          "uri": "at://$did/app.bsky.feed.post/$rkey",
          "author": { "did": "$did", "handle": "$handle", "displayName": "Ada" },
          "replyCount": $replies,
          "record": { "text": "$text", "createdAt": "2026-10-01T00:00:00.000Z"$recordTail },
          $extra
          "labels": []
        }
        """.trimIndent()
    }
}
