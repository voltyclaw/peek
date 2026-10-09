package app.pane.android.ui.tiktok

import android.annotation.SuppressLint
import android.app.Activity
import android.content.pm.ActivityInfo
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material3.Icon
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.withLink
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import app.pane.android.R
import app.pane.android.domain.tiktok.TikTokCaption
import app.pane.android.domain.tiktok.TikTokIds
import app.pane.android.domain.tiktok.TikTokPlayback
import app.pane.android.ui.model.ViewerPostUiModel
import app.pane.android.ui.theme.PaneHandle
import app.pane.android.ui.theme.Inter
import app.pane.android.ui.theme.PaneGround
import app.pane.android.ui.theme.PaneInk
import app.pane.android.ui.theme.PaneMuted
import app.pane.android.ui.theme.PaneOnFill
import app.pane.android.ui.theme.PaneTile

/**
 * TODO: counsel review — TikTok agreement copy is a draft. Guard owns the final wording and Hebrew.
 * Pane Terms and Pane Privacy stay blank until those URLs exist. TikTok's privacy URL is the
 * region-neutral link; Guard still confirms it.
 */
internal object TikTokConsentLinks {
    const val PANE_TERMS = ""
    const val PANE_PRIVACY = ""
    const val TIKTOK_TERMS = "https://www.tiktok.com/legal/terms-of-service"
    const val TIKTOK_PRIVACY = "https://www.tiktok.com/legal/privacy-policy"
    const val TIKTOK_COOKIES = "https://www.tiktok.com/legal/cookie-policy"
}

data class TikTokFrame(
    val embedHtml: String? = null,
    val embedOff: Boolean = false,
    val removed: Boolean = false,
    val live: Boolean = false,
    val hostLine: String = "",
    val onPlay: () -> Unit = {},
    val onOpenLink: (String) -> Unit = {},
    val onOpenInTikTok: () -> Unit = {},
    val onGone: () -> Unit = {},
    val onEmbedOff: () -> Unit = {},
)

