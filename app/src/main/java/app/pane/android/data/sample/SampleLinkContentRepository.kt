package app.pane.android.data.sample

import app.pane.android.domain.model.LinkContent
import app.pane.android.domain.repository.LinkContentRepository
import app.pane.android.domain.repository.LoadProgressListener
import java.util.concurrent.ConcurrentHashMap

/** Serves [SamplePosts] with no network. Reddit and Instagram samples page comments in memory. */
class SampleLinkContentRepository(
    private val files: SampleMedia.Files,
) : LinkContentRepository {
    private val pages = ConcurrentHashMap<String, Int>()

    fun supports(url: String): Boolean = SamplePosts.entryFor(url) != null

    override suspend fun resolve(url: String): Result<LinkContent> = outcome(url, reset = false)

    override suspend fun resolve(
        url: String,
        onProgress: LoadProgressListener,
        onPreview: (LinkContent) -> Unit,
    ): Result<LinkContent> = outcome(url, reset = false)

    override suspend fun peekCached(url: String): LinkContent? {
        val entry = SamplePosts.entryFor(url) ?: return null
        if (entry.kind != SamplePosts.Kind.Post) return SamplePosts.render(entry, files, 0)
        return SamplePosts.render(entry, files, pages[entry.id] ?: 0)
    }

    override suspend fun loadMoreComments(url: String): Result<LinkContent> {
        val entry = SamplePosts.entryFor(url)
            ?: return Result.failure(app.pane.android.domain.model.SourceFailure.Unsupported(url))
        if (entry.kind != SamplePosts.Kind.Post) return outcome(url, reset = false)
        val current = pages[entry.id] ?: 0
        if (current >= entry.maxPage) {
            return Result.success(SamplePosts.render(entry, files, current))
        }
        val next = current + 1
        pages[entry.id] = next
        return Result.success(SamplePosts.render(entry, files, next))
    }

    override suspend fun refresh(url: String): Result<LinkContent> = outcome(url, reset = true)

    override suspend fun refresh(
        url: String,
        onProgress: LoadProgressListener,
        onPreview: (LinkContent) -> Unit,
    ): Result<LinkContent> = outcome(url, reset = true)

    private fun outcome(url: String, reset: Boolean): Result<LinkContent> {
        val entry = SamplePosts.entryFor(url)
            ?: return Result.failure(app.pane.android.domain.model.SourceFailure.Unsupported(url))
        if (reset) pages.remove(entry.id)
        return when (entry.kind) {
            SamplePosts.Kind.Error -> Result.failure(
                app.pane.android.domain.model.SourceFailure.Gone(message = "This post was removed"),
            )
            SamplePosts.Kind.Unsupported -> Result.failure(app.pane.android.domain.model.SourceFailure.Unsupported(url))
            SamplePosts.Kind.Post -> Result.success(SamplePosts.render(entry, files, pages[entry.id] ?: 0))
        }
    }
}
