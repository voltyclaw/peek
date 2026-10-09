package app.pane.android.domain.model

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.max

enum class HistoryBucket { Today, Yesterday, ThisWeek, Older }

enum class HistoryGesture { Remove, Star, RemoveStar }

enum class HistoryEmptyKind { None, History, Starred, Search }

data class HistorySection(
    val bucket: HistoryBucket,
    val rows: List<HistoryEntry>,
)

/**
 * Ledger grouping and swipe decisions. The Starred chip groups by [HistoryEntry.starredAt].
 * All groups by [HistoryEntry.lastViewedAt]. Swipes unstar a starred row first.
 * A menu remove is not a swipe: it deletes the row and the star together.
 */
object HistoryLedger {
    const val NONE = "none"
    const val IMAGE = "image"
    const val VIDEO = "video"

    fun normalizeMedia(raw: String, thumbUrl: String?): String = when (raw.trim().lowercase()) {
        VIDEO -> VIDEO
        IMAGE, "photo", "carousel", "gallery" -> IMAGE
        NONE, "" -> NONE
        else -> if (thumbUrl.isNullOrBlank()) NONE else IMAGE
    }

    fun showsThumb(mediaType: String): Boolean {
        val kind = normalizeMedia(mediaType, null)
        return kind == IMAGE || kind == VIDEO
    }

    fun isVideo(mediaType: String): Boolean = normalizeMedia(mediaType, null) == VIDEO

    /** Left removes or unstars. Right stars, or unstars when the row is already starred. */
    fun gesture(left: Boolean, starred: Boolean): HistoryGesture = when {
        starred -> HistoryGesture.RemoveStar
        left -> HistoryGesture.Remove
        else -> HistoryGesture.Star
    }

    /** Hub swipe-left always leaves History alone. Right still stars or unstars. */
    fun hubGesture(left: Boolean, starred: Boolean): HistoryGesture =
        if (left) HistoryGesture.Remove else gesture(left = false, starred = starred)

    fun stamp(entry: HistoryEntry, scope: HistoryScope): Long =
        if (scope == HistoryScope.Starred) entry.starredAt ?: entry.lastViewedAt else entry.lastViewedAt

    fun group(
        rows: List<HistoryEntry>,
        scope: HistoryScope,
        now: Long,
        zone: ZoneId,
    ): List<HistorySection> {
        val sorted = rows.sortedWith(
            compareByDescending<HistoryEntry> { stamp(it, scope) }.thenBy { it.url },
        )
        return HistoryBucket.entries.mapNotNull { bucket ->
            val inBucket = sorted.filter { bucket(stamp(it, scope), now, zone) == bucket }
            inBucket.takeIf { it.isNotEmpty() }?.let { HistorySection(bucket, it) }
        }
    }

    fun bucket(at: Long, now: Long, zone: ZoneId): HistoryBucket {
        val days = daysBetween(at, now, zone)
        return when {
            days <= 0 -> HistoryBucket.Today
            days == 1L -> HistoryBucket.Yesterday
            days <= 6L -> HistoryBucket.ThisWeek
            else -> HistoryBucket.Older
        }
    }

    fun relTime(at: Long, now: Long, zone: ZoneId, locale: Locale = Locale.getDefault()): String {
        val days = daysBetween(at, now, zone)
        if (days <= 0) {
            val minutes = max(0L, (now - at) / 60_000)
            return if (minutes < 60) "${max(1L, minutes)}m" else "${minutes / 60}h"
        }
        if (days == 1L) return "Yesterday"
        val date = LocalDate.ofInstant(Instant.ofEpochMilli(at), zone)
        if (days <= 6L) return date.dayOfWeek.getDisplayName(TextStyle.FULL, locale)
        val month = date.month.getDisplayName(TextStyle.SHORT, locale)
        return "$month ${date.dayOfMonth}"
    }

    fun identity(source: SourceApp, handle: String, authorName: String): String {
        val clean = handle.trim().removePrefix("@")
        return when (source) {
            SourceApp.Reddit -> {
                val sub = clean.removePrefix("r/").trim()
                if (sub.isNotEmpty()) "r/$sub" else authorName.trim()
            }
            SourceApp.Facebook -> authorName.trim().ifBlank { clean }
            SourceApp.X, SourceApp.Instagram, SourceApp.Threads ->
                if (clean.isNotEmpty()) "@$clean" else authorName.trim()
            SourceApp.Other -> when {
                clean.isNotEmpty() -> "@$clean"
                else -> authorName.trim()
            }
        }
    }

    fun emptyKind(
        totalRows: Int,
        starredRows: Int,
        filteredRows: Int,
        scope: HistoryScope,
        apps: Set<SourceApp>,
        query: String,
    ): HistoryEmptyKind {
        if (filteredRows > 0) return HistoryEmptyKind.None
        if (query.isNotBlank() || apps.isNotEmpty()) return HistoryEmptyKind.Search
        if (scope == HistoryScope.Starred && starredRows == 0) return HistoryEmptyKind.Starred
        if (totalRows == 0) return HistoryEmptyKind.History
        return if (scope == HistoryScope.Starred) HistoryEmptyKind.Starred else HistoryEmptyKind.History
    }

    private fun daysBetween(at: Long, now: Long, zone: ZoneId): Long {
        val day = LocalDate.ofInstant(Instant.ofEpochMilli(at), zone)
        val today = LocalDate.ofInstant(Instant.ofEpochMilli(now), zone)
        return ChronoUnit.DAYS.between(day, today)
    }
}
