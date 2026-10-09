package app.pane.android

import app.pane.android.data.reddit.RedditGalleryWait
import app.pane.android.data.reddit.RedditShredditPost
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RedditShredditPostTest {
    @Test
    fun readsAPublicImagePostFromTheServerRenderedElement() {
        val html = """
            <shreddit-post id="t3_1w3fcl7" post-title="In 1960, David Latimer planted a garden"
              author="The_Love-Tap" subreddit-name="interestingasfuck" score="45372"
              comment-count="676" created-timestamp="2026-08-31T14:25:05.266000+0000"
              content-href="https://i.redd.it/h190t4tyzpmh1.jpeg" post-type="image">
            </shreddit-post>
        """.trimIndent()

        val post = RedditShredditPost.parse(html)

        assertEquals("1w3fcl7", post?.id)
        assertEquals("The_Love-Tap", post?.author)
        assertEquals("interestingasfuck", post?.subreddit)
        assertEquals(45372, post?.score)
        assertEquals("https://i.redd.it/h190t4tyzpmh1.jpeg", post?.media?.single()?.imageUrl)
        assertTrue(post?.createdUtcEpochSeconds ?: 0L > 0L)
    }

    @Test
    fun playsThePackagedMp4AndPosterInsteadOfTheBareRedditVideoLink() {
        val post = RedditShredditPost.parse(VIDEO_PAGE)

        val media = post?.media?.single()
        assertEquals(
            "https://packaged-media.redd.it/munf2f7c5psh1/pb/m2-res_1280p.mp4?m=DASHPlaylist.mpd&v=1",
            media?.videoUrl,
        )
        assertEquals("https://external-preview.redd.it/passport.jpeg", media?.imageUrl)
        assertEquals(1280, media?.height)
        assertEquals(720, media?.width)
        assertEquals(40, media?.durationSeconds)
        assertEquals("1wubxdy", post?.id)
    }

    @Test
    fun bareRedditVideoLinkFallsBackToHlsWhenThePlayerIsMissing() {
        val html = """
            <shreddit-post id="t3_vid111" post-title="A clip" post-type="video" author="alice"
              content-href="https://v.redd.it/clipid"></shreddit-post>
        """.trimIndent()

        val media = RedditShredditPost.parse(html)?.media?.single()

        assertEquals("https://v.redd.it/clipid/HLSPlaylist.m3u8", media?.videoUrl)
    }

    @Test
    fun readsGallerySlidesInsteadOfTheGalleryPageUrl() {
        val media = RedditShredditPost.parse(GALLERY_PAGE)?.media

        assertEquals(listOf("monster-v0-vzt58q9dhosh1", "monster-v0-heidkheviosh1"), media?.map { it.id })
        assertEquals(
            "https://preview.redd.it/monster-v0-vzt58q9dhosh1.jpg?width=1080&crop=smart&auto=webp&s=abc",
            media?.get(0)?.imageUrl,
        )
        assertEquals(
            "https://preview.redd.it/monster-v0-heidkheviosh1.png?width=736&format=png&auto=webp&s=def",
            media?.get(1)?.imageUrl,
        )
        assertEquals(null, media?.get(0)?.videoUrl)
    }

    @Test
    fun appendsLaterSlidesTheWebViewCollected() {
        val html = GALLERY_PAGE + """
            <peek-gallery>
              <img src="https://preview.redd.it/monster-v0-vzt58q9dhosh1.jpg?width=640" width="640" height="640">
              <img src="https://preview.redd.it/monster-v0-4edkq47ejosh1.jpeg?width=1080" width="1080" height="1400">
            </peek-gallery>
        """.trimIndent()

        val media = RedditShredditPost.parse(html)?.media

        assertEquals(
            listOf("monster-v0-vzt58q9dhosh1", "monster-v0-heidkheviosh1", "monster-v0-4edkq47ejosh1"),
            media?.map { it.id },
        )
        assertTrue(media?.get(0)?.imageUrl?.contains("width=1080") == true)
    }

    @Test
    fun aGalleryKeepsWaitingUntilTheSlideListSettles() {
        assertTrue(RedditGalleryWait.shouldWait(mediaPending = true, displayableCount = 0, stablePolls = 2, waits = 2))
        assertFalse(RedditGalleryWait.shouldWait(mediaPending = true, displayableCount = 0, stablePolls = 6, waits = 6))
        assertTrue(RedditGalleryWait.shouldWait(mediaPending = true, displayableCount = 2, stablePolls = 5, waits = 4))
        assertTrue(RedditGalleryWait.shouldWait(mediaPending = true, displayableCount = 2, stablePolls = 1, waits = 8))
        assertFalse(RedditGalleryWait.shouldWait(mediaPending = true, displayableCount = 2, stablePolls = 3, waits = 8))
        assertFalse(RedditGalleryWait.shouldWait(mediaPending = false, displayableCount = 1, stablePolls = 0, waits = 0))
        assertFalse(RedditGalleryWait.shouldWait(mediaPending = true, displayableCount = 1, stablePolls = 0, waits = RedditGalleryWait.MAX_WAITS))
    }

    @Test
    fun failsClosedForQuarantineAndRemovedPosts() {
        assertNull(
            RedditShredditPost.parse("""<shreddit-post id="t3_abc" post-title="Hidden" quarantine="true"></shreddit-post>"""),
        )
        assertNull(
            RedditShredditPost.parse("""<shreddit-post id="t3_abc" post-title="[removed]"></shreddit-post>"""),
        )
        assertNull(RedditShredditPost.parse("<html><title>Blocked</title></html>"))
    }
}

