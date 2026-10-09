package app.pane.android.data.history

import app.pane.android.data.links.profileLink
import app.pane.android.domain.model.Clock
import app.pane.android.domain.model.HISTORY_TEXT_LIMIT
import app.pane.android.domain.model.HistoryEntry
import app.pane.android.domain.model.HistoryLedger
import app.pane.android.domain.model.HistoryQuery
import app.pane.android.domain.model.HistoryRetention
import app.pane.android.domain.model.HistoryRetentionCandidate
import app.pane.android.domain.model.HistorySwipeEffect
import app.pane.android.domain.model.HistorySwipeResolver
import app.pane.android.domain.model.HistoryUndo
import app.pane.android.domain.model.HistoryView
import app.pane.android.domain.model.SourceApp
import app.pane.android.domain.model.RecentContent
import app.pane.android.domain.model.RecentLink
import app.pane.android.domain.model.toHistoryView
import app.pane.android.domain.model.StarCopy
import app.pane.android.domain.model.StarImageBytes
import app.pane.android.domain.model.SystemClock
import app.pane.android.domain.repository.HistoryRepository
import app.pane.android.domain.tiktok.TikTokCopyRetention
import app.pane.android.domain.youtube.YouTubeCopyExpiry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

internal class SqliteHistoryRepository(
    private val sql: HistorySql,
    private val imageStore: StarImageStore,
    private val clock: Clock = SystemClock,
) : HistoryRepository {
    private val lock = Any()
    private val entries = MutableStateFlow<List<HistoryEntry>>(emptyList())

    init {
        synchronized(lock) { reload() }
    }

    override fun observeHistory(query: HistoryQuery): Flow<List<HistoryEntry>> =
        entries.map { rows -> HistoryQueries.filter(rows, query) }

    override suspend fun distinctSourceApps(): List<SourceApp> = withContext(Dispatchers.IO) {
        synchronized(lock) { HistoryQueries.distinctApps(entries.value) }
    }

    override suspend fun recordSuccessfulView(view: HistoryView) = withContext(Dispatchers.IO) {
        synchronized(lock) {
            if (profileLink(view.url) != null) return@withContext
            val url = HistoryUrls.canonical(view.url)
            if (url.isBlank() || profileLink(url) != null) return@withContext
            val snapshot = view.normalized(url)
            val app = HistoryUrls.sourceApp(url, snapshot.source)
            val pfp = if (app == SourceApp.TikTok) null else snapshot.pfpUrl
            sql.transaction {
                if (find(url) == null) {
                    insert(
                        snapshot.asEntry(
                            firstViewedAt = view.viewedAtEpochMillis,
                            lastViewedAt = view.viewedAtEpochMillis,
                            viewCount = 1,
                            starredAt = null,
                            pinnedThumbPath = null,
                            pinnedPfpPath = null,
                        ),
                    )
                } else {
                    sql.exec(
                        """
                        UPDATE history SET
                          source = ?, source_app = ?, title = ?, author_name = ?, handle = ?, caption = ?,
                          thumb_url = ?, pfp_url = ?, media_type = ?,
                          last_viewed_at = ?, view_count = view_count + 1
                        WHERE url = ?
                        """.trimIndent(),
                        listOf(
                            snapshot.source,
                            HistoryUrls.sourceApp(url, snapshot.source).name,
                            snapshot.title,
                            snapshot.authorName,
                            snapshot.handle,
                            snapshot.caption,
                            snapshot.thumbUrl,
                            pfp,
                            HistoryLedger.normalizeMedia(snapshot.mediaType, snapshot.thumbUrl),
                            view.viewedAtEpochMillis,
                            url,
                        ),
                    )
                }
                if (app == SourceApp.TikTok) stampTikTokCache(url, snapshot, view.viewedAtEpochMillis)
            }
            reload()
        }
    }

    override suspend fun star(url: String, copy: StarCopy, images: StarImageBytes) = withContext(Dispatchers.IO) {
        synchronized(lock) {
            val key = HistoryUrls.canonical(url)
            if (key.isBlank() || profileLink(key) != null) return@withContext
            val existing = find(key)
            val tiktok = HistoryUrls.sourceApp(key) == SourceApp.TikTok
            if (tiktok) imageStore.unpin(existing?.pinnedThumbPath, existing?.pinnedPfpPath)
            val pinned = if (tiktok) {
                PinnedStarFiles(null, null, 0)
            } else {
                imageStore.pin(key, images.thumbnail, images.profile)
            }
            val starredAt = existing?.starredAt ?: clock.nowEpochMillis()
            val text = copy.normalized()
            sql.transaction {
                if (existing == null) {
                    insert(
                        HistoryEntry(
                            url = key,
                            source = HistoryUrls.sourceLabel(key),
                            sourceApp = HistoryUrls.sourceApp(key),
                            title = text.title,
                            authorName = text.authorName,
                            handle = text.handle,
                            caption = text.caption,
                            thumbUrl = text.thumbUrl,
                            pfpUrl = text.pfpUrl,
                            pinnedThumbPath = pinned.thumbPath,
                            pinnedPfpPath = pinned.profilePath,
                            mediaType = HistoryLedger.NONE,
                            note = null,
                            firstViewedAt = starredAt,
                            lastViewedAt = starredAt,
                            viewCount = 0,
                            starredAt = starredAt,
                        ),
                    )
                } else {
                    sql.exec(
                        """
                        UPDATE history SET
                          title = ?, author_name = ?, handle = ?, caption = ?,
                          thumb_url = ?, pfp_url = ?,
                          pinned_thumb_path = ?, pinned_pfp_path = ?,
                          starred_at = ?
                        WHERE url = ?
                        """.trimIndent(),
                        listOf(
                            text.title,
                            text.authorName,
                            text.handle,
                            text.caption,
                            text.thumbUrl,
                            text.pfpUrl,
                            pinned.thumbPath,
                            pinned.profilePath,
                            starredAt,
                            key,
                        ),
                    )
                }
            }
            if (tiktok) {
                val hasCache = text.title.isNotBlank() || text.authorName.isNotBlank() ||
                    text.handle.isNotBlank() || text.caption.isNotBlank() || !text.thumbUrl.isNullOrBlank()
                sql.exec(
                    "UPDATE history SET pfp_url = NULL, cache_fetched_at = ? WHERE url = ?",
                    listOf(if (hasCache) starredAt else null, key),
                )
            }
            if (existing?.pinnedThumbPath != null && existing.pinnedThumbPath != pinned.thumbPath) {
                imageStore.unpin(existing.pinnedThumbPath, null)
            }
            if (existing?.pinnedPfpPath != null && existing.pinnedPfpPath != pinned.profilePath) {
                imageStore.unpin(null, existing.pinnedPfpPath)
            }
            reload()
        }
    }

    override suspend fun unstar(url: String) = withContext(Dispatchers.IO) {
        synchronized(lock) {
            val key = HistoryUrls.canonical(url)
            val existing = find(key) ?: return@withContext
            sql.transaction {
                sql.exec(
                    """
                    UPDATE history SET starred_at = NULL, pinned_thumb_path = NULL, pinned_pfp_path = NULL
                    WHERE url = ?
                    """.trimIndent(),
                    listOf(key),
                )
            }
            imageStore.unpin(existing.pinnedThumbPath, existing.pinnedPfpPath)
            reload()
        }
    }

    override suspend fun seedFromRecents(recents: List<RecentLink>) =
        seedFromCached(recents.map { RecentContent(it, null) })

    override suspend fun seedFromCached(entries: List<RecentContent>) = withContext(Dispatchers.IO) {
        synchronized(lock) {
            sql.transaction {
                entries.sortedByDescending { it.recentLink.openedAtEpochMillis }.forEach { entry ->
                    val recent = entry.recentLink
                    if (recent.url.isBlank() || profileLink(recent.url) != null) return@forEach
                    val url = HistoryUrls.canonical(recent.url)
                    if (url.isBlank() || profileLink(url) != null) return@forEach
                    if (find(url) != null) return@forEach
                    val viewed = entry.content?.toHistoryView(url, recent.openedAtEpochMillis)
                    insert(
                        HistoryEntry(
                            url = url,
                            source = viewed?.source ?: HistoryUrls.sourceLabel(url),
                            sourceApp = HistoryUrls.sourceApp(url, viewed?.source.orEmpty()),
                            title = viewed?.title.orEmpty(),
                            authorName = viewed?.authorName.orEmpty(),
                            handle = viewed?.handle.orEmpty(),
                            caption = viewed?.caption.orEmpty(),
                            thumbUrl = viewed?.thumbUrl,
                            pfpUrl = viewed?.pfpUrl,
                            pinnedThumbPath = null,
                            pinnedPfpPath = null,
                            mediaType = HistoryLedger.normalizeMedia(viewed?.mediaType.orEmpty(), viewed?.thumbUrl),
                            note = null,
                            firstViewedAt = recent.openedAtEpochMillis,
                            lastViewedAt = recent.openedAtEpochMillis,
                            viewCount = 1,
                            starredAt = null,
                        ),
                    )
                    if (HistoryUrls.sourceApp(url) == SourceApp.TikTok && viewed != null) {
                        stampTikTokCache(url, viewed, recent.openedAtEpochMillis)
                    }
                }
            }
            reload()
        }
    }

    override suspend fun remove(url: String): HistoryUndo? = withContext(Dispatchers.IO) {
        synchronized(lock) { deleteRow(HistoryUrls.canonical(url)) }
    }

    override suspend fun applySwipe(url: String): HistoryUndo? = withContext(Dispatchers.IO) {
        synchronized(lock) {
            val key = HistoryUrls.canonical(url)
            val row = find(key) ?: return@withContext null
            when (HistorySwipeResolver.effect(row.starredAt != null)) {
                HistorySwipeEffect.Unstar -> unstarRow(row)
                HistorySwipeEffect.Remove -> deleteRow(key)
            }
        }
    }

    override suspend fun undo(undo: HistoryUndo) = withContext(Dispatchers.IO) {
        synchronized(lock) {
            val row = undo.row
            undo.thumbBytes?.let { imageStore.restore(row.pinnedThumbPath, it) }
            undo.profileBytes?.let { imageStore.restore(row.pinnedPfpPath, it) }
            sql.transaction {
                if (find(row.url) == null) insert(row) else replaceRow(row)
            }
            reload()
        }
    }

    override suspend fun pruneUnstarred(enabled: Boolean, cap: Int): List<String> = withContext(Dispatchers.IO) {
        synchronized(lock) {
            if (!enabled) return@withContext emptyList()
            val victims = HistoryRetention.urlsToPrune(retentionRows(), cap)
            if (victims.isEmpty()) return@withContext emptyList()
            sql.transaction {
                victims.forEach { url ->
                    val row = find(url)
                    sql.exec("DELETE FROM history WHERE url = ?", listOf(url))
                    imageStore.unpin(row?.pinnedThumbPath, row?.pinnedPfpPath)
                }
            }
            reload()
            victims
        }
    }

    internal suspend fun setNote(url: String, note: String?) = withContext(Dispatchers.IO) {
        synchronized(lock) {
            val key = HistoryUrls.canonical(url)
            val stored = note?.trim()?.take(HISTORY_TEXT_LIMIT)?.takeIf { it.isNotEmpty() }
            sql.exec("UPDATE history SET note = ? WHERE url = ?", listOf(stored, key))
            reload()
        }
    }

    internal suspend fun addTag(url: String, name: String) = withContext(Dispatchers.IO) {
        synchronized(lock) {
            val key = HistoryUrls.canonical(url)
            if (find(key) == null) return@withContext
            val display = name.trim().take(24)
            val norm = display.lowercase()
            if (norm.isEmpty()) return@withContext
            sql.transaction {
                sql.exec(
                    "INSERT OR IGNORE INTO tag(name, name_norm) VALUES(?, ?)",
                    listOf(display, norm),
                )
                val id = sql.query("SELECT id FROM tag WHERE name_norm = ?", listOf(norm)).first().long("id")
                sql.exec(
                    "INSERT OR IGNORE INTO history_tag(url, tag_id) VALUES(?, ?)",
                    listOf(key, id),
                )
            }
            reload()
        }
    }

    private fun unstarRow(row: HistoryEntry): HistoryUndo {
        val undo = capture(row)
        sql.transaction {
            sql.exec(
                """
                UPDATE history SET starred_at = NULL, pinned_thumb_path = NULL, pinned_pfp_path = NULL
                WHERE url = ?
                """.trimIndent(),
                listOf(row.url),
            )
        }
        imageStore.unpin(row.pinnedThumbPath, row.pinnedPfpPath)
        reload()
        return undo
    }

    private fun deleteRow(url: String): HistoryUndo? {
        val row = find(url) ?: return null
        val undo = capture(row)
        sql.transaction {
            sql.exec("DELETE FROM history WHERE url = ?", listOf(url))
        }
        imageStore.unpin(row.pinnedThumbPath, row.pinnedPfpPath)
        reload()
        return undo
    }

    private fun capture(row: HistoryEntry) = HistoryUndo(
        row = row,
        thumbBytes = imageStore.read(row.pinnedThumbPath),
        profileBytes = imageStore.read(row.pinnedPfpPath),
    )

    private fun replaceRow(row: HistoryEntry) {
        sql.exec(
            """
            UPDATE history SET
              source = ?, source_app = ?, title = ?, author_name = ?, handle = ?, caption = ?,
              thumb_url = ?, pfp_url = ?, pinned_thumb_path = ?, pinned_pfp_path = ?,
              media_type = ?, note = ?, first_viewed_at = ?, last_viewed_at = ?,
              view_count = ?, starred_at = ?
            WHERE url = ?
            """.trimIndent(),
            listOf(
                row.source,
                row.sourceApp.name,
                row.title,
                row.authorName,
                row.handle,
                row.caption,
                row.thumbUrl,
                row.pfpUrl,
                row.pinnedThumbPath,
                row.pinnedPfpPath,
                row.mediaType,
                row.note,
                row.firstViewedAt,
                row.lastViewedAt,
                row.viewCount,
                row.starredAt,
                row.url,
            ),
        )
    }

    private fun retentionRows(): List<HistoryRetentionCandidate> =
        sql.query(
            """
            SELECT h.url AS url, h.last_viewed_at AS last_viewed_at, h.starred_at AS starred_at,
              h.note AS note,
              EXISTS(SELECT 1 FROM history_tag t WHERE t.url = h.url) AS tagged
            FROM history h
            """.trimIndent(),
        ).map { row ->
            HistoryRetentionCandidate(
                url = row.text("url").orEmpty(),
                lastViewedAt = row.long("last_viewed_at") ?: 0L,
                starred = row.long("starred_at") != null,
                hasNote = !row.text("note").isNullOrBlank(),
                hasTags = (row.long("tagged") ?: 0L) != 0L,
            )
        }

    private fun find(url: String): HistoryEntry? =
        sql.query("$SELECT_ROW WHERE url = ?", listOf(url)).firstOrNull()?.toEntry()

    override suspend fun stripDisplayCache(url: String) = withContext(Dispatchers.IO) {
        synchronized(lock) {
            clearDisplayCache("url = ?", listOf(HistoryUrls.canonical(url)))
            reload()
        }
    }

    override suspend fun stripSourceDisplayCache(source: SourceApp) = withContext(Dispatchers.IO) {
        synchronized(lock) {
            clearDisplayCache("source_app = ?", listOf(source.name))
            reload()
        }
    }

    override suspend fun replaceDisplayCache(
        url: String,
        title: String,
        authorName: String,
        handle: String,
        caption: String,
        thumbUrl: String?,
        fetchedAtEpochMillis: Long,
    ) = withContext(Dispatchers.IO) {
        synchronized(lock) {
            val key = HistoryUrls.canonical(url)
            sql.exec(
                """
                UPDATE history SET
                  title = ?, author_name = ?, handle = ?, caption = ?,
                  thumb_url = ?, pfp_url = NULL, cache_fetched_at = ?
                WHERE url = ?
                """.trimIndent(),
                listOf(title, authorName, handle, caption, thumbUrl, fetchedAtEpochMillis, key),
            )
            reload()
        }
    }

    private fun reload() {
        val now = clock.nowEpochMillis()
        stripStaleYouTube(now)
        if (TikTokCopyRetention.ENFORCED) stripStaleTikTok(now)
        entries.value = sql.query("$SELECT_ROW ORDER BY last_viewed_at DESC, url ASC").map { it.toEntry() }
    }

    /** YouTube API fields older than 30 days are cleared. Pane-owned star, note, and view time stay. */
    private fun stripStaleYouTube(now: Long) {
        val cutoff = now - YouTubeCopyExpiry.WINDOW_MILLIS
        sql.exec(
            """
            UPDATE history SET
              title = '', author_name = '', handle = '', caption = '',
              thumb_url = NULL, pfp_url = NULL, pinned_thumb_path = NULL, pinned_pfp_path = NULL,
              media_type = 'none'
            WHERE source_app = ? AND last_viewed_at > 0 AND last_viewed_at < ?
            """.trimIndent(),
            listOf(SourceApp.YouTube.name, cutoff),
        )
    }

    /** TikTok oEmbed cache older than 30 days without a refresh. The URL row stays. */
    private fun stripStaleTikTok(now: Long) {
        val cutoff = now - TikTokCopyRetention.WINDOW_MILLIS
        clearDisplayCache(
            "source_app = ? AND cache_fetched_at IS NOT NULL AND cache_fetched_at > 0 AND cache_fetched_at < ?",
            listOf(SourceApp.TikTok.name, cutoff),
        )
    }

    private fun clearDisplayCache(where: String, args: List<Any?>) {
        sql.exec(
            """
            UPDATE history SET
              title = '', author_name = '', handle = '', caption = '',
              thumb_url = NULL, pfp_url = NULL, pinned_thumb_path = NULL, pinned_pfp_path = NULL,
              media_type = 'none', cache_fetched_at = NULL
            WHERE $where
            """.trimIndent(),
            args,
        )
    }

    private fun stampTikTokCache(url: String, snapshot: HistoryView, viewedAt: Long) {
        val hasCache = snapshot.title.isNotBlank() || snapshot.authorName.isNotBlank() ||
            snapshot.handle.isNotBlank() || snapshot.caption.isNotBlank() || !snapshot.thumbUrl.isNullOrBlank()
        sql.exec(
            "UPDATE history SET pfp_url = NULL, cache_fetched_at = ? WHERE url = ?",
            listOf(if (hasCache) viewedAt else null, url),
        )
    }

    private fun insert(entry: HistoryEntry) {
        sql.exec(
            """
            INSERT INTO history (
              url, source, source_app, title, author_name, handle, caption, thumb_url, pfp_url,
              pinned_thumb_path, pinned_pfp_path, media_type, note,
              first_viewed_at, last_viewed_at, view_count, starred_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            listOf(
                entry.url,
                entry.source,
                entry.sourceApp.name,
                entry.title,
                entry.authorName,
                entry.handle,
                entry.caption,
                entry.thumbUrl,
                entry.pfpUrl,
                entry.pinnedThumbPath,
                entry.pinnedPfpPath,
                entry.mediaType,
                entry.note,
                entry.firstViewedAt,
                entry.lastViewedAt,
                entry.viewCount,
                entry.starredAt,
            ),
        )
    }

    private fun HistorySqlRow.toEntry(): HistoryEntry = HistoryEntry(
        url = text("url").orEmpty(),
        source = text("source").orEmpty(),
        sourceApp = text("source_app")?.let { name ->
            SourceApp.entries.firstOrNull { it.name == name }
        } ?: SourceApp.Other,
        title = text("title").orEmpty(),
        authorName = text("author_name").orEmpty(),
        handle = text("handle").orEmpty(),
        caption = text("caption").orEmpty(),
        thumbUrl = text("thumb_url"),
        pfpUrl = text("pfp_url"),
        pinnedThumbPath = text("pinned_thumb_path"),
        pinnedPfpPath = text("pinned_pfp_path"),
        mediaType = text("media_type").orEmpty(),
        note = text("note")?.takeIf { it.isNotEmpty() },
        firstViewedAt = long("first_viewed_at") ?: 0L,
        lastViewedAt = long("last_viewed_at") ?: 0L,
        viewCount = long("view_count")?.toInt() ?: 0,
        starredAt = long("starred_at"),
    )

    private fun HistoryView.normalized(url: String): HistoryView = copy(
        url = url,
        source = source.ifBlank { HistoryUrls.sourceLabel(url) },
        title = title.trim().take(HISTORY_TEXT_LIMIT),
        authorName = authorName.trim().take(HISTORY_TEXT_LIMIT),
        handle = handle.trim().removePrefix("@").take(80),
        caption = caption.trim().take(HISTORY_TEXT_LIMIT),
    )

    private fun StarCopy.normalized(): StarCopy = StarCopy(
        title = title.trim().take(HISTORY_TEXT_LIMIT),
        authorName = authorName.trim().take(HISTORY_TEXT_LIMIT),
        handle = handle.trim().removePrefix("@").take(80),
        caption = caption.trim().take(HISTORY_TEXT_LIMIT),
        thumbUrl = thumbUrl?.takeIf { it.isNotBlank() },
        pfpUrl = pfpUrl?.takeIf { it.isNotBlank() },
    )

    private fun HistoryView.asEntry(
        firstViewedAt: Long,
        lastViewedAt: Long,
        viewCount: Int,
        starredAt: Long?,
        pinnedThumbPath: String?,
        pinnedPfpPath: String?,
    ): HistoryEntry = HistoryEntry(
        url = url,
        source = source,
        sourceApp = HistoryUrls.sourceApp(url, source),
        title = title,
        authorName = authorName,
        handle = handle,
        caption = caption,
        thumbUrl = thumbUrl,
        pfpUrl = pfpUrl,
        pinnedThumbPath = pinnedThumbPath,
        pinnedPfpPath = pinnedPfpPath,
        mediaType = HistoryLedger.normalizeMedia(mediaType, thumbUrl),
        note = null,
        firstViewedAt = firstViewedAt,
        lastViewedAt = lastViewedAt,
        viewCount = viewCount,
        starredAt = starredAt,
    )

    private companion object {
        const val SELECT_ROW = """
            SELECT url, source, source_app, title, author_name, handle, caption, thumb_url, pfp_url,
              pinned_thumb_path, pinned_pfp_path, media_type, note,
              first_viewed_at, last_viewed_at, view_count, starred_at
            FROM history
        """
    }
}
