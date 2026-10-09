package app.pane.android.ui.home

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.pane.android.R
import app.pane.android.data.webview.EmbedWebProfiles
import app.pane.android.domain.model.SourceApp
import app.pane.android.domain.source.ConsentLine
import app.pane.android.domain.source.EmbedNote
import app.pane.android.domain.source.hasSourceDetail
import app.pane.android.domain.source.secondLine
import app.pane.android.ui.history.sourceMark
import app.pane.android.ui.tiktok.TikTokConsentLinks
import app.pane.android.ui.youtube.YouTubeConsentLinks
import java.text.DateFormat
import java.util.Date
import java.util.Locale

data class SourceLinkUi(val label: String, val url: String)

data class SourceDetailUi(
    val body: String,
    val status: String?,
    val links: List<SourceLinkUi>,
    val canWithdraw: Boolean,
    val withdrawTitle: String,
    val withdrawBody: String,
)

data class SourceRowUi(
    val id: String,
    val title: String,
    val mark: Int?,
    val shown: Boolean,
    val subtitle: String?,
    val switchLabel: String,
    val detail: SourceDetailUi?,
)

@Composable
fun rememberSourceRows(
    shown: Map<SourceApp, Boolean>,
    youTubeAgreedAt: Long?,
    tikTokAgreedAt: Long?,
): List<SourceRowUi> {
    val locale = Locale.getDefault()
    return listOf(SourceApp.X, SourceApp.Facebook, SourceApp.Instagram, SourceApp.YouTube, SourceApp.TikTok, SourceApp.Bluesky).map { app ->
        val name = stringResource(sourceName(app))
        val on = shown[app] != false
        val line = secondLine(
            app,
            agreedAt = if (app == SourceApp.YouTube) youTubeAgreedAt else if (app == SourceApp.TikTok) tikTokAgreedAt else null,
            allowedAt = null,
        )
        val status = line?.let { statusText(it.status, it.atEpochMillis, locale) }
        val note = if (on) line?.let { stringResource(noteRes(it.note)) } else stringResource(R.string.source_off_note, name)
        val subtitle = listOfNotNull(note, status).joinToString(" · ").ifBlank { null }
        val detail = if (hasSourceDetail(app) && line != null) {
            SourceDetailUi(
                body = stringResource(detailRes(line.note)),
                status = status,
                links = linksFor(line.note),
                canWithdraw = line.status == ConsentLine.Agreed || line.status == ConsentLine.Allowed,
                withdrawTitle = stringResource(R.string.consent_withdraw_title, name),
                withdrawBody = stringResource(
                    withdrawBodyRes(line.note, EmbedWebProfiles.expectsFullProfileClear(profileName(line.note))),
                ),
            )
        } else {
            null
        }
        SourceRowUi(
            id = app.name,
            title = name,
            mark = sourceMark(app),
            shown = on,
            subtitle = subtitle,
            switchLabel = stringResource(R.string.a11y_source_show_in_pane, name),
            detail = detail,
        )
    }
}

internal fun consentDate(epochMillis: Long, locale: Locale = Locale.getDefault()): String =
    DateFormat.getDateInstance(DateFormat.MEDIUM, locale).format(Date(epochMillis))

@Composable
private fun statusText(status: ConsentLine, at: Long?, locale: Locale): String = when (status) {
    ConsentLine.Agreed -> stringResource(R.string.consent_status_agreed, consentDate(at ?: 0L, locale))
    ConsentLine.Allowed -> stringResource(R.string.consent_status_allowed, consentDate(at ?: 0L, locale))
    ConsentLine.Asks -> stringResource(R.string.consent_status_not_yet)
}

private fun sourceName(app: SourceApp): Int = when (app) {
    SourceApp.X -> R.string.source_x
    SourceApp.Facebook -> R.string.source_facebook
    SourceApp.Instagram -> R.string.source_instagram
    SourceApp.YouTube -> R.string.source_youtube
    SourceApp.TikTok -> R.string.source_tiktok
    SourceApp.Bluesky -> R.string.source_bluesky
    SourceApp.Threads -> R.string.source_threads
    SourceApp.Reddit -> R.string.source_reddit
    SourceApp.Other -> R.string.source_x
}

private fun noteRes(note: EmbedNote): Int = when (note) {
    EmbedNote.YouTube -> R.string.source_note_youtube
    EmbedNote.TikTok -> R.string.source_note_tiktok
    EmbedNote.Instagram -> R.string.source_note_instagram
    EmbedNote.Threads -> R.string.source_note_threads
}