@Composable
internal fun TikTokConsentSurface(frame: TikTokFrame, modifier: Modifier = Modifier) {
    val configuration = LocalConfiguration.current
    val cap = (configuration.screenHeightDp * 0.68f).dp
    val width = minOf(configuration.screenWidthDp.dp, cap * 9f / 16f)
    val height = minOf(width * 16f / 9f, cap)
    var gone by remember(frame.embedHtml, frame.removed) { mutableStateOf(frame.removed) }
    var blocked by remember(frame.embedHtml, frame.embedOff) { mutableStateOf(frame.embedOff) }
    Box(modifier.fillMaxWidth().background(PaneGround), contentAlignment = Alignment.Center) {
        Box(Modifier.width(width).height(height)) {
            when {
                frame.live -> TikTokNotice(stringResource(R.string.tt_live_title), frame.onOpenInTikTok)
                gone -> TikTokNotice(stringResource(R.string.tt_gone_title), frame.onOpenInTikTok)
                blocked -> TikTokNotice(
                    stringResource(R.string.tt_embed_off_title),
                    frame.onOpenInTikTok,
                    stringResource(R.string.tt_embed_off_body),
                )
                frame.embedHtml == null -> TikTokPoster(frame.hostLine, frame.onPlay, frame.onOpenLink)
                else -> TikTokEmbed(frame.embedHtml, frame.onOpenLink) { code ->
                    when (code) {
                        1001 -> {
                            gone = true
                            frame.onGone()
                        }
                        2001, 3001 -> {
                            blocked = true
                            frame.onEmbedOff()
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TikTokPoster(hostLine: String, onPlay: () -> Unit, onOpenLink: (String) -> Unit) {
    val button = stringResource(R.string.a11y_tt_play)
    Column(
        Modifier.fillMaxWidth().background(PaneGround).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(RoundedCornerShape(14.dp)).background(PaneInk)
                .clickable(role = Role.Button, onClick = onPlay)
                .semantics { role = Role.Button; contentDescription = button },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                stringResource(R.string.tt_play_on_tiktok),
                color = PaneOnFill,
                style = TextStyle(fontFamily = Inter, fontSize = 16.sp, fontWeight = FontWeight.Medium),
            )
        }
        val line = stringResource(R.string.tt_agree_line)
        Agreement(line, onOpenLink)
        if (hostLine.isNotBlank()) {
            Text(hostLine, color = PaneMuted, style = TextStyle(fontFamily = Inter, fontSize = 12.sp), textAlign = TextAlign.Center)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Agreement(line: String, onOpenLink: (String) -> Unit) {
    val paneTerms = line.indexOf("Pane's Terms")
    val firstPrivacy = line.indexOf("Privacy Policy")
    val tiktokTerms = line.indexOf("TikTok's Terms")
    val secondPrivacy = line.indexOf("Privacy Policy", (firstPrivacy + 1).coerceAtLeast(0))
    val pieces = listOf(
        paneTerms to (paneTerms + "Pane's Terms".length) to TikTokConsentLinks.PANE_TERMS,
        firstPrivacy to (firstPrivacy + "Privacy Policy".length) to TikTokConsentLinks.PANE_PRIVACY,
        tiktokTerms to (tiktokTerms + "TikTok's Terms".length) to TikTokConsentLinks.TIKTOK_TERMS,
        secondPrivacy to (secondPrivacy + "Privacy Policy".length) to TikTokConsentLinks.TIKTOK_PRIVACY,
    ).filter { (range, _) -> range.first >= 0 }
    FlowRow(horizontalArrangement = Arrangement.Center) {
        Text(
            buildAnnotatedString {
                var cursor = 0
                pieces.sortedBy { it.first.first }.forEach { (range, url) ->
                    if (range.first > cursor) append(line.substring(cursor, range.first))
                    if (url.isNotBlank()) {
                        pushStringAnnotation("url", url)
                        withStyle(SpanStyle(color = PaneInk)) { append(line.substring(range.first, range.second)) }
                        pop()
                    } else {
                        append(line.substring(range.first, range.second))
                    }
                    cursor = range.second
                }
                if (cursor < line.length) append(line.substring(cursor))
            },
            color = PaneMuted,
            style = TextStyle(fontFamily = Inter, fontSize = 13.sp, lineHeight = 18.sp, textAlign = TextAlign.Center),
            modifier = Modifier.clickable {
                // Link taps are handled below by the annotation walk in the poster button's sibling.
            },
        )
    }
    pieces.filter { it.second.isNotBlank() }.forEach { (range, url) ->
        Text(
            text = line.substring(range.first, range.second),
            modifier = Modifier
                .heightIn(min = 48.dp)
                .clickable(role = Role.Button) { onOpenLink(url) }
                .padding(horizontal = 4.dp),
            color = PaneInk,
            style = TextStyle(fontFamily = Inter, fontSize = 13.sp, fontWeight = FontWeight.Medium),
        )
    }
}

@Composable
private fun TikTokNotice(title: String, onOpen: () -> Unit, body: String? = null) {
    Column(
        Modifier.fillMaxWidth().background(PaneTile).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(title, color = PaneInk, style = TextStyle(fontFamily = Inter, fontSize = 16.sp, fontWeight = FontWeight.Medium))
        if (body != null) {
            Text(body, color = PaneMuted, style = TextStyle(fontFamily = Inter, fontSize = 13.sp, lineHeight = 18.sp))
        }
        Text(
            text = stringResource(R.string.open_in_named_app, stringResource(R.string.source_tiktok)),
            modifier = Modifier.heightIn(min = 48.dp).clickable(role = Role.Button, onClick = onOpen),
            color = PaneInk,
            style = TextStyle(fontFamily = Inter, fontSize = 15.sp, fontWeight = FontWeight.Medium),
        )
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun TikTokEmbed(html: String, onOpenLink: (String) -> Unit, onPlayerError: (Int) -> Unit) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val density = LocalDensity.current
    val screenHeight = with(density) { LocalConfiguration.current.screenHeightDp.dp.toPx() }
    var webView by remember { mutableStateOf<WebView?>(null) }
    var customView by remember { mutableStateOf<View?>(null) }
    var customCallback by remember { mutableStateOf<WebChromeClient.CustomViewCallback?>(null) }
    var playing by remember { mutableStateOf(false) }
    val activity = context as? Activity
    BackHandler(enabled = customView != null) {
        customCallback?.onCustomViewHidden()
        customView = null
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    }
    Box(Modifier.fillMaxWidth().onGloballyPositioned { coords ->
        val view = webView ?: return@onGloballyPositioned
        val top = coords.positionInWindow().y
        val bottom = top + coords.size.height
        val visibleTop = top.coerceAtLeast(0f)
        val visibleBottom = bottom.coerceAtMost(screenHeight)
        val fraction = ((visibleBottom - visibleTop).coerceAtLeast(0f) / coords.size.height.coerceAtLeast(1)).toFloat()
        val play = TikTokPlayback.shouldPlay(fraction)
        if (play && !playing) {
            view.evaluateJavascript("send('mute'); send('play');", null)
            playing = true
        } else if (!play && playing) {
            view.evaluateJavascript("send('pause');", null)
            playing = false
        }
    }) {
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.mediaPlaybackRequiresUserGesture = false
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                            val target = request.url?.toString().orEmpty()
                            if (target.contains("/player/v1/")) return false
                            if (target.isBlank() || target.startsWith("about:")) return false
                            onOpenLink(target)
                            return true
                        }
                    }
                    webChromeClient = object : WebChromeClient() {
                        override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                            customView = view
                            customCallback = callback
                            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR
                        }

                        override fun onHideCustomView() {
                            customView = null
                            customCallback?.onCustomViewHidden()
                            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                        }
                    }
                    addJavascriptInterface(object {
                        @JavascriptInterface fun onEnded() = Unit
                        @JavascriptInterface fun onError(code: Int) {
                            post {
                                if (code == 1001 || code == 2001 || code == 3001) loadUrl("about:blank")
                                onPlayerError(code)
                            }
                        }
                    }, "Pane")
                    loadDataWithBaseURL("https://app.pane.android", html, "text/html", "utf-8", null)
                    webView = this
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        customView?.let { shown ->
            AndroidView(factory = { shown }, modifier = Modifier.fillMaxWidth())
        }
    }
    DisposableEffect(lifecycle, webView) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) webView?.evaluateJavascript("send('pause');", null)
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            (webView?.parent as? ViewGroup)?.removeView(webView)
            webView?.destroy()
        }
    }
}

@Composable
internal fun TikTokDetails(
    authorName: String,
    handle: String,
    caption: String,
    postedAtEpochSeconds: Long?,
    detailsFailed: Boolean,
    removed: Boolean,
    live: Boolean,
    embedOff: Boolean,
    onOpenProfile: (String) -> Unit,
    onOpenMention: (String) -> Unit,
    onOpenComments: () -> Unit,
    onRetry: () -> Unit,
) {
    val cleanHandle = handle.removePrefix("@").trim()
    val name = authorName.trim()
    if (name.isNotBlank() || cleanHandle.isNotBlank()) {
        val profile = cleanHandle.takeIf { it.isNotBlank() }?.let(TikTokCaption::profileUrl).orEmpty()
        val description = stringResource(
            R.string.a11y_open_tiktok_profile,
            name.ifBlank { cleanHandle },
            cleanHandle.let { if (it.isBlank()) "" else "@$it" },
        )
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clickable(enabled = profile.isNotBlank(), role = Role.Button) { onOpenProfile(profile) }
                .semantics { contentDescription = description },
        ) {
            if (name.isNotBlank()) {
                Text(
                    name,
                    color = PaneInk,
                    style = TextStyle(fontFamily = Inter, fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
                )
            }
            if (cleanHandle.isNotBlank()) {
                Text(
                    "@$cleanHandle",
                    color = PaneHandle,
                    style = TextStyle(fontFamily = Inter, fontSize = 14.sp, fontWeight = FontWeight.Medium),
                )
            }
        }
    } else if (detailsFailed) {
        Text(
            stringResource(R.string.tiktok_post_fallback),
            color = PaneInk,
            style = TextStyle(fontFamily = Inter, fontSize = 16.sp, fontWeight = FontWeight.Medium),
        )
    }
    if (detailsFailed) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.tt_details_failed),
                color = PaneMuted,
                style = TextStyle(fontFamily = Inter, fontSize = 14.sp),
            )
            Text(
                stringResource(R.string.try_again),
                modifier = Modifier.heightIn(min = 48.dp).clickable(role = Role.Button, onClick = onRetry),
                color = PaneInk,
                style = TextStyle(fontFamily = Inter, fontSize = 14.sp, fontWeight = FontWeight.Medium),
            )
        }
    }
    if (caption.isNotBlank()) TikTokCaptionBlock(caption, onOpenMention)
    if (postedAtEpochSeconds != null) {
        Text(
            TikTokIds.label(postedAtEpochSeconds, System.currentTimeMillis()),
            color = PaneMuted,
            style = TextStyle(fontFamily = Inter, fontSize = 13.sp),
        )
    }
    if (!removed && !live && !embedOff) {
        val comments = stringResource(R.string.a11y_tt_comments)
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(PaneTile)
                .clickable(role = Role.Button, onClick = onOpenComments)
                .semantics { contentDescription = comments }
                .padding(horizontal = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.tt_comments_live_on_tiktok),
                color = Color(0xFFDAD4CE),
                style = TextStyle(fontFamily = Inter, fontSize = 15.sp),
            )
            Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null, tint = Color(0xFFDAD4CE))
        }
    }
}

