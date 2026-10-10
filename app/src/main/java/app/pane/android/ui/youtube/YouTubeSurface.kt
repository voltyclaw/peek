package app.pane.android.ui.youtube

import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import app.pane.android.R
import app.pane.android.data.webview.EmbedWebViewLiveness
import app.pane.android.ui.theme.Inter
import app.pane.android.ui.theme.PaneGround
import app.pane.android.ui.theme.PaneInk
import app.pane.android.ui.theme.PaneMuted
import app.pane.android.ui.theme.PaneOnFill
import app.pane.android.ui.theme.PaneTile
import java.util.Locale

/**
 * TODO: counsel review — draft consent copy, not legal advice.
 * The Pane privacy policy URL and the Israeli §11 notice are still placeholders.
 * Hebrew UI should open the Hebrew policy once those addresses exist.
 */
internal object YouTubeConsentLinks {
    const val YOUTUBE_TERMS = "https://www.youtube.com/t/terms"
    const val GOOGLE_PRIVACY = "https://policies.google.com/privacy"
    const val PANE_TERMS = ""
    const val PANE_PRIVACY = ""
    const val PANE_PRIVACY_HEBREW = ""
    const val ISRAELI_NOTICE = ""

    fun panePrivacy(language: String): String {
        val code = language.lowercase(Locale.US)
        return if (code == "he" || code == "iw") PANE_PRIVACY_HEBREW else PANE_PRIVACY
    }
}

data class YouTubeFrame(
    val embedHtml: String? = null,
    val blocked: Boolean = false,
    val embedOff: Boolean = false,
    val ageRestricted: Boolean = false,
    val portrait: Boolean = false,
    val title: String = "",
    val hostLine: String = "",
    val onPlay: () -> Unit = {},
    val onOpenLink: (String) -> Unit = {},
    val onLeave: () -> Unit = {},
    val onOpenInYouTube: () -> Unit = {},
)

/**
 * Poster until [YouTubeFrame.embedHtml] is set. The WebView is not created before that.
 * A Shorts block, an embed-off panel, or an age panel also keeps the IFrame unloaded.
 */
@Composable
internal fun YouTubeConsentSurface(
    frame: YouTubeFrame,
    modifier: Modifier = Modifier,
    language: String = Locale.getDefault().language,
) {
    when {
        frame.ageRestricted -> YouTubeNotice(
            stringResource(R.string.yt_age_title),
            stringResource(R.string.yt_age_body),
            frame.onOpenInYouTube,
            modifier,
        )
        frame.embedOff -> YouTubeNotice(
            stringResource(R.string.yt_embed_off_title),
            stringResource(R.string.yt_embed_off_body),
            frame.onOpenInYouTube,
            modifier,
        )
        frame.embedHtml == null -> YouTubeConsentPoster(frame.title, frame.hostLine, frame.onPlay, frame.onOpenLink, language, modifier)
        else -> YouTubeEmbed(frame.embedHtml, frame.portrait, frame.onLeave, modifier)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun YouTubeConsentPoster(
    title: String,
    hostLine: String,
    onPlay: () -> Unit,
    onOpenLink: (String) -> Unit,
    language: String,
    modifier: Modifier = Modifier,
) {
    val button = stringResource(R.string.a11y_yt_consent_button)
    val paneTerms = stringResource(R.string.consent_link_pane_terms)
    val panePrivacy = stringResource(R.string.yt_consent_link_pane_privacy)
    val terms = stringResource(R.string.yt_consent_link_yt_terms)
    val google = stringResource(R.string.yt_consent_link_google_privacy)
    val line = stringResource(R.string.yt_consent_line, "\u0001", "\u0002", "\u0003", "\u0004")
    val links = listOf(
        "\u0001" to (paneTerms to YouTubeConsentLinks.PANE_TERMS),
        "\u0002" to (panePrivacy to YouTubeConsentLinks.panePrivacy(language)),
        "\u0003" to (terms to YouTubeConsentLinks.YOUTUBE_TERMS),
        "\u0004" to (google to YouTubeConsentLinks.GOOGLE_PRIVACY),
    )
    Column(
        modifier = modifier.fillMaxWidth().background(PaneGround).padding(horizontal = 20.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(12.dp)).background(PaneTile),
            contentAlignment = Alignment.Center,
        ) {
            if (title.isNotBlank()) {
                Text(
                    text = title,
                    color = PaneInk,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp),
                    style = TextStyle(fontFamily = Inter, fontSize = 16.sp, fontWeight = FontWeight.Medium),
                )
            }
        }
        Text(
            text = stringResource(R.string.yt_consent_heading),
            color = PaneInk,
            textAlign = TextAlign.Center,
            style = TextStyle(fontFamily = Inter, fontSize = 16.sp, fontWeight = FontWeight.Medium),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(PaneInk)
                .clickable(role = Role.Button, onClick = onPlay)
                .semantics { contentDescription = button }
                .padding(horizontal = 20.dp, vertical = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.yt_consent_button),
                color = PaneOnFill,
                textAlign = TextAlign.Center,
                style = TextStyle(fontFamily = Inter, fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
            )
        }
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalArrangement = Arrangement.Center,
        ) {
            consentPieces(line, links).forEach { piece ->
                if (piece.url == null) {
                    Text(
                        text = piece.label,
                        color = PaneMuted,
                        style = TextStyle(fontFamily = Inter, fontSize = 14.sp, lineHeight = 20.sp),
                    )
                } else {
                    Text(
                        text = piece.label,
                        color = PaneInk,
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .clickable(role = Role.Button, enabled = piece.url.isNotBlank()) {
                                if (piece.url.isNotBlank()) onOpenLink(piece.url)
                            }
                            .padding(horizontal = 2.dp),
                        style = TextStyle(fontFamily = Inter, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Bold),
                    )
                }
            }
        }
        if (hostLine.isNotBlank()) {
            Text(
                text = hostLine,
                color = PaneMuted,
                textAlign = TextAlign.Center,
                style = TextStyle(fontFamily = Inter, fontSize = 12.sp),
            )
        }
    }
}

