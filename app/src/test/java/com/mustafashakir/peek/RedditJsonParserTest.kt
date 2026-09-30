package com.mustafashakir.peek

import com.mustafashakir.peek.data.reddit.RedditJsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RedditJsonParserTest {
    private val parser = RedditJsonParser()

    @Test
    fun parsesImagePostCommentsAndSkipsMorePlaceholders() {
        val post = parser.parse(IMAGE_POST)

        assertEquals("abc123", post?.id)
        assertEquals("A public photo", post?.title)
        assertEquals("alice", post?.author)
        assertEquals("pics", post?.subreddit)
        assertEquals(1_200, post?.score)
        assertEquals(2, post?.commentCount)
        assertEquals("https://i.redd.it/photo.jpg", post?.media?.single()?.imageUrl)
        assertNull(post?.media?.single()?.videoUrl)
        assertEquals("Nice shot", post?.comments?.single()?.body)
        assertEquals("Thanks", post?.comments?.single()?.replies?.single()?.body)
        assertTrue(post?.comments?.single()?.replies?.single()?.isSubmitter == true)
    }

    @Test
    fun parsesGalleryInItemOrderAndDecodesHtmlEntities() {
        val media = parser.parse(GALLERY_POST)?.media

        assertEquals(listOf("one", "two"), media?.map { it.id })
        assertEquals("https://preview.redd.it/one.jpg?width=800&format=pjpg", media?.get(0)?.imageUrl)
        assertEquals("https://preview.redd.it/two.mp4", media?.get(1)?.videoUrl)
        assertEquals("", media?.get(1)?.imageUrl)
    }

    @Test
    fun parsesRedditVideoAndCrosspostParentWhenTheOuterPostHasNoVideo() {
        val own = parser.parse(VIDEO_POST)?.media?.single()
        assertEquals("https://v.redd.it/clip/DASH_720.mp4?source=fallback", own?.videoUrl)
        assertEquals("https://preview.redd.it/poster.jpg", own?.imageUrl)
        assertEquals(12, own?.durationSeconds)

        val crosspost = parser.parse(CROSSPOST)?.media?.single()
        assertEquals("https://v.redd.it/parent/DASH_720.mp4", crosspost?.videoUrl)
    }

    @Test
    fun parsesTextPostsWithoutInventingMedia() {
        val post = parser.parse(TEXT_POST)

        assertEquals("Hello there", post?.selfText)
        assertTrue(post?.isSelf == true)
        assertTrue(post?.media?.isEmpty() == true)
        assertTrue(post?.over18 == true)
    }

    @Test
    fun returnsNullWhenTheListingHasNoPublicPost() {
        assertNull(parser.parse("""[{"kind":"Listing","data":{"children":[]}}]"""))
        assertNull(parser.parse("""{"message":"Forbidden","error":403}"""))
        assertNull(
            parser.parse(
                """[{"kind":"Listing","data":{"children":[{"kind":"t3","data":{"id":"x","title":"[deleted]"}}]}}]""",
            ),
        )
    }
}

private const val IMAGE_POST = """
[
  {"kind":"Listing","data":{"children":[{"kind":"t3","data":{
    "id":"abc123","title":"A public photo","selftext":"","author":"alice","subreddit":"pics",
    "score":1200,"num_comments":2,"created_utc":1700000000.0,
    "permalink":"/r/pics/comments/abc123/a_public_photo/","url":"https://i.redd.it/photo.jpg",
    "is_self":false,"is_video":false,"over_18":false,"spoiler":false,"post_hint":"image",
    "preview":{"images":[{"source":{"url":"https://preview.redd.it/photo.jpg","width":1080,"height":720}}]}
  }}]}},
  {"kind":"Listing","data":{"children":[
    {"kind":"t1","data":{"id":"c1","author":"bob","body":"Nice shot","created_utc":1700000100,
      "is_submitter":false,"replies":{"kind":"Listing","data":{"children":[
        {"kind":"t1","data":{"id":"c2","author":"alice","body":"Thanks","created_utc":1700000200,
          "is_submitter":true,"replies":""}}
      ]}}}},
    {"kind":"more","data":{"count":1,"children":["c3"]}}
  ]}}
]
"""

private const val GALLERY_POST = """
[{"kind":"Listing","data":{"children":[{"kind":"t3","data":{
  "id":"gal111","title":"Two frames","author":"alice","subreddit":"pics","score":3,"num_comments":0,
  "created_utc":1700000000,"permalink":"/r/pics/comments/gal111/two_frames/","is_gallery":true,"is_self":false,
  "url":"https://www.reddit.com/gallery/gal111",
  "gallery_data":{"items":[{"media_id":"one"},{"media_id":"two"}]},
  "media_metadata":{
    "one":{"status":"valid","e":"Image","s":{"u":"https://preview.redd.it/one.jpg?width=800&amp;format=pjpg","x":800,"y":600}},
    "two":{"status":"valid","e":"AnimatedImage","s":{"mp4":"https://preview.redd.it/two.mp4","x":400,"y":400}},
    "skip":{"status":"failed","e":"Image","s":{"u":"https://preview.redd.it/nope.jpg","x":1,"y":1}}
  }
}}]}}]
"""

private const val VIDEO_POST = """
[{"kind":"Listing","data":{"children":[{"kind":"t3","data":{
  "id":"vid111","title":"A clip","author":"alice","subreddit":"videos","score":10,"num_comments":0,
  "created_utc":1700000000,"permalink":"/r/videos/comments/vid111/a_clip/","is_video":true,"is_self":false,
  "url":"https://v.redd.it/clip",
  "secure_media":{"reddit_video":{"fallback_url":"https://v.redd.it/clip/DASH_720.mp4?source=fallback","height":720,"width":1280,"duration":12}},
  "preview":{"images":[{"source":{"url":"https://preview.redd.it/poster.jpg","width":1280,"height":720}}]}
}}]}}]
"""

private const val CROSSPOST = """
[{"kind":"Listing","data":{"children":[{"kind":"t3","data":{
  "id":"cross1","title":"Shared clip","author":"bob","subreddit":"all","score":1,"num_comments":0,
  "created_utc":1700000000,"permalink":"/r/all/comments/cross1/shared_clip/","is_self":false,"is_video":false,
  "url":"https://www.reddit.com/r/videos/comments/vid111/a_clip/",
  "preview":{"images":[{"source":{"url":"https://preview.redd.it/small.jpg","width":320,"height":180}}]},
  "crosspost_parent_list":[{
    "id":"vid111","title":"A clip","is_video":true,"is_self":false,"url":"https://v.redd.it/parent",
    "secure_media":{"reddit_video":{"fallback_url":"https://v.redd.it/parent/DASH_720.mp4","height":720,"width":1280,"duration":12}},
    "preview":{"images":[{"source":{"url":"https://preview.redd.it/poster.jpg","width":1280,"height":720}}]}
  }]
}}]}}]
"""

private const val TEXT_POST = """
[{"kind":"Listing","data":{"children":[{"kind":"t3","data":{
  "id":"txt111","title":"A question","selftext":"Hello there","author":"alice","subreddit":"ask",
  "score":4,"num_comments":0,"created_utc":1700000000,"permalink":"/r/ask/comments/txt111/a_question/",
  "url":"https://www.reddit.com/r/ask/comments/txt111/a_question/","is_self":true,"over_18":true,"spoiler":false
}}]}}]
"""