@Composable
private fun TikTokCaptionBlock(caption: String, onOpenMention: (String) -> Unit) {
    var expanded by remember(caption) { mutableStateOf(false) }
    var canExpand by remember(caption) { mutableStateOf(false) }
    SelectionContainer {
        Text(
            text = buildAnnotatedString {
                TikTokCaption.pieces(caption).forEach { piece ->
                    val mention = piece.handle
                    if (mention == null) {
                        append(piece.text)
                    } else {
                        withLink(
                            LinkAnnotation.Clickable(
                                tag = mention,
                                styles = TextLinkStyles(SpanStyle(color = PaneHandle)),
                            ) {
                                onOpenMention(TikTokCaption.profileUrl(mention))
                            },
                        ) {
                            append(piece.text)
                        }
                    }
                }
            },
            color = PaneInk,
            maxLines = if (expanded) Int.MAX_VALUE else 3,
            overflow = TextOverflow.Ellipsis,
            onTextLayout = { if (it.hasVisualOverflow) canExpand = true },
            style = TextStyle(fontFamily = Inter, fontSize = 15.sp, lineHeight = 22.sp),
        )
    }
    if (canExpand || expanded) {
        Text(
            stringResource(if (expanded) R.string.caption_less else R.string.caption_more),
            modifier = Modifier.heightIn(min = 48.dp).clickable(role = Role.Button) { expanded = !expanded },
            color = PaneMuted,
            style = TextStyle(fontFamily = Inter, fontSize = 14.sp, fontWeight = FontWeight.Medium),
        )
    }
}
