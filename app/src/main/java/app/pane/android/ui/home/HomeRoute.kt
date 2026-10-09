package app.pane.android.ui.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import android.content.ClipData
import android.content.Intent
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.core.content.pm.PackageInfoCompat
import app.pane.android.R
import app.pane.android.domain.model.HistoryUndo
import app.pane.android.domain.model.RecentLink
import app.pane.android.domain.usecase.ExtractUrlFromTextUseCase
import app.pane.android.ui.actions.openExternally
import app.pane.android.ui.actions.sharePostIntent
import app.pane.android.ui.components.PaneSnackbarHost
import app.pane.android.ui.components.showForFiveSeconds
import app.pane.android.ui.model.HomeUiState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import app.pane.android.ui.media.VideoQuality
import app.pane.android.ui.navigation.BackBehavior
import app.pane.android.ui.navigation.SoundMode
import app.pane.android.ui.theme.ThemeMode
import kotlinx.coroutines.launch

@Composable
fun HomeRoute(
    uiState: HomeUiState,
    onOpenLink: (String) -> Unit,
    modifier: Modifier = Modifier,
    themeMode: ThemeMode = ThemeMode.System,
    onThemeMode: (ThemeMode) -> Unit = {},
    backBehavior: BackBehavior = BackBehavior.ClosePeek,
    onBackBehavior: (BackBehavior) -> Unit = {},
    videoQuality: VideoQuality = VideoQuality.Auto,
    onVideoQuality: (VideoQuality) -> Unit = {},
    soundMode: SoundMode = SoundMode.Muted,
    onSoundMode: (SoundMode) -> Unit = {},
    browserTrampoline: Boolean = false,
    onBrowserTrampoline: (Boolean) -> Unit = {},
    handoffBrowsers: List<HandoffBrowserOption> = emptyList(),
    handoffPackage: String = "",
    onHandoffBrowser: (String) -> Unit = {},
    onSetDefaultBrowser: () -> Unit = {},
    onRemoveRecent: (String) -> Unit = {},
    onClearRecents: () -> Unit = {},
    onOpenHistory: () -> Unit = {},
    onSwipeRecent: (String, Boolean) -> Unit = { url, left -> if (left) onRemoveRecent(url) },
    notices: Flow<HubNotice> = emptyFlow(),
    onUndoRecent: (RecentLink) -> Unit = {},
    onUndoHistory: (HistoryUndo) -> Unit = {},
    extractUrlFromText: ExtractUrlFromTextUseCase = ExtractUrlFromTextUseCase(),
    showDeveloperTools: Boolean = false,
    sampleGroups: List<SamplePickerGroup> = emptyList(),
    showSamplesInRecents: Boolean = false,
    onShowSamplesInRecents: (Boolean) -> Unit = {},
    onOpenSample: (String) -> Unit = {},
    sourceRows: List<SourceRowUi> = emptyList(),
    onSourceShown: (String, Boolean) -> Unit = { _, _ -> },
    onWithdrawSource: (String) -> Boolean = { false },
) {
    val clipboard = LocalClipboard.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val starredMessage = stringResource(R.string.starred_snack)
    val unstarredMessage = stringResource(R.string.unstarred_snack)
    val removedRecentsMessage = stringResource(R.string.recents_removed_snack)
    val undoLabel = stringResource(R.string.undo)
    val invalidClipboardMessage = stringResource(R.string.link_invalid)
    val nothingToPasteMessage = stringResource(R.string.nothing_to_paste)
    val usingFirstLink = stringResource(R.string.using_first_link)
    val linksKeptMessage = stringResource(R.string.link_settings_kept)
    val linksPartialMessage = stringResource(R.string.link_settings_partial)
    val linksStillOffMessage = stringResource(R.string.link_settings_still_off)
    val linksHandlingOffMessage = stringResource(R.string.link_settings_handling_off)
    val lifecycleOwner = LocalLifecycleOwner.current
    var openedLinkSettings by remember { mutableStateOf(false) }
    var showFirstLaunchHint by remember { mutableStateOf(!FirstLaunchPreferences.isDismissed(context)) }
    var leftLinkSettings by remember { mutableStateOf(false) }
    var openingLink by remember { mutableStateOf(false) }
    var linkDraft by rememberSaveable { mutableStateOf("") }
    var showLinkError by remember { mutableStateOf(false) }
    val versionLabel = remember(context) {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        val code = PackageInfoCompat.getLongVersionCode(info).toString()
        context.getString(R.string.app_version, info.versionName.orEmpty(), code)
    }
    fun openUrl(url: String) {
        if (openingLink) return
        openingLink = true
        onOpenLink(url)
    }

    fun readClipboard(onText: (CharSequence?) -> Unit) {
        scope.launch {
            val clipData = clipboard.getClipEntry()?.clipData
            val clipboardText = clipData
                ?.takeIf { it.itemCount > 0 }
                ?.getItemAt(0)
                ?.coerceToText(context)
            onText(clipboardText)
        }
    }

    fun pasteIntoField() {
        readClipboard { clipboardText ->
            val url = extractUrlFromText(clipboardText)
            when (pasteOutcome(clipboardText, url)) {
                PasteOutcome.Empty -> {
                    scope.launch { snackbarHostState.showSnackbar(nothingToPasteMessage) }
                    return@readClipboard
                }
                PasteOutcome.NotALink -> {
                    scope.launch { snackbarHostState.showSnackbar(invalidClipboardMessage) }
                    return@readClipboard
                }
                PasteOutcome.Ready -> Unit
            }
            val pasted = url ?: return@readClipboard
            linkDraft = pasted
            showLinkError = false
            if (extractUrlFromText.count(clipboardText) > 1) {
                scope.launch { snackbarHostState.showSnackbar(usingFirstLink, duration = SnackbarDuration.Short) }
            }
        }
    }

    fun submitDraft() {
        val url = extractUrlFromText(linkDraft)
        if (url == null) {
            showLinkError = true
            scope.launch { snackbarHostState.showSnackbar(invalidClipboardMessage) }
            return
        }
        showLinkError = false
        openUrl(url)
    }

    LaunchedEffect(notices) {
        notices.collect { notice ->
            when (notice) {
                is HubNotice.Removed -> snackbarHostState.showForFiveSeconds(removedRecentsMessage, undoLabel) {
                    onUndoRecent(notice.link)
                }
                HubNotice.Starred -> snackbarHostState.showForFiveSeconds(starredMessage)
                is HubNotice.Unstarred -> snackbarHostState.showForFiveSeconds(unstarredMessage, undoLabel) {
                    onUndoHistory(notice.undo)
                }
            }
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> if (openedLinkSettings) leftLinkSettings = true
                Lifecycle.Event.ON_RESUME -> {
                    openingLink = false
                    if (leftLinkSettings) {
                        leftLinkSettings = false
                        openedLinkSettings = false
                        val status = LinkSettings.read(context)
                        if (status != null) {
                            val report = LinkSettings.report(
                                linkHandlingAllowed = status.linkHandlingAllowed,
                                enabledCount = status.enabledHosts.size,
                                hostCount = LinkSettings.webHosts.size,
                            )
                            val message = when (report) {
                                LinkSettings.LinkHandlingReport.HandlingOff -> linksHandlingOffMessage
                                LinkSettings.LinkHandlingReport.NoneSelected -> linksStillOffMessage
                                LinkSettings.LinkHandlingReport.SomeSelected -> linksPartialMessage
                                LinkSettings.LinkHandlingReport.AllSelected -> linksKeptMessage
                            }
                            scope.launch {
                                snackbarHostState.showSnackbar(message, duration = SnackbarDuration.Long)
                            }
                        }
                    }
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Box(modifier = modifier.fillMaxSize()) {
        HomeView(
            uiState = uiState,
            onPasteClick = { pasteIntoField() },
            linkDraft = linkDraft,
            onLinkDraft = {
                linkDraft = it
                showLinkError = false
            },
            linkIsValid = extractUrlFromText(linkDraft) != null,
            showLinkError = showLinkError,
            onSubmitLink = { submitDraft() },
            onRecentLink = ::openUrl,
            onRemoveRecent = onRemoveRecent,
            onClearRecents = onClearRecents,
            onOpenHistory = onOpenHistory,
            onSwipeRecent = onSwipeRecent,
            onShareRecent = { url, title ->
                context.startActivity(Intent.createChooser(sharePostIntent(url, title), null))
            },
            onCopyRecent = { url ->
                scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("Link", url))) }
            },
            versionLabel = versionLabel,
            onOpenLinkSettings = {
                openedLinkSettings = true
                LinkSettings.open(context)
            },
            themeMode = themeMode,
            onThemeMode = onThemeMode,
            backBehavior = backBehavior,
            onBackBehavior = onBackBehavior,
            videoQuality = videoQuality,
            onVideoQuality = onVideoQuality,
            soundMode = soundMode,
            onSoundMode = onSoundMode,
            browserTrampoline = browserTrampoline,
            onBrowserTrampoline = onBrowserTrampoline,
            handoffBrowsers = handoffBrowsers,
            handoffPackage = handoffPackage,
            onHandoffBrowser = onHandoffBrowser,
            onSetDefaultBrowser = onSetDefaultBrowser,
            showFirstLaunchHint = showFirstLaunchHint,
            onDismissFirstLaunchHint = {
                FirstLaunchPreferences.dismiss(context)
                showFirstLaunchHint = false
            },
            showDeveloperTools = showDeveloperTools,
            sampleGroups = sampleGroups,
            showSamplesInRecents = showSamplesInRecents,
            onShowSamplesInRecents = onShowSamplesInRecents,
            onOpenSample = onOpenSample,
            sourceRows = sourceRows,
            onSourceShown = onSourceShown,
            onWithdrawSource = { id ->
                val clearedAll = onWithdrawSource(id)
                val name = when (id) {
                    "YouTube" -> context.getString(R.string.source_youtube)
                    "TikTok" -> context.getString(R.string.source_tiktok)
                    "Instagram" -> context.getString(R.string.source_instagram)
                    "Threads" -> context.getString(R.string.source_threads)
                    else -> null
                }
                if (name != null) {
                    scope.launch {
                        snackbarHostState.showForFiveSeconds(
                            context.getString(withdrawnSnackbarRes(clearedAll), name),
                        )
                    }
                }
            },
            onOpenExternal = { url -> openExternally(context, url, finishAfter = false) },
        )
        PaneSnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

internal enum class PasteOutcome { Empty, NotALink, Ready }

/** Empty clipboard is its own message. A clipboard with no link stays the invalid-link message. */
internal fun pasteOutcome(text: CharSequence?, url: String?): PasteOutcome = when {
    text.isNullOrBlank() -> PasteOutcome.Empty
    url.isNullOrBlank() -> PasteOutcome.NotALink
    else -> PasteOutcome.Ready
}
