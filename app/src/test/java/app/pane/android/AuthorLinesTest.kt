package app.pane.android

import app.pane.android.domain.model.Author
import app.pane.android.domain.model.AuthorLines
import app.pane.android.domain.model.ExternalPostMetadata
import app.pane.android.domain.model.LinkContent
import app.pane.android.domain.model.LinkKind
import app.pane.android.domain.model.LinkSource
import app.pane.android.domain.model.Media
import app.pane.android.domain.model.MediaLocation
import app.pane.android.ui.mapper.UiImageMapper
import app.pane.android.ui.mapper.ViewerUiMapper
import org.junit.Assert.assertEquals
import org.junit.Test

class AuthorLinesTest {
    private val mapper = ViewerUiMapper(UiImageMapper())

    @Test
    fun sourceNameIsNeverTheDisplayName() {
        assertEquals(AuthorLines.Presented("Maya Ren", "mayaren"), AuthorLines.present("Maya Ren", "mayaren"))
        assertEquals(AuthorLines.Presented("Maya Ren", "@mayaren"), AuthorLines.present("Maya Ren", "@mayaren"))
        assertEquals(AuthorLines.Presented("", ""), AuthorLines.present("Facebook", "FACEBOOK"))
        assertEquals(AuthorLines.Presented("Maya Ren", ""), AuthorLines.present("Maya Ren", "FACEBOOK"))
        assertEquals(AuthorLines.Presented("Maya Ren", ""), AuthorLines.present("Maya Ren", "X"))
        assertEquals(AuthorLines.Presented("maya.ren", "INSTAGRAM · 12 LIKES"), AuthorLines.present("maya.ren", "INSTAGRAM · 12 LIKES"))
        assertEquals(AuthorLines.Presented("", ""), AuthorLines.present("Instagram", "INSTAGRAM"))
        assertEquals(AuthorLines.Presented("maya_ren", "REDDIT · r/hiking · 4 POINTS"), AuthorLines.present("maya_ren", "REDDIT · r/hiking · 4 POINTS"))
        assertEquals(AuthorLines.Presented("REDDIT · r/hiking · 4 POINTS", ""), AuthorLines.present("Reddit", "REDDIT · r/hiking · 4 POINTS"))
        assertEquals(AuthorLines.Presented("Ada", "@ada"), AuthorLines.present("Ada", "@ada"))
        assertEquals(AuthorLines.Presented("", ""), AuthorLines.present("X", "X"))
    }

    @Test
    fun viewerDropsADuplicateFacebookAuthor() {
        val ui = mapper.map(post(LinkSource.Facebook, "Facebook", "FACEBOOK"))
        assertEquals("", ui.authorName)
        assertEquals("", ui.authorMetadata)
    }

    @Test
    fun viewerKeepsARealNameAndHandle() {
        val facebook = mapper.map(post(LinkSource.Facebook, "Maya Ren", "mayaren"))
        assertEquals("Maya Ren", facebook.authorName)
        assertEquals("mayaren", facebook.authorMetadata)
        val x = mapper.map(post(LinkSource.X, "Maya Ren", "@mayaren"))
        assertEquals("Maya Ren", x.authorName)
        assertEquals("@mayaren", x.authorMetadata)
    }

    private fun post(source: LinkSource, name: String, metadata: String) = LinkContent(
        url = "https://example.com/post",
        title = "Hello",
        source = source,
        kind = LinkKind.Post,
        thumbnail = MediaLocation.Remote(""),
        media = Media(MediaLocation.Remote(""), "Hello", badge = "TEXT"),
        author = Author(name, metadata),
        commentCount = 0,
        comments = emptyList(),
        sourceMetadata = ExternalPostMetadata(postId = "1"),
    )
}
