package com.mustafashakir.peek

import com.mustafashakir.peek.data.reddit.RedditShredditPost
import org.junit.Assert.assertEquals
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
