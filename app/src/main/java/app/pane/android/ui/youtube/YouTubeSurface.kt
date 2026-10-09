package app.pane.android.ui.youtube

import android.webkit.WebView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
    const val GOOGLE_PRIVACY = "http://www.google.com/policies/privacy"
    const val PANE_PRIVACY = ""
    const val PANE_PRIVACY_HEBREW = ""
    const val ISRAELI_NOTICE = ""

    fun panePrivacy(language: String): String {
        val code = language.lowercase(Locale.US)
        return if (code == "he" || code == "iw") PANE_PRIVACY_HEBREW else PANE_PRIVACY
    }
}

data class YouTubeFrame(
    val embedUrl: String?,
    val onPlay: () -> Unit,
    val onOpenLink: (String) -> Unit,
)

/**
 * Poster until [YouTubeFrame.embedUrl] is set. The WebView is not created before that.
 */
@Composable
internal fun YouTubeConsentSurface(
    frame: YouTubeFrame,
    modifier: Modifier = Modifier,
    language: String = Locale.getDefault().language,
) {
    if (frame.embedUrl == null) {
        YouTubeConsentPoster(frame.onPlay, frame.onOpenLink, language, modifier)
    } else {
        YouTubeEmbed(frame.embedUrl, modifier)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun YouTubeConsentPoster(
    onPlay: () -> Unit,
    onOpenLink: (String) -> Unit,
    language: String,
    modifier: Modifier = Modifier,
) {
    val button = stringResource(R.string.a11y_yt_consent_button)
    val terms = stringResource(R.string.yt_consent_link_yt_terms)
    val google = stringResource(R.string.yt_consent_link_google_privacy)
    val pane = stringResource(R.string.yt_consent_link_pane_privacy)
    val line = stringResource(R.string.yt_consent_line, "\u0001", "\u0002", "\u0003")
    val links = listOf(
        "\u0001" to (terms to YouTubeConsentLinks.YOUTUBE_TERMS),
        "\u0002" to (google to YouTubeConsentLinks.GOOGLE_PRIVACY),
        "\u0003" to (pane to YouTubeConsentLinks.panePrivacy(language)),
    )
    Column(
        modifier = modifier.fillMaxWidth().background(PaneGround).padding(horizontal = 20.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
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
private fun YouTubeEmbed(url: String, modifier: Modifier = Modifier) {
    AndroidView(
        modifier = modifier.fillMaxWidth().height(220.dp).background(PaneTile),
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.mediaPlaybackRequiresUserGesture = true
                loadUrl(url)
            }
        },
        update = { view ->
            if (view.url != url) view.loadUrl(url)
        },
        onRelease = { view ->
            view.stopLoading()
            view.loadUrl("about:blank")
            view.destroy()
        },
    )
}