private fun detailRes(note: EmbedNote): Int = when (note) {
    EmbedNote.YouTube -> R.string.consent_youtube_detail_body
    EmbedNote.TikTok -> R.string.consent_tiktok_detail_body
    EmbedNote.Instagram -> R.string.consent_instagram_detail_body
    EmbedNote.Threads -> R.string.consent_threads_detail_body
}

internal fun withdrawBodyRes(note: EmbedNote, fullClear: Boolean): Int = when (note) {
    EmbedNote.YouTube -> if (fullClear) R.string.consent_youtube_withdraw_body_full else R.string.consent_youtube_withdraw_body_partial
    EmbedNote.TikTok -> if (fullClear) R.string.consent_tiktok_withdraw_body_full else R.string.consent_tiktok_withdraw_body_partial
    EmbedNote.Instagram -> if (fullClear) R.string.consent_instagram_withdraw_body_full else R.string.consent_instagram_withdraw_body_partial
    EmbedNote.Threads -> if (fullClear) R.string.consent_threads_withdraw_body_full else R.string.consent_threads_withdraw_body_partial
}

/** Snackbar follows the clear that just ran, which can be partial even when the sheet promised a full clear. */
internal fun withdrawnSnackbarRes(clearedAllSiteData: Boolean): Int =
    if (clearedAllSiteData) R.string.consent_withdrawn_snackbar_full else R.string.consent_withdrawn_snackbar_partial

private fun profileName(note: EmbedNote): String = when (note) {
    EmbedNote.YouTube -> EmbedWebProfiles.YOUTUBE
    EmbedNote.TikTok -> EmbedWebProfiles.TIKTOK
    EmbedNote.Instagram -> EmbedWebProfiles.INSTAGRAM
    EmbedNote.Threads -> EmbedWebProfiles.THREADS
}

@Composable
private fun linksFor(note: EmbedNote): List<SourceLinkUi> {
    fun link(label: String, url: String) = url.takeIf { it.isNotBlank() }?.let { SourceLinkUi(label, it) }
    val panePrivacy = YouTubeConsentLinks.panePrivacy(Locale.getDefault().language)
    return when (note) {
        EmbedNote.YouTube -> listOfNotNull(
            link(stringResource(R.string.consent_link_yt_terms), YouTubeConsentLinks.YOUTUBE_TERMS),
            link(stringResource(R.string.consent_link_google_privacy), YouTubeConsentLinks.GOOGLE_PRIVACY),
            link(stringResource(R.string.consent_link_pane_terms), ""),
            link(stringResource(R.string.consent_link_pane_privacy), panePrivacy),
            link(stringResource(R.string.consent_link_google_security), GOOGLE_SECURITY),
        )
        EmbedNote.TikTok -> listOfNotNull(
            link(stringResource(R.string.consent_link_pane_terms), ""),
            link(stringResource(R.string.consent_link_pane_privacy), panePrivacy),
            link(stringResource(R.string.consent_link_tiktok_terms), TikTokConsentLinks.TIKTOK_TERMS),
            link(stringResource(R.string.consent_link_tiktok_privacy), TikTokConsentLinks.TIKTOK_PRIVACY),
            link(stringResource(R.string.consent_link_tiktok_cookies), TikTokConsentLinks.TIKTOK_COOKIES),
        )
        EmbedNote.Instagram -> listOfNotNull(
            link(stringResource(R.string.consent_link_meta_privacy), META_PRIVACY),
            link(stringResource(R.string.consent_link_meta_cookies), META_COOKIES),
            link(stringResource(R.string.consent_link_pane_privacy), panePrivacy),
            link(stringResource(R.string.consent_link_instagram_terms), INSTAGRAM_TERMS),
        )
        EmbedNote.Threads -> listOfNotNull(
            link(stringResource(R.string.consent_link_meta_privacy), META_PRIVACY),
            link(stringResource(R.string.consent_link_threads_supp_privacy), THREADS_SUPPLEMENT),
            link(stringResource(R.string.consent_link_meta_cookies), META_COOKIES),
            link(stringResource(R.string.consent_link_pane_privacy), panePrivacy),
        )
    }
}

private const val GOOGLE_SECURITY = "https://security.google.com/settings/security/permissions"
private const val META_PRIVACY = "https://privacycenter.instagram.com/policy/"
private const val META_COOKIES = "https://privacycenter.instagram.com/policies/cookies/"
private const val INSTAGRAM_TERMS = "https://help.instagram.com/581066165581870"
private const val THREADS_SUPPLEMENT = "https://help.instagram.com/515230437301944"
