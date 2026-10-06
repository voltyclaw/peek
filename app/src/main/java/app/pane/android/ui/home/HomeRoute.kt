package app.pane.android.ui.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.core.content.pm.PackageInfoCompat
import app.pane.android.R
import app.pane.android.domain.usecase.ExtractUrlFromTextUseCase
import app.pane.android.ui.model.HomeUiState
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
    extractUrlFromText: ExtractUrlFromTextUseCase = ExtractUrlFromTextUseCase(),
) {
    val clipboard = LocalClipboard.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val invalidClipboardMessage = stringResource(R.string.clipboard_url_unavailable)
    val linkReadyMessage = stringResource(R.string.link_ready)
    val openAction = stringResource(R.string.open_action)
    val usingFirstLink = stringResource(R.string.using_first_link)
    val linksKeptMessage = stringResource(R.string.link_settings_kept)
    val linksPartialMessage = stringResource(R.string.link_settings_partial)
    val linksStillOffMessage = stringResource(R.string.link_settings_still_off)
    val linksHandlingOffMessage = stringResource(R.string.link_settings_handling_off)
    val lifecycleOwner = LocalLifecycleOwner.current
    var openedLinkSettings by remember { mutableStateOf(false) }
    var showFirstLaunchHint by remember { mutableStateOf(!FirstLaunchPreferences.isDismissed(context)) }
    var leftLinkSettings by remember { mutableStateOf(false) }
    var linkDraft by rememberSaveable { mutableStateOf("") }
    var showLinkField by rememberSaveable { mutableStateOf(false) }
    var openingLink by remember { mutableStateOf(false) }
    var offeredClipboardUrl by remember { mutableStateOf<String?>(null) }
    val versionLabel = remember(context) {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        val code = PackageInfoCompat.getLongVersionCode(info).toString()
        context.getString(R.string.app_version, info.versionName.orEmpty(), code)
    }
    val linkIsValid = extractUrlFromText(linkDraft) != null
    val showLinkError = showLinkField && linkDraft.isNotBlank() && !linkIsValid

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

    fun offerClipboard(fromButton: Boolean) {
        readClipboard { clipboardText ->
            val url = extractUrlFromText(clipboardText)
            if (url == null) {
                if (fromButton) {
                    scope.launch { snackbarHostState.showSnackbar(invalidClipboardMessage) }
                }
                return@readClipboard
            }
            if (!fromButton && url == offeredClipboardUrl) return@readClipboard
            offeredClipboardUrl = url
            scope.launch {
                if (extractUrlFromText.count(clipboardText) > 1) {
                    snackbarHostState.showSnackbar(usingFirstLink, duration = SnackbarDuration.Short)
                }
                val result = snackbarHostState.showSnackbar(
                    message = linkReadyMessage,
                    actionLabel = openAction,
                    duration = SnackbarDuration.Long,
                )
                if (result == SnackbarResult.ActionPerformed) openUrl(url)
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
                    } else {
                        offerClipboard(fromButton = false)
                    }
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            offerClipboard(fromButton = false)
        }
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Box(modifier = modifier.fillMaxSize()) {
        HomeView(
            uiState = uiState,
            onPasteClick = { offerClipboard(fromButton = true) },
            onRecentLink = ::openUrl,
            onRemoveRecent = onRemoveRecent,
            onClearRecents = onClearRecents,
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
            linkDraft = linkDraft,
            onLinkDraft = { linkDraft = it },
            showLinkField = showLinkField,
            onTypeLink = { showLinkField = true },
            linkIsValid = linkIsValid,
            showLinkError = showLinkError,
            openingLink = openingLink,
            onSubmitLink = {
                val url = extractUrlFromText(linkDraft) ?: return@HomeView
                if (extractUrlFromText.count(linkDraft) > 1) {
                    scope.launch { snackbarHostState.showSnackbar(usingFirstLink, duration = SnackbarDuration.Short) }
                }
                openUrl(url)
            },
        )
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}
