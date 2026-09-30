package app.pane.android

import app.pane.android.data.fixture.FixtureCatalog
import app.pane.android.data.fixture.FixtureLinkContentRepository
import app.pane.android.domain.model.LinkKind
import app.pane.android.domain.repository.LoadProgressListener
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FixtureRepositoryTest {
    private val repository = FixtureLinkContentRepository()

    @Test
    fun resolvesEverySeededLink() = runTest {
        val resolved = FixtureCatalog.urls.map { repository.resolve(it).getOrThrow() }

        assertEquals(3, resolved.size)
        assertEquals(LinkKind.Post, resolved.first().kind)
        assertEquals(LinkKind.Video, resolved[1].kind)
    }

    @Test
    fun rejectsUnknownLinks() = runTest {
        assertTrue(repository.resolve("https://invalid.example/post").isFailure)
    }

    @Test
    fun defaultProgressOverloadNeverInvokesListener() = runTest {
        var invoked = false
        val result = repository.resolve(FixtureCatalog.urls.first(), LoadProgressListener { invoked = true })

        assertTrue(result.isSuccess)
        assertEquals(false, invoked)
    }
}
