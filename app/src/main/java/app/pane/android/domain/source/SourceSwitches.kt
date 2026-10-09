package app.pane.android.domain.source

import app.pane.android.domain.model.SourceApp

/**
 * Show-in-Pane switches. Absent means ON. Consent lives in other files and is never read here.
 * Threads stays off this list until that source ships. Reddit is not a row.
 * Bluesky has a row and no detail page.
 */
object SourceSwitches {
    val rows: List<SourceApp> = listOf(
        SourceApp.X,
        SourceApp.Facebook,
        SourceApp.Instagram,
        SourceApp.YouTube,
        SourceApp.TikTok,
        SourceApp.Bluesky,
    )

    /** Keys that meant "not consented" or an old off toggle. Migration drops them. */
    val legacyKeys: List<String> = listOf(
        "youtube_in_pane",
        "tiktok_in_pane",
        "play_youtube",
        "play_tiktok",
        "show_youtube_off",
        "show_tiktok_off",
        "settings_yt_off",
        "settings_tt_off",
    )

    fun key(app: SourceApp): String = "show_${app.name.lowercase()}"

    fun shown(stored: Map<String, Boolean>, app: SourceApp): Boolean = stored[key(app)] ?: true

    fun handsOff(app: SourceApp, stored: Map<String, Boolean>): Boolean =
        app in rows && !shown(stored, app)

    /**
     * 1.0.45 → 1.2.0. Every switch becomes ON. Legacy off keys are removed.
     * Keys this function does not own, including consent version and time, stay.
     */
    fun migrate(stored: MutableMap<String, Any?>) {
        legacyKeys.forEach { stored.remove(it) }
        rows.forEach { stored[key(it)] = true }
    }
}

enum class ConsentLine { Agreed, Allowed, Asks }

enum class EmbedNote { YouTube, TikTok, Instagram, Threads }

data class SourceSecondLine(val note: EmbedNote, val status: ConsentLine, val atEpochMillis: Long?)

/** Embed sources only. X, Facebook, and Bluesky have no second line and no detail page. */
fun secondLine(app: SourceApp, agreedAt: Long?, allowedAt: Long?): SourceSecondLine? = when (app) {
    SourceApp.YouTube -> SourceSecondLine(
        EmbedNote.YouTube,
        if (agreedAt != null && agreedAt > 0L) ConsentLine.Agreed else ConsentLine.Asks,
        agreedAt?.takeIf { it > 0L },
    )
    SourceApp.TikTok -> SourceSecondLine(
        EmbedNote.TikTok,
        if (agreedAt != null && agreedAt > 0L) ConsentLine.Agreed else ConsentLine.Asks,
        agreedAt?.takeIf { it > 0L },
    )
    SourceApp.Instagram -> SourceSecondLine(
        EmbedNote.Instagram,
        if (allowedAt != null && allowedAt > 0L) ConsentLine.Allowed else ConsentLine.Asks,
        allowedAt?.takeIf { it > 0L },
    )
    SourceApp.Threads -> SourceSecondLine(
        EmbedNote.Threads,
        if (allowedAt != null && allowedAt > 0L) ConsentLine.Allowed else ConsentLine.Asks,
        allowedAt?.takeIf { it > 0L },
    )
    else -> null
}

fun hasSourceDetail(app: SourceApp): Boolean = secondLine(app, null, null) != null

enum class OpenRoute { Viewer, Handoff }

/** Profile links and a hidden source skip the viewer. History is written only after a successful viewer load. */
fun openRoute(app: SourceApp, shown: Map<String, Boolean>, profile: Boolean): OpenRoute = when {
    profile -> OpenRoute.Handoff
    SourceSwitches.handsOff(app, shown) -> OpenRoute.Handoff
    else -> OpenRoute.Viewer
}