internal data class ConsentPiece(val label: String, val url: String?)

internal fun consentPieces(template: String, links: List<Pair<String, Pair<String, String>>>): List<ConsentPiece> {
    val pieces = mutableListOf<ConsentPiece>()
    var rest = template
    while (rest.isNotEmpty()) {
        val next = links.mapNotNull { (token, value) ->
            val at = rest.indexOf(token)
            if (at < 0) null else at to (token to value)
        }.minByOrNull { it.first }
        if (next == null) {
            pieces += ConsentPiece(rest, null)
            break
        }
        val (at, match) = next
        if (at > 0) pieces += ConsentPiece(rest.substring(0, at), null)
        pieces += ConsentPiece(match.second.first, match.second.second)
        rest = rest.substring(at + match.first.length)
    }
    return pieces
}

@Composable
private fun YouTubeNotice(
    title: String,
    body: String,
    onOpenInYouTube: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().background(PaneGround).padding(horizontal = 20.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = title,
            color = PaneInk,
            textAlign = TextAlign.Center,
            style = TextStyle(fontFamily = Inter, fontSize = 20.sp, fontWeight = FontWeight.Medium),
        )
        Text(
            text = body,
            color = PaneMuted,
            textAlign = TextAlign.Center,
            style = TextStyle(fontFamily = Inter, fontSize = 15.sp, lineHeight = 22.sp),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(PaneInk)
                .clickable(role = Role.Button, onClick = onOpenInYouTube)
                .padding(horizontal = 20.dp, vertical = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.open_in_named_app, stringResource(R.string.source_youtube)),
                color = PaneOnFill,
                style = TextStyle(fontFamily = Inter, fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
            )
        }
    }
}

/**
 * Official IFrame player. Nothing from Pane is drawn on top of the WebView.
 * When the video ends, the WebView is replaced by a leave/replay card.
 */
@Composable
private fun YouTubeEmbed(
    html: String,
    portrait: Boolean,
    onLeave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var ended by remember(html) { mutableStateOf(false) }
    var generation by remember(html) { mutableIntStateOf(0) }
    val ratio = if (portrait) 4f / 5f else 16f / 9f
    if (ended) {
        Column(
            modifier = modifier.fillMaxWidth().aspectRatio(ratio).background(PaneTile).padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        ) {
            Text(
                text = stringResource(R.string.yt_replay),
                color = PaneInk,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .clickable(role = Role.Button) {
                        ended = false
                        generation += 1
                    }
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                style = TextStyle(fontFamily = Inter, fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
            )
            Text(
                text = stringResource(R.string.yt_leave),
                color = PaneMuted,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .clickable(role = Role.Button, onClick = onLeave)
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                style = TextStyle(fontFamily = Inter, fontSize = 16.sp),
            )
        }
        return
    }
    AndroidView(
        modifier = modifier.fillMaxWidth().aspectRatio(ratio).background(PaneTile),
        factory = { context ->
            EmbedWebViewLiveness.acquire()
            try {
                WebView(context).apply {
                    app.pane.android.data.webview.EmbedWebProfiles.assign(this, app.pane.android.data.webview.EmbedWebProfiles.YOUTUBE)
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.mediaPlaybackRequiresUserGesture = false
                    addJavascriptInterface(
                        object {
                            @JavascriptInterface
                            fun onEnded() {
                                post { ended = true }
                            }
                        },
                        "Pane",
                    )
                    loadDataWithBaseURL("https://app.pane.android", html, "text/html", "UTF-8", null)
                }
            } catch (error: Throwable) {
                EmbedWebViewLiveness.release()
                throw error
            }
        },
        update = { view ->
            if (view.tag != generation) {
                view.tag = generation
                view.loadDataWithBaseURL("https://app.pane.android", html, "text/html", "UTF-8", null)
            }
        },
        onRelease = { view ->
            try {
                view.stopLoading()
                view.loadUrl("about:blank")
                view.destroy()
            } finally {
                EmbedWebViewLiveness.release()
            }
        },
    )
}
