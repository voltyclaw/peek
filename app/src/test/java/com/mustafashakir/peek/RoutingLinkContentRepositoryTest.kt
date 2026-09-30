package com.mustafashakir.peek

import com.mustafashakir.peek.data.resolver.RoutingLinkContentRepository
import com.mustafashakir.peek.domain.model.Author
import com.mustafashakir.peek.domain.model.LinkContent
import com.mustafashakir.peek.domain.model.LinkKind
import com.mustafashakir.peek.domain.model.LinkSource
import com.mustafashakir.peek.domain.model.Media
import com.mustafashakir.peek.domain.model.MediaLocation
import com.mustafashakir.peek.domain.repository.LinkContentRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutingLinkContentRepositoryTest {
    @Test
    fun dispatchesToTheFirstSourceThatSupportsTheUrl() = runTest {
        val instagram = FakeRepository { it.contains("instagram.com") }
        val reddit = FakeRepository { it.contains("reddit.com") || it.contains("redd.it") }
        val router = RoutingLinkContentRepository(
            listOf(
                RoutingLinkContentRepository.Route(instagram::supports, instagram),
                RoutingLinkContentRepository.Route(reddit::supports, reddit),
            ),
        )

        val redditResult = router.resolve("https://redd.it/abc123").getOrThrow()
        val instagramResult = router.resolve("https://www.instagram.com/p/example/").getOrThrow()
        val unsupported = router.resolve("https://example.com/post")

        assertEquals("https://redd.it/abc123", redditResult.url)
        assertEquals(listOf("https://redd.it/abc123"), reddit.resolved)
        assertEquals(listOf("https://www.instagram.com/p/example/"), instagram.resolved)
        assertEquals("https://www.instagram.com/p/example/", instagramResult.url)
        assertTrue(unsupported.isFailure)
        assertEquals(null, router.peekCached("https://example.com/post"))
    }

    private class FakeRepository(
        private val supportsUrl: (String) -> Boolean,
    ) : LinkContentRepository {
        val resolved = mutableListOf<String>()

        fun supports(url: String): Boolean = supportsUrl(url)

        override suspend fun resolve(url: String): Result<LinkContent> {
            resolved += url
            return Result.success(
                LinkContent(
                    url = url,
                    title = url,
                    source = LinkSource.Reddit,
                    kind = LinkKind.Post,
                    thumbnail = MediaLocation.Remote("https://example.com/image.jpg"),
                    media = Media(MediaLocation.Remote("https://example.com/image.jpg"), url),
                    author = Author("author", "metadata"),
                    commentCount = 0,
                    comments = emptyList(),
                ),
            )
        }

        override suspend fun peekCached(url: String): LinkContent? = null

        override suspend fun refresh(url: String): Result<LinkContent> = resolve(url)
    }
}
