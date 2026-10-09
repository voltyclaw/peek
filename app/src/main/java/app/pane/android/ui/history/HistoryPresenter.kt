package app.pane.android.ui.history

import androidx.annotation.DrawableRes
import app.pane.android.R
import app.pane.android.data.history.HistoryQueries
import app.pane.android.domain.model.HistoryBucket
import app.pane.android.domain.model.HistoryEmptyKind
import app.pane.android.domain.model.HistoryEntry
import app.pane.android.domain.model.HistoryLedger
import app.pane.android.domain.model.HistoryQuery
import app.pane.android.domain.model.HistoryScope
import app.pane.android.data.youtube.YouTubeUrls
import app.pane.android.domain.model.RowTitles
import app.pane.android.domain.model.SourceApp
import app.pane.android.domain.model.YouTubeRowCopy
import app.pane.android.domain.text.UnicodeEscapes
import app.pane.android.domain.tiktok.TikTokLinks
import app.pane.android.ui.model.LedgerRowUi
import app.pane.android.ui.model.UiImage
import java.time.ZoneId
import java.util.Locale

data class HistorySectionUi(
    val title: String,
    val rows: List<LedgerRowUi>,
)

data class HistoryListUi(
    val scopeStarred: Boolean,
    val apps: List<SourceApp>,
    val selectedApps: Set<SourceApp>,
    val sections: List<HistorySectionUi>,
    val empty: HistoryEmptyKind,
)

object HistoryPresenter {
    fun present(
        rows: List<HistoryEntry>,
        query: HistoryQuery,
        now: Long,
        zone: ZoneId,
        locale: Locale = Locale.getDefault(),
    ): HistoryListUi {
        val filtered = HistoryQueries.filter(rows, query)
        val sections = HistoryLedger.group(filtered, query.scope, now, zone).map { section ->
            HistorySectionUi(
                title = bucketTitle(section.bucket),
                rows = section.rows.map { entry -> row(entry, query, now, zone, locale) },
            )
        }
        return HistoryListUi(
            scopeStarred = query.scope == HistoryScope.Starred,
            apps = HistoryQueries.distinctApps(rows),
            selectedApps = query.apps,
            sections = sections,
            empty = HistoryLedger.emptyKind(
                totalRows = rows.size,
                starredRows = rows.count { it.starredAt != null },
                filteredRows = filtered.size,
                scope = query.scope,
                apps = query.apps,
                query = "",
            ),
        )
    }

    fun bucketTitle(bucket: HistoryBucket): String = when (bucket) {
        HistoryBucket.Today -> "TODAY"
        HistoryBucket.Yesterday -> "YESTERDAY"
        HistoryBucket.ThisWeek -> "THIS WEEK"
        HistoryBucket.Older -> "OLDER"
    }

    private fun row(
        entry: HistoryEntry,
        query: HistoryQuery,
        now: Long,
        zone: ZoneId,
        locale: Locale,
    ): LedgerRowUi {
        val stamp = HistoryLedger.stamp(entry, query.scope)
        val video = HistoryLedger.isVideo(entry.mediaType)
        val thumb = entry.thumbUrl?.takeIf { HistoryLedger.showsThumb(entry.mediaType) && it.isNotBlank() }
        val tiktokId = if (entry.sourceApp == SourceApp.TikTok) {
            TikTokLinks.parse(entry.url)?.videoId.orEmpty()
        } else {
            ""
        }
        val youtube = entry.sourceApp == SourceApp.YouTube
        val pfp = entry.pfpUrl?.takeIf { it.isNotBlank() }?.let(UiImage::Url)
            ?: if (entry.sourceApp == SourceApp.Other) {
                app.pane.android.domain.model.OtherTitles.faviconUrl(entry.url)?.let(UiImage::Url)
            } else {
                null
            }
        return LedgerRowUi(
            url = entry.url,
            title = rowTitle(entry),
            identity = rowIdentity(entry),
            timeLabel = HistoryLedger.relTime(stamp, now, zone, locale),
            pfp = pfp,
            sourceMark = sourceMark(entry.sourceApp),
            markAsAvatar = youtube && pfp == null,
            thumb = thumb?.let(UiImage::Url),
            video = video,
            starred = entry.starredAt != null,
            tiktokId = tiktokId,
            tiktokThumbUrl = if (tiktokId.isBlank()) null else entry.thumbUrl,
            globe = entry.sourceApp == SourceApp.Other && entry.pfpUrl.isNullOrBlank(),
        )
    }

    private fun rowTitle(entry: HistoryEntry): String {
        val title = UnicodeEscapes.decode(entry.title)
        val caption = UnicodeEscapes.decode(entry.caption)
        if (entry.sourceApp == SourceApp.YouTube) {
            return YouTubeRowCopy.title(
                title.ifBlank { caption },
                YouTubeUrls.videoId(entry.url).orEmpty(),
            )
        }
        if (entry.sourceApp == SourceApp.Bluesky && title.isBlank() && caption.isBlank()) return ""
        if (entry.sourceApp == SourceApp.X) {
            return RowTitles.xTitle(title, caption, entry.url, handle = UnicodeEscapes.decode(entry.handle))
        }
        return RowTitles.display(title, caption, entry.url)
    }

    private fun rowIdentity(entry: HistoryEntry): String {
        val author = UnicodeEscapes.decode(entry.authorName)
        val handle = UnicodeEscapes.decode(entry.handle)
        if (entry.sourceApp == SourceApp.YouTube) return YouTubeRowCopy.identity(author, handle)
        return HistoryLedger.identity(entry.sourceApp, handle, author)
            .ifBlank { if (entry.sourceApp == SourceApp.Bluesky) "bsky.app" else "" }
    }
}

@DrawableRes
fun sourceMark(app: SourceApp): Int? = when (app) {
    SourceApp.X -> R.drawable.ic_source_x
    SourceApp.Instagram -> R.drawable.ic_source_instagram
    SourceApp.Reddit -> R.drawable.ic_source_reddit
    SourceApp.Facebook -> R.drawable.ic_source_facebook
    SourceApp.YouTube -> R.drawable.ic_source_youtube
    SourceApp.TikTok, SourceApp.Threads, SourceApp.Bluesky, SourceApp.Other -> null
}
