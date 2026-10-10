package app.pane.android.testing

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.performTouchInput
import app.pane.android.data.history.FileStarImageStore
import app.pane.android.data.history.HistoryUrls
import app.pane.android.data.history.JdbcHistorySql
import app.pane.android.data.history.SqliteHistoryRepository
import app.pane.android.domain.model.Clock
import app.pane.android.domain.model.HistoryEntry
import app.pane.android.domain.model.HistoryQuery
import app.pane.android.domain.model.HistoryView
import app.pane.android.domain.model.RecentLink
import app.pane.android.domain.model.SourceApp
import app.pane.android.domain.model.StarCopy
import app.pane.android.domain.model.StarImageBytes
import app.pane.android.domain.repository.RecentLinksRepository
import app.pane.android.ui.history.HistoryViewModel
import java.io.File
import java.time.ZoneOffset
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first

internal const val NOW = 1_760_000_000_000L

internal class BudgetCompressor : app.pane.android.data.history.StarImageCompressor {
    override fun compress(source: ByteArray, maxBytes: Int): ByteArray = when {
        source.isEmpty() -> ByteArray(0)
        source.size <= maxBytes -> source
        else -> ByteArray(maxBytes) { 7 }
    }
}

internal class RecordingHaptics : HapticFeedback {
    val events = mutableListOf<HapticFeedbackType>()
    override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
        events += hapticFeedbackType
    }
}

internal class FakeRecentLinksRepository(initial: List<RecentLink>) : RecentLinksRepository {
    private val state = MutableStateFlow(initial)
    val openedUrls = mutableListOf<String>()

    override fun observeRecents(): Flow<List<RecentLink>> = state

    override suspend fun markOpened(url: String) {
        openedUrls += url
    }

    override suspend fun remove(url: String) {
        state.value = state.value.filterNot { it.url == url }
    }

    override suspend fun clear() {
        state.value = emptyList()
    }

    override suspend fun restore(link: RecentLink) {
        state.value = listOf(link) + state.value.filterNot { it.url == link.url }
    }
}

internal data class Seed(
    val url: String,
    val title: String,
    val viewedAt: Long,
    val starredAt: Long? = null,
    val author: String = "Ada",
    val handle: String = "ada",
    val mediaType: String = "none",
    val images: StarImageBytes = StarImageBytes(),
)

internal fun historyRepo(
    root: File,
    clock: Clock = Clock { NOW },
    io: CoroutineDispatcher = Dispatchers.Unconfined,
): SqliteHistoryRepository = SqliteHistoryRepository(
    sql = JdbcHistorySql.open(),
    imageStore = FileStarImageStore(root, BudgetCompressor()),
    clock = clock,
    io = io,
)

internal suspend fun seed(repo: SqliteHistoryRepository, vararg rows: Seed) {
    val clock = MutableClock()
    rows.forEach { row ->
        repo.recordSuccessfulView(
            HistoryView(
                url = row.url,
                source = "",
                title = row.title,
                authorName = row.author,
                handle = row.handle,
                caption = row.title,
                thumbUrl = null,
                pfpUrl = null,
                mediaType = row.mediaType,
                viewedAtEpochMillis = row.viewedAt,
            ),
        )
        if (row.starredAt != null) {
            clock.now = row.starredAt
            // The repository clock is fixed at construction. Re-star through the same clock
            // by writing with the repo that was built for NOW, then the test passes starredAt
            // only when the repo clock matches. Callers that need distinct starredAt use
            // seedStarred on a MutableClock repo.
            repo.star(row.url, StarCopy(row.title, row.author, row.handle, row.title), row.images)
        }
    }
}

internal class MutableClock(var now: Long = NOW) : Clock {
    override fun nowEpochMillis(): Long = now
}

internal suspend fun seedOn(
    repo: SqliteHistoryRepository,
    clock: MutableClock,
    vararg rows: Seed,
) {
    rows.forEach { row ->
        repo.recordSuccessfulView(
            HistoryView(
                url = row.url,
                source = "",
                title = row.title,
                authorName = row.author,
                handle = row.handle,
                caption = row.title,
                thumbUrl = null,
                pfpUrl = null,
                mediaType = row.mediaType,
                viewedAtEpochMillis = row.viewedAt,
            ),
        )
        if (row.starredAt != null) {
            clock.now = row.starredAt
            repo.star(row.url, StarCopy(row.title, row.author, row.handle, row.title), row.images)
        }
    }
}

internal fun historyVm(
    repo: SqliteHistoryRepository,
    initial: HistoryQuery = HistoryQuery(),
    persisted: MutableList<HistoryQuery> = mutableListOf(),
    clock: Clock = Clock { NOW },
): HistoryViewModel = HistoryViewModel(
    historyRepository = repo,
    initial = initial,
    persist = { persisted += it },
    clock = clock,
    zone = ZoneOffset.UTC,
)

internal fun xUrl(n: Int) = "https://x.com/i/status/$n"
/** Stored form. A permalink is rewritten to /comments/{id}/ before the row tag exists. */
internal fun redditUrl(n: Int) = "https://www.reddit.com/comments/abc$n/"
internal fun instagramUrl(n: Int) = "https://www.instagram.com/p/abc$n/"
internal fun youtubeUrl(id: String) = "https://www.youtube.com/watch?v=$id"
internal fun tiktokUrl(n: Long) = "https://www.tiktok.com/@u/video/$n"
internal fun blueskyUrl(n: Int) = "https://bsky.app/profile/a.bsky.social/post/$n"

internal suspend fun rowsOf(repo: SqliteHistoryRepository): List<HistoryEntry> =
    repo.observeHistory().first()

internal fun canonical(url: String): String = HistoryUrls.canonical(url)

internal fun SemanticsNodeInteraction.dragRow(
    fraction: Float,
    dir: Int,
    durationMs: Long,
    release: Boolean = true,
    steps: Int = 20,
) = performTouchInput {
    val start = Offset(if (dir < 0) width * 0.85f else width * 0.15f, centerY)
    val total = fraction * width + viewConfiguration.touchSlop + 2f
    down(start)
    val stepMs = (durationMs / steps).coerceAtLeast(1)
    repeat(steps) {
        advanceEventTime(stepMs)
        moveBy(Offset(dir * total / steps, 0f))
    }
    if (release) up()
}

internal fun <T> SemanticsNode.prop(key: SemanticsPropertyKey<T>): T = config[key]

internal fun appOf(url: String): SourceApp = HistoryUrls.sourceApp(url)