private const val VIDEO_PAGE = """
<shreddit-post id="t3_1wubxdy" post-title="The new US Passport reveal akin to Apple product reveal."
  author="JKKIDD231" subreddit-name="interestingasfuck" score="4078" comment-count="1572"
  post-type="video" content-href="https://v.redd.it/munf2f7c5psh1">
  <shreddit-player post-id="t3_1wubxdy" post-type="video"
    poster="https://external-preview.redd.it/passport.jpeg"
    preview="https://v.redd.it/munf2f7c5psh1/CMAF_96.mp4"
    src="https://v.redd.it/munf2f7c5psh1/HLSPlaylist.m3u8?f=sd&amp;v=1"
    packaged-media-json='{"playbackMp4s":{"duration":40,"permutations":[
      {"source":{"dimensions":{"height":480,"width":270},"url":"https://packaged-media.redd.it/munf2f7c5psh1/pb/m2-res_480p.mp4?m=DASHPlaylist.mpd&amp;v=1","videoCodec":"H264"}},
      {"source":{"dimensions":{"height":1280,"width":720},"url":"https://packaged-media.redd.it/munf2f7c5psh1/pb/m2-res_1280p.mp4?m=DASHPlaylist.mpd&amp;v=1","videoCodec":"H264"}}
    ]}}'>
  </shreddit-player>
</shreddit-post>
<shreddit-player post-id="t3_ad" post-promoted="" src="https://v.redd.it/ad/HLSPlaylist.m3u8"></shreddit-player>
<shreddit-player post-id="t3_1wubxdy" comment-id="t1_gif" gif=""
  src="https://preview.redd.it/comment.gif?format=mp4"></shreddit-player>
"""

private const val GALLERY_PAGE = """
<shreddit-post id="t3_1wu8cmd" post-title="Monster - some comics about depression [OC]"
  author="caromakercomics" subreddit-name="comics" score="4353" comment-count="61"
  post-type="gallery" content-href="https://www.reddit.com/gallery/1wu8cmd"
  permalink="/r/comics/comments/1wu8cmd/monster_some_comics_about_depression_oc/">
  <gallery-carousel post-id="t3_1wu8cmd">
    <ul>
      <li slot="page-2">
        <figure>
          <img alt="panel 2" src="https://preview.redd.it/monster-v0-heidkheviosh1.png?width=320&amp;auto=webp"
            srcset="https://preview.redd.it/monster-v0-heidkheviosh1.png?width=320&amp;auto=webp 320w, https://preview.redd.it/monster-v0-heidkheviosh1.png?width=736&amp;format=png&amp;auto=webp&amp;s=def 736w">
        </figure>
      </li>
      <li slot="page-1">
        <img role="presentation" class="post-background-image-filter" src="https://preview.redd.it/monster-v0-vzt58q9dhosh1.jpg?width=320">
        <figure>
          <img alt="panel 1" width="2048" height="2048"
            src="https://preview.redd.it/monster-v0-vzt58q9dhosh1.jpg?width=640&amp;auto=webp"
            srcset="https://preview.redd.it/monster-v0-vzt58q9dhosh1.jpg?width=640&amp;auto=webp 640w, https://preview.redd.it/monster-v0-vzt58q9dhosh1.jpg?width=1080&amp;crop=smart&amp;auto=webp&amp;s=abc 1080w">
        </figure>
      </li>
    </ul>
  </gallery-carousel>
</shreddit-post>
<shreddit-comment author="bob" thingid="t1_c1">
  <figure class="rte-media">
    <img src="https://preview.redd.it/comment-only.png?width=640">
  </figure>
</shreddit-comment>
<gallery-carousel post-id="t3_ad">
  <img src="https://preview.redd.it/ad.jpg">
</gallery-carousel>
"""
