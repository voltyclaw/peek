package app.pane.android.ui.navigation

import android.app.Activity
import androidx.activity.BackEventCompat
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import app.pane.android.BrowserTrampoline
import app.pane.android.BrowserTrampolinePreferences
import app.pane.android.InstalledBrowsers
import app.pane.android.app.AppContainer
import app.pane.android.ui.home.HandoffBrowserOption
import app.pane.android.ui.home.HomeRoute
import app.pane.android.ui.media.VideoQualityPreferences
import app.pane.android.ui.home.HomeViewModel
import app.pane.android.ui.player.PlayerRoute
import app.pane.android.ui.player.PlayerViewModel
import app.pane.android.ui.theme.ThemeMode
import app.pane.android.ui.viewer.ViewerRoute
import app.pane.android.ui.viewer.ViewerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PeekNavigation(
    container: AppContainer,
    viewIntentUrl: MutableState<String?>,
    launchedFromViewLink: Boolean,
    themeMode: ThemeMode,
    onThemeMode: (ThemeMode) -> Unit,
) {
    val backStack = rememberNavBackStack(HomeKey)
    val context = LocalContext.current
    val activity = context as? Activity
    var backBehavior by remember { mutableStateOf(BackPreferences.read(context)) }
    var videoQuality by remember { mutableStateOf(VideoQualityPreferences.read(context)) }
    var soundMode by remember { mutableStateOf(SoundPreferences.read(context)) }
    var browserTrampoline by remember { mutableStateOf(BrowserTrampolinePreferences.read(context)) }
    var storedHandoff by remember { mutableStateOf(BrowserTrampolinePreferences.readHandoff(context).orEmpty()) }
    var installedBrowsers by remember { mutableStateOf(InstalledBrowsers.list(context)) }
    var systemBrowser by remember { mutableStateOf(InstalledBrowsers.systemPackage(context)) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                installedBrowsers = InstalledBrowsers.list(context)
                systemBrowser = InstalledBrowsers.systemPackage(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val handoffPackage = BrowserTrampoline.pickHandoff(
        candidates = installedBrowsers.map { browser ->
            BrowserTrampoline.Candidate(browser.packageName, browser.activityName)
        },
        ownPackage = context.packageName,
        preferredPackage = storedHandoff,
        systemPackage = systemBrowser,
    )?.packageName.orEmpty()
    val backBehaviorState = rememberUpdatedState(backBehavior)
    val homeViewModel: HomeViewModel = viewModel(
        factory = HomeViewModel.Factory(
            container.observeRecentContent,
            container.homeUiMapper,
            container.recentLinksRepository,
        ),
    )
    val homeUiState by homeViewModel.uiState.collectAsStateWithLifecycle()

    fun handleBack() {
        when (
            peekBackAction(
                launchedFromViewLink = launchedFromViewLink,
                stackSize = backStack.size,
                backClosesPeek = backBehaviorState.value == BackBehavior.ClosePeek,
                topIsPlayer = backStack.lastOrNull() is PlayerKey,
            )
        ) {
            PeekBackAction.Pop -> if (backStack.size > 1) backStack.removeLastOrNull()
            PeekBackAction.ClearToHome -> while (backStack.size > 1) backStack.removeLastOrNull()
            PeekBackAction.Finish -> activity?.finish()
            PeekBackAction.DeferToSystem -> Unit
        }
    }

    LaunchedEffect(viewIntentUrl.value) {
        val url = viewIntentUrl.value ?: return@LaunchedEffect
        viewIntentUrl.value = null
        backStack.add(ViewerKey(url))
    }

    NavDisplay(
        backStack = backStack,
        onBack = ::handleBack,
        transitionSpec = {
            EnterTransition.None togetherWith ExitTransition.None
        },
        popTransitionSpec = {
            EnterTransition.None togetherWith ExitTransition.None
        },
        predictivePopTransitionSpec = { swipeEdge ->
            val direction = if (swipeEdge == BackEventCompat.EDGE_LEFT) 1 else -1
            slideInHorizontally(initialOffsetX = { -it * direction }) togetherWith
                slideOutHorizontally(targetOffsetX = { it * direction })
        },
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator(),
        ),
        entryProvider = entryProvider {
            entry<HomeKey> {
                HomeRoute(
                    uiState = homeUiState,
                    onOpenLink = { url -> backStack.add(ViewerKey(url)) },
                    themeMode = themeMode,
                    onThemeMode = onThemeMode,
                    backBehavior = backBehavior,
                    onBackBehavior = { behavior ->
                        backBehavior = behavior
                        BackPreferences.write(context, behavior)
                    },
                    videoQuality = videoQuality,
                    onVideoQuality = { quality ->
                        videoQuality = quality
                        VideoQualityPreferences.write(context, quality)
                    },
                    soundMode = soundMode,
                    onSoundMode = { mode ->
                        soundMode = mode
                        SoundPreferences.write(context, mode)
                    },
                    browserTrampoline = browserTrampoline,
                    onBrowserTrampoline = { enabled ->
                        browserTrampoline = enabled
                        BrowserTrampolinePreferences.write(context, enabled)
                        if (enabled) BrowserTrampolinePreferences.requestDefaultBrowser(context)
                    },
                    handoffBrowsers = installedBrowsers.map { browser ->
                        HandoffBrowserOption(browser.packageName, browser.label)
                    },
                    handoffPackage = handoffPackage,
                    onHandoffBrowser = { packageName ->
                        storedHandoff = packageName
                        BrowserTrampolinePreferences.writeHandoff(context, packageName)
                    },
                    onSetDefaultBrowser = {
                        if (!browserTrampoline) {
                            browserTrampoline = true
                            BrowserTrampolinePreferences.write(context, true)
                        }
                        BrowserTrampolinePreferences.requestDefaultBrowser(context)
                    },
                    onRemoveRecent = homeViewModel::removeRecent,
                    onClearRecents = homeViewModel::clearRecents,
                    modifier = Modifier.safeDrawingPadding(),
                )
            }
            entry<ViewerKey> { key ->
                val viewerViewModel: ViewerViewModel = viewModel(
                    factory = ViewerViewModel.Factory(key.url, container.openLink, container.refreshLink, container.loadMoreComments, container.viewerUiMapper),
                )
                ViewerRoute(
                    viewModel = viewerViewModel,
                    prepareMediaForSharing = container.prepareMediaForSharing,
                    downloadMedia = container.downloadMedia,
                    videoQuality = videoQuality,
                    startMuted = { SoundPreferences.startMuted(context) },
                    onMutedChange = { muted -> SoundPreferences.rememberMuted(context, muted) },
                    onBack = ::handleBack,
                    onLeave = { while (backStack.size > 1) backStack.removeLastOrNull() },
                    onOpenInBrowser = { url -> InstalledBrowsers.open(context, url) },
                    onOpenMedia = { mediaIndex -> backStack.add(PlayerKey(key.url, mediaIndex)) },
                    modifier = Modifier.safeDrawingPadding(),
                )
            }
            entry<PlayerKey> { key ->
                val playerViewModel: PlayerViewModel = viewModel(
                    factory = PlayerViewModel.Factory(key.url, container.openLink, container.loadMoreComments, container.viewerUiMapper),
                )
                PlayerRoute(
                    viewModel = playerViewModel,
                    prepareMediaForSharing = container.prepareMediaForSharing,
                    downloadMedia = container.downloadMedia,
                    initialMediaIndex = key.mediaIndex,
                    videoQuality = videoQuality,
                    startMuted = { SoundPreferences.startMuted(context) },
                    onMutedChange = { muted -> SoundPreferences.rememberMuted(context, muted) },
                    onBack = ::handleBack,
                )
            }
        },
    )
}
