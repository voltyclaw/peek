package app.pane.android

import app.pane.android.data.facebook.FacebookDirectPageLoader
import app.pane.android.data.instagram.InstagramDirectPageLoader
import app.pane.android.data.reddit.RedditDirectPageLoader
import app.pane.android.data.x.XDirectPageLoader
import app.pane.android.domain.model.SourceFailure
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test

class SourceFailureSitesTest {
    @Test
    fun anUnsupportedUrlIsUnsupportedOnEachDirectLoader() = runBlocking {
        val errors = listOf(
            runCatching { RedditDirectPageLoader().resolve("https://example.com/not-reddit") }.exceptionOrNull(),
            runCatching { InstagramDirectPageLoader().resolve("https://example.com/not-instagram") }.exceptionOrNull(),
            runCatching { FacebookDirectPageLoader().resolve("https://example.com/not-facebook") }.exceptionOrNull(),
            runCatching { XDirectPageLoader().resolve("https://example.com/not-x") }.exceptionOrNull(),
        )
        errors.forEach { error ->
            assertTrue(error.toString(), error is SourceFailure.Unsupported)
        }
    }
}
