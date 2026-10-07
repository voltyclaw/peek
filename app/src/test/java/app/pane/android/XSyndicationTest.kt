package app.pane.android

import app.pane.android.data.x.XConversation
import app.pane.android.data.x.XDirectPageLoader
import app.pane.android.data.x.XPreviewElement
import app.pane.android.data.x.XReplyPage
import app.pane.android.data.x.XSyndication
import app.pane.android.data.x.initialXReplyContinuation
import app.pane.android.data.x.mergeXReplyPage
import app.pane.android.data.x.ParsedXReply
import app.pane.android.domain.model.XReplyContinuation
import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class XSyndicationTest {
    @Test
    fun tokenMatchesTheEmbedPage() {
        assertEquals("6dq1a2xwd93", XSyndication.token("20"))
        assertEquals("2jn1m8zmk1", XSyndication.token("1050118621198921728"))
        assertEquals(
            "4agma7xpol",
            XSyndication.token("1770000000000000000"),
        )
    }

    @Test
    fun readsAPublicSyndicationPost() {
        val body = """
            {
              "id_str": "20",
              "text": "just setting up my twttr",
              "user": {"name": "jack", "screen_name": "jack"},
              "photos": [{"url": "https://pbs.twimg.com/media/a.jpg"}],
              "conversation_count": 3
            }
        """.trimIndent()
        val post = XSyndication.parseJson(body, "20", "https://x.com/i/status/20")
        assertEquals("jack", post?.author)
        assertEquals("just setting up my twttr", post?.text)
        assertEquals(listOf("https://pbs.twimg.com/media/a.jpg"), post?.imageUrls)
        assertEquals(3, post?.commentCount)
    }

    @Test
    fun picksTheHighestBitrateMp4() {
        val body = """
            {
              "text": "clip",
              "user": {"name": "Ada"},
              "video": {
                "poster": "https://pbs.twimg.com/poster.jpg",
                "variants": [
                  {"type": "application/x-mpegURL", "src": "https://video.twimg.com/a.m3u8"},
                  {"content_type": "video/mp4", "url": "https://video.twimg.com/low.mp4", "bitrate": 256000},
                  {"type": "video/mp4", "src": "https://video.twimg.com/high.mp4", "bitrate": 832000}
                ]
              }
            }
        """.trimIndent()
        val post = XSyndication.parseJson(body, "9", "https://x.com/i/status/9")
        assertEquals("https://video.twimg.com/high.mp4", post?.videoUrl)
        assertEquals(
            listOf("https://video.twimg.com/low.mp4", "https://video.twimg.com/high.mp4"),
            post?.videos?.map { it.url },
        )
        assertTrue(post?.imageUrls?.contains("https://pbs.twimg.com/poster.jpg") == true)
    }

    @Test
    fun tombstoneIsNotAPost() {
        val body = """{"__typename":"TweetTombstone","text":"This post is unavailable."}"""
        assertNull(XSyndication.parseJson(body, "1", "https://x.com/i/status/1"))
    }

    @Test
    fun oEmbedStripsTheEmbedHtml() {
        val body = """
            {"author_name":"Ada","html":"<blockquote>Hello &amp; welcome</blockquote> — Ada (@ada)"}
        """.trimIndent()
        val post = XSyndication.parseOEmbed(body, "5", "https://x.com/i/status/5")
        assertEquals("Ada", post?.author)
        assertEquals("Hello & welcome", post?.text)
    }

    @Test
    fun loggedOutStatusHtmlIncludesRepliesBesidesThePost() {
        val html = """
            name:"jack" full_text:"just setting up my twttr" created_at_ms:1142974214000 entry_id:"tweet-20"
            name:"Ada" full_text:"@jack hello there" created_at_ms:1710000000000 entry_id:"conversationthread-8-tweet-8"
            entry_id:"cursor-bottom"
        """.trimIndent()

        val replies = XConversation.parseReplies(html, "20")

        assertEquals(1, replies.size)
        assertEquals("8", replies[0].id)
        assertEquals("Ada", replies[0].author)
        assertEquals("@jack hello there", replies[0].text)
        assertEquals(1710000000000L, replies[0].createdAtEpochMillis)
    }

    @Test
    fun repliesListedAfterTheirEntryIdKeepTheAuthorName() {
        val html = """
            entry_id:"tweet-20" full_text:"the post"
            entry_id:"conversationthread-8-tweet-8" __typename:"TimelineTimelineItem" screen_name:"ada" name:"Ada" full_text:"a public reply" created_at_ms:1710000000000
        """.trimIndent()

        val replies = XConversation.parseReplies(html, "20")

        assertEquals("Ada", replies.single().author)
        assertEquals("a public reply", replies.single().text)
    }

    @Test
    fun jsonAndEscapedReplyPayloadsStillParse() {
        val json = """
            {"entry_id":"conversationthread-8-tweet-8","__typename":"TimelineTimelineItem","screen_name":"ada","name":"Ada","full_text":"json reply","created_at_ms":1710000000000}
        """.trimIndent()
        val escaped = """
            entry_id:\"conversationthread-9-tweet-9\" name:\"Bea\" full_text:\"escaped reply\" created_at_ms:1710000000001
        """.trimIndent()

        assertEquals("json reply", XConversation.parseReplies(json, "20").single().text)
        assertEquals("Ada", XConversation.parseReplies(json, "20").single().author)
        assertEquals("escaped reply", XConversation.parseReplies(escaped, "20").single().text)
        assertEquals("Bea", XConversation.parseReplies(escaped, "20").single().author)
    }

    @Test
    fun aRichPublicPageKeepsEveryEmbeddedReply() {
        val html = buildString {
            append("""entry_id:"tweet-20" full_text:"the post" """)
            repeat(90) { index ->
                val id = 1_000 + index
                append(
                    """entry_id:"conversationthread-$id-tweet-$id" name:"Ada" full_text:"reply $index" created_at_ms:1710000000000 """,
                )
            }
        }

        val replies = XConversation.parseReplies(html, "20")

        assertEquals(90, replies.size)
        assertEquals("reply 0", replies.first().text)
        assertEquals("reply 89", replies.last().text)
    }

    @Test
    fun loaderTriesTheNextStatusPageWhenTheFirstHasNoReplies() = runTest {
        val loader = XDirectPageLoader { url ->
            val body = when {
                url.contains("syndication") -> """
                    {"id_str":"20","text":"hello","user":{"name":"jack"},"conversation_count":1}
                """.trimIndent()
                url.contains("https://x.com/i/status") -> "<html>login wall</html>"
                url.contains("twitter.com/i/status") -> """
                    entry_id:"conversationthread-9-tweet-9" name:"Bea" full_text:"from twitter" created_at_ms:1710000000000
                """.trimIndent()
                else -> """{"author_name":"jack","html":"<p>fallback</p>"}"""
            }
            jsonConnection(body)
        }

        val post = loader.resolve("https://x.com/jack/status/20")

        assertEquals("from twitter", post.replies.single().text)
        assertEquals("Bea", post.replies.single().author)
    }

    @Test
    fun loaderKeepsRepliesFromEveryPublicStatusPage() = runTest {
        val loader = XDirectPageLoader { url ->
            val body = when {
                url.contains("syndication") -> """
                    {"id_str":"20","text":"hello","user":{"name":"jack","screen_name":"jack"},"conversation_count":4}
                """.trimIndent()
                url.contains("https://x.com/i/status") -> """
                    entry_id:"conversationthread-8-tweet-8" name:"Ada" full_text:"from x" created_at_ms:1710000000000
                """.trimIndent()
                url.contains("https://twitter.com/i/status") -> """
                    entry_id:"conversationthread-9-tweet-9" name:"Bea" full_text:"from twitter" created_at_ms:1710000000001
                """.trimIndent()
                else -> "<html>login wall</html>"
            }
            jsonConnection(body)
        }

        val post = loader.resolve("https://x.com/jack/status/20")

        assertEquals(listOf("from x", "from twitter"), post.replies.map { it.text })
    }

    @Test
    fun noteTextIsTheLongerBodyAfterThePreview() {
        val html = """
            full_text:"That's a +225 basis"
            __typename:"NoteTweet" text:"short entity" text:"That's a +225 basis point move and the rest of the note."
        """.trimIndent()

        assertEquals(
            "That's a +225 basis point move and the rest of the note.",
            XConversation.parseNoteText(html),
        )
        assertEquals(
            "That's a +225 basis point move and the rest of the note.",
            XConversation.longerCaption("That's a +225 basis", XConversation.parseNoteText(html)),
        )
    }

    @Test
    fun loaderCopiesTheNoteAndRepliesFromTheStatusPage() = runTest {
        val loader = XDirectPageLoader { url ->
            val body = when {
                url.contains("syndication") -> """
                    {"id_str":"20","text":"That's a +225 basis","user":{"name":"The Kobeissi Letter","screen_name":"KobeissiLetter"},"conversation_count":1}
                """.trimIndent()
                url.contains("/i/status/") -> "<html>login wall</html>"
                url.contains("/KobeissiLetter/status/") -> """
                    __typename:"NoteTweet" text:"That's a +225 basis point move and the rest of the note."
                    entry_id:"conversationthread-8-tweet-8" name:"Ada" full_text:"a public reply" created_at_ms:1710000000000
                """.trimIndent()
                else -> """{"author_name":"jack","html":"<p>fallback</p>"}"""
            }
            jsonConnection(body)
        }

        val post = loader.resolve("https://x.com/KobeissiLetter/status/20")

        assertEquals("That's a +225 basis point move and the rest of the note.", post.text)
        assertEquals("a public reply", post.replies.single().text)
    }

    @Test
    fun loaderAttachesRepliesFromTheStatusPage() = runTest {
        val loader = XDirectPageLoader { url ->
            val body = when {
                url.contains("syndication") -> """
                    {"id_str":"20","text":"just setting up my twttr","user":{"name":"jack"},"conversation_count":2}
                """.trimIndent()
                url.contains("x.com/i/status") -> """
                    full_text:"just setting up my twttr" entry_id:"tweet-20"
                    name:"Ada" full_text:"a public reply" created_at_ms:1710000000000 entry_id:"conversationthread-8-tweet-8"
                """.trimIndent()
                else -> """{"author_name":"jack","html":"<p>fallback</p>"}"""
            }
            jsonConnection(body)
        }

        val post = loader.resolve("https://x.com/jack/status/20")

        assertEquals("just setting up my twttr", post.text)
        assertEquals(2, post.commentCount)
        assertEquals("a public reply", post.replies.single().text)
        assertEquals("Ada", post.replies.single().author)
    }

    @Test
    fun aPublicStatusPageReadsTheAuthorThreadFromTheRoot() {
        val html = checkNotNull(javaClass.classLoader!!.getResource("x-author-thread.html")).readText()
        val openedLast = XConversation.parseAuthorThread(html, "2105362146922000492")

        assertEquals(
            listOf(
                "2105362093482401889",
                "2105362113736712273",
                "2105362129628848525",
                "2105362146922000492",
            ),
            openedLast.posts.map { it.id },
        )
        assertEquals("Aexodus", openedLast.posts.first().author)
        assertEquals("AexodusCapital", openedLast.posts.first().screenName)
        assertTrue(openedLast.posts.first().text.startsWith("Everyone asks who wins the space race."))
        assertTrue(openedLast.posts.last().text.startsWith("On the Silk Road"))
        assertFalse(openedLast.partial)

        val openedMiddle = XConversation.parseAuthorThread(html, "2105362113736712273")
        assertEquals(openedLast.posts.map { it.id }, openedMiddle.posts.map { it.id })
        assertFalse(openedMiddle.partial)
    }

    @Test
    fun aSingleStatusIsNotAnAuthorThread() {
        val html = """
            display_type:"Tweet",tweet_results:{rest_id:"20",core:{name:"jack",screen_name:"jack"},details:{full_text:"just setting up my twttr"}}
        """.trimIndent()

        val thread = XConversation.parseAuthorThread(html, "20")

        assertTrue(thread.posts.isEmpty())
        assertFalse(thread.partial)
    }

    @Test
    fun authorThreadKeepsOnlyTheSameAccountAndMarksAMissingAncestor() {
        val html = """
            display_type:"Tweet",tweet_results:{rest_id:"2",core:{name:"Ada",screen_name:"ada"},details:{full_text:"This continues an earlier post."},reply_to_results:{rest_id:"1"},reply_to_user_results:{screen_name:"ada"}}
            display_type:"Tweet",tweet_results:{rest_id:"8",core:{name:"Bea",screen_name:"bea"},details:{full_text:"Someone else replied."}}
            display_type:"SelfThread",tweet_results:{rest_id:"3",core:{name:"Ada",screen_name:"ada"},details:{full_text:"Ada finishes the chain."},reply_to_results:{rest_id:"2"},reply_to_user_results:{screen_name:"ada"}}
        """.trimIndent()

        val thread = XConversation.parseAuthorThread(html, "3")

        assertEquals(listOf("2", "3"), thread.posts.map { it.id })
        assertEquals("This continues an earlier post.", thread.posts.first().text)
        assertTrue(thread.partial)
    }

    @Test
    fun aReplyToSomeoneElseIsTheStartOfTheAuthorThread() {
        val html = """
            display_type:"Tweet",tweet_results:{rest_id:"2",core:{name:"Ada",screen_name:"ada"},details:{full_text:"Ada answers Bob."},reply_to_results:{rest_id:"9"},reply_to_user_results:{screen_name:"bob"}}
            display_type:"SelfThread",tweet_results:{rest_id:"3",core:{name:"Ada",screen_name:"ada"},details:{full_text:"Ada continues."},reply_to_results:{rest_id:"2"},reply_to_user_results:{screen_name:"ada"}}
        """.trimIndent()

        val thread = XConversation.parseAuthorThread(html, "3")

        assertEquals(listOf("2", "3"), thread.posts.map { it.id })
        assertFalse(thread.partial)
    }

    @Test
    fun loaderKeepsTheAuthorThreadAndLeavesOtherPeoplesReplies() = runTest {
        val html = checkNotNull(javaClass.classLoader!!.getResource("x-author-thread.html")).readText() +
            """ entry_id:"conversationthread-9-tweet-9" name:"Bea" full_text:"a public reply" created_at_ms:1710000000000 """
        val loader = XDirectPageLoader { url ->
            val body = when {
                url.contains("syndication") -> """
                    {"id_str":"2105362146922000492","text":"On the Silk Road","user":{"name":"Aexodus","screen_name":"AexodusCapital"},"conversation_count":2}
                """.trimIndent()
                url.contains("https://x.com/i/status") -> html
                else -> "<html>login wall</html>"
            }
            jsonConnection(body)
        }

        val post = loader.resolve("https://x.com/AexodusCapital/status/2105362146922000492")

        assertEquals(4, post.authorThread.size)
        assertEquals("2105362093482401889", post.authorThread.first().id)
        assertEquals("2105362146922000492", post.authorThread.last().id)
        assertTrue(post.authorThread.last().text.startsWith("On the Silk Road"))
        assertFalse(post.authorThreadPartial)
        assertEquals(listOf("a public reply"), post.replies.map { it.text })
    }

    @Test
    fun loaderPrefersTheLongerAuthorThreadFromAnotherPublicPage() = runTest {
        val short = """
            display_type:"Tweet",tweet_results:{rest_id:"20",core:{name:"Ada",screen_name:"ada"},details:{full_text:"Root of the chain."}}
            display_type:"SelfThread",tweet_results:{rest_id:"21",core:{name:"Ada",screen_name:"ada"},details:{full_text:"Opened in the middle."},reply_to_results:{rest_id:"20"},reply_to_user_results:{screen_name:"ada"}}
        """.trimIndent()
        val longer = short + """
            display_type:"SelfThread",tweet_results:{rest_id:"22",core:{name:"Ada",screen_name:"ada"},details:{full_text:"A later post."},reply_to_results:{rest_id:"21"},reply_to_user_results:{screen_name:"ada"}}
        """.trimIndent()
        val loader = XDirectPageLoader { url ->
            val body = when {
                url.contains("syndication") -> """
                    {"id_str":"21","text":"Opened in the middle.","user":{"name":"Ada","screen_name":"ada"},"conversation_count":0}
                """.trimIndent()
                url.contains("https://x.com/i/status") -> short
                url.contains("https://twitter.com/i/status") -> longer
                else -> "<html>login wall</html>"
            }
            jsonConnection(body)
        }

        val post = loader.resolve("https://x.com/ada/status/21")

        assertEquals(listOf("20", "21", "22"), post.authorThread.map { it.id })
        assertFalse(post.authorThreadPartial)
    }

    @Test
    fun syndicationPreviewArrivesWhileStatusPagesAreStillLoading() = runBlocking {
        val previews = Collections.synchronizedList(mutableListOf<String>())
        val htmlStarted = CountDownLatch(1)
        val releaseHtml = CountDownLatch(1)
        val threadHtml = """
            display_type:"Tweet",tweet_results:{rest_id:"19",core:{name:"jack",screen_name:"jack"},details:{full_text:"Earlier post in the chain."}}
            display_type:"SelfThread",tweet_results:{rest_id:"20",core:{name:"jack",screen_name:"jack"},details:{full_text:"just setting up my twttr"},reply_to_results:{rest_id:"19"},reply_to_user_results:{screen_name:"jack"}}
        """.trimIndent()
        val loader = XDirectPageLoader { url ->
            val body = when {
                url.contains("syndication") -> """
                    {"id_str":"20","text":"just setting up my twttr","user":{"name":"jack","screen_name":"jack"}}
                """.trimIndent()
                else -> {
                    htmlStarted.countDown()
                    check(releaseHtml.await(5, TimeUnit.SECONDS))
                    threadHtml
                }
            }
            jsonConnection(body)
        }

        val deferred = async(Dispatchers.IO + XPreviewElement { previews += it.text }) {
            loader.resolve("https://x.com/jack/status/20")
        }
        assertTrue(htmlStarted.await(5, TimeUnit.SECONDS))
        val deadline = System.currentTimeMillis() + 2_000
        while (previews.isEmpty() && System.currentTimeMillis() < deadline) {
            Thread.sleep(20)
        }
        assertEquals(listOf("just setting up my twttr"), previews.toList())
        releaseHtml.countDown()
        val post = deferred.await()

        assertEquals(listOf("19", "20"), post.authorThread.map { it.id })
        assertEquals("just setting up my twttr", post.text)
    }

    @Test
    fun aNoteTweetKeepsTheBodyPastTheFirstQuoteAndADifferentShortLink() {
        val preview = "Opus 5.5 on https://t.co/RWvZWjo4hs\n---\nOkay. I need a minute.\n\n" +
            "I cloned the thing expecting maybe forty or fifty families, and my reaction went from \"huh\" to \"wait,"
        val note = "Opus 5.5 on https://t.co/YK5THQVTKf\n---\nOkay. I need a minute.\n\n" +
            "I cloned the thing expecting maybe forty or fifty families, and my reaction went from \"huh\" to \"wait, what\" " +
            "in about six lines. Family 003 claims a zero-free half-plane."
        val html = """
            __typename:"NoteTweet" text:"${jsEscape(note)}"
            entry_id:"conversationthread-8-tweet-8" name:"Joshua" full_text:"@cleo lol at the auto suggestion" created_at_ms:1710000000000
            entry_id:"conversationthread-9-tweet-9" name:"Yuhan" full_text:"@cleo &gt; It feels like a lot of lifetimes." created_at_ms:1710000000001
            entry_id:"conversationthread-10-tweet-10" name:"Ada" full_text:"third public reply" created_at_ms:1710000000002
        """.trimIndent()

        assertEquals(note, XConversation.parseNoteText(html))
        assertTrue(XConversation.longerCaption(preview, note).contains("wait, what"))
        assertEquals(">", XConversation.decodeEntities("&gt;"))

        val loader = XDirectPageLoader { url ->
            val body = when {
                url.contains("syndication") -> """
                    {"id_str":"2107618065869574208","text":${jsonString(preview)},"conversation_count":31,"note_tweet":{"id":"note-only"},"user":{"name":"Cleo","screen_name":"ereliuer_eteer","profile_image_url_https":"https://pbs.twimg.com/profile_images/abc_normal.jpg"}}
                """.trimIndent()
                url.contains("/i/status/") -> html
                else -> "<html>login wall</html>"
            }
            jsonConnection(body)
        }

        val post = runBlocking { loader.resolve("https://x.com/ereliuer_eteer/status/2107618065869574208") }

        assertEquals("Cleo", post.author)
        assertEquals("ereliuer_eteer", post.screenName)
        assertEquals("https://pbs.twimg.com/profile_images/abc_200x200.jpg", post.avatarUrl)
        assertTrue(post.text.contains("wait, what"))
        assertTrue(post.text.contains("zero-free half-plane"))
        assertFalse(post.text.endsWith("wait,"))
        assertEquals(31, post.commentCount)
        assertEquals(listOf("Joshua", "Yuhan", "Ada"), post.replies.map { it.author })
        assertEquals("@cleo > It feels like a lot of lifetimes.", post.replies[1].text)
    }

    @Test
    fun repliesOmitTheOpenedStatusEvenWhenAnAncestorEntryCarriesItsText() {
        val html = """
            entry_id:"tweet-100"
            rest_id:"200" name:"Anshu" screen_name:"anshuc" image_url:"https://pbs.twimg.com/profile_images/anshu_normal.jpg" full_text:"the opened post"
            rest_id:"100"
            entry_id:"tweet-200"
            entry_id:"conversationthread-9-tweet-9" rest_id:"9" name:"Happy" screen_name:"happy" image_url:"https://pbs.twimg.com/profile_images/happy_normal.jpg" full_text:"a real reply" created_at_ms:1710000000000
        """.trimIndent()

        val replies = XConversation.parseReplies(html, "200")

        assertEquals(listOf("9"), replies.map { it.id })
        assertEquals("Happy", replies.single().author)
        assertEquals("happy", replies.single().screenName)
        assertEquals("https://pbs.twimg.com/profile_images/happy_200x200.jpg", replies.single().avatarUrl)
        assertEquals("a real reply", replies.single().text)
    }

    @Test
    fun bottomCursorIsReadOnlyWhenTheTimelineHasOne() {
        val html = """__typename:"TimelineTimelineCursor" value:"cursor-next" cursorType:"Bottom""""
        assertEquals("cursor-next", XConversation.parseBottomCursor(html))
        assertNull(XConversation.parseBottomCursor("""__typename:"TimelineTerminateTimeline" direction:"Bottom""""))
    }

    @Test
    fun loaderFollowsABottomCursorAndStopsWhenTheNextPageIsAWall() = runTest {
        val loader = XDirectPageLoader { url ->
            val body = when {
                url.contains("cursor=cursor-next") -> """
                    entry_id:"conversationthread-11-tweet-11" rest_id:"11" name:"Bea" screen_name:"bea" full_text:"second page" created_at_ms:1710000000001
                """.trimIndent()
                url.contains("cursor=cursor-wall") -> "login wall"
                url.contains("syndication") -> """
                    {"id_str":"20","text":"hello","user":{"name":"jack","screen_name":"jack"},"conversation_count":4}
                """.trimIndent()
                url.contains("/i/status/") -> """
                    entry_id:"conversationthread-8-tweet-8" rest_id:"8" name:"Ada" full_text:"from x" created_at_ms:1710000000000
                    cursorType:"Bottom" value:"cursor-next"
                """.trimIndent()
                else -> "<html></html>"
            }
            jsonConnection(body)
        }

        val post = loader.resolve("https://x.com/jack/status/20")
        val page = loader.loadReplies("20", "cursor-next")
        val wall = loader.loadReplies("20", "cursor-wall")

        assertEquals("cursor-next", post.repliesCursor)
        assertEquals(listOf("8"), post.replies.map { it.id })
        assertEquals("second page", page.replies.single().text)
        assertFalse(page.blocked)
        assertTrue(wall.blocked)
        assertTrue(wall.replies.isEmpty())
    }

    @Test
    fun mergingAReplyPageDropsTheOpenedStatusAndStopsAtTheWall() {
        val opened = ParsedXReply(id = "20", author = "Anshu", text = "root", createdAtEpochMillis = 0)
        val kept = mergeXReplyPage(
            primaryId = "20",
            knownIds = setOf("9"),
            requestedCursor = "cursor-next",
            page = XReplyPage(
                replies = listOf(opened, ParsedXReply("11", "Bea", "next", 0)),
                nextCursor = null,
                blocked = false,
            ),
        )
        val wall = mergeXReplyPage(
            primaryId = "20",
            knownIds = setOf("9"),
            requestedCursor = "cursor-next",
            page = XReplyPage(
                replies = listOf(ParsedXReply("12", "Happy", "later", 0)),
                nextCursor = null,
                blocked = true,
            ),
        )

        assertEquals(listOf("11"), kept.fresh.map { it.id })
        assertEquals(XReplyContinuation.Exhausted, kept.continuation)
        assertEquals(XReplyContinuation.Blocked, wall.continuation)
        assertTrue(wall.fresh.isEmpty())
        assertEquals(XReplyContinuation.Blocked, initialXReplyContinuation(5, 1, null))
        assertEquals(XReplyContinuation.More, initialXReplyContinuation(5, 1, "cursor-next"))
        assertEquals(XReplyContinuation.Exhausted, initialXReplyContinuation(1, 1, null))
    }

    @Test
    fun loaderFallsThroughATombstoneToOEmbed() = runTest {
        val opened = Collections.synchronizedList(mutableListOf<String>())
        val loader = XDirectPageLoader { url ->
            opened += url
            val body = if (url.contains("syndication")) {
                """{"__typename":"TweetTombstone","tombstone":{"text":"private"}}"""
            } else {
                """{"author_name":"Ada","html":"<p>Still public</p>"}"""
            }
            jsonConnection(body)
        }

        val post = loader.resolve("https://x.com/ada/status/20")

        assertEquals("Still public", post.text)
        assertTrue(opened.any { it.contains("cdn.syndication.twimg.com") && it.contains("token=6dq1a2xwd93") })
        assertTrue(opened.any { it.startsWith("https://publish.twitter.com/oembed") })
    }

    private fun jsEscape(text: String): String = buildString {
        text.forEach { char ->
            when (char) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                else -> append(char)
            }
        }
    }

    private fun jsonString(text: String): String = "\"" + jsEscape(text) + "\""

    private fun jsonConnection(body: String): HttpURLConnection =
        object : HttpURLConnection(URL("https://cdn.syndication.twimg.com/tweet-result")) {
            override fun setInstanceFollowRedirects(followRedirects: Boolean) {
                super.setInstanceFollowRedirects(followRedirects)
            }
            override fun setRequestMethod(method: String) {
                super.setRequestMethod(method)
            }
            override fun connect() = Unit
            override fun disconnect() = Unit
            override fun usingProxy(): Boolean = false
            override fun getResponseCode(): Int = HTTP_OK
            override fun getInputStream() = ByteArrayInputStream(body.toByteArray(StandardCharsets.UTF_8))
            override fun getErrorStream() = inputStream
        }
}
