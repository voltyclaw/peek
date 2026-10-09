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
import androidx.compose.runtime.rememberCoroutineScope
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
import app.pane.android.BuildConfig
import app.pane.android.InstalledBrowsers
import app.pane.android.app.AppContainer
import app.pane.android.data.sample.SamplePosts
import app.pane.android.data.sample.SampleRecentsPreferences
import app.pane.android.ui.home.HandoffBrowserOption
import app.pane.android.ui.history.HistoryRoute
import app.pane.android.ui.history.HistoryViewModel
import app.pane.android.ui.home.HomeRoute
import app.pane.android.ui.home.SamplePickerGroup
import app.pane.android.ui.home.SamplePickerRow
import app.pane.android.ui.media.PlaybackSessionViewModel
import app.pane.android.ui.media.VideoQualityPreferences
import app.pane.android.ui.media.findActivity
import app.pane.android.ui.home.HomeViewModel
import app.pane.android.ui.player.PlayerRoute
import app.pane.android.ui.player.PlayerViewModel
import app.pane.android.ui.theme.ThemeMode
import android.widget.Toast
import app.pane.android.R
import app.pane.android.data.links.PaneEntry
import app.pane.android.data.links.paneEntry
import app.pane.android.data.links.profileHandoffFinishes
import app.pane.android.ui.actions.openExternally
import app.pane.android.domain.model.SourceApp
import app.pane.android.ui.viewer.ViewerRoute
import app.pane.android.ui.viewer.ViewerViewModel
import kotlinx.coroutines.launch

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
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val playbackOwner = context.findActivity()
    val playbackSession = playbackOwner?.let { viewModel<PlaybackSessionViewModel>(it).session }
    val activePostUrl = when (val top = backStack.lastOrNull()) {
        is ViewerKey -> top.url
        is PlayerKey -> top.url
        else -> null
    }
    LaunchedEffect(activePostUrl) {
        playbackSession?.retainOnly(activePostUrl)
    }
    val activity = context as? Activity
    var videoQuality by remember { mutableStateOf(VideoQualityPreferences.read(context)) }
    var soundMode by remember { mutableStateOf(SoundPreferences.read(context)) }
    var youTubeConsent by remember { mutableStateOf(container.youtube.hasConsent()) }
    var youTubeAgreedAt by remember { mutableStateOf(container.youtube.acceptedAtEpochMillis()) }
    var tikTokConsent by remember { mutableStateOf(container.tiktok.hasConsent()) }
    var tikTokAgreedAt by remember { mutableStateOf(container.tiktok.acceptedAtEpochMillis()) }
    val sourceStore = remember { app.pane.android.ui.home.SourceSwitchStore(context).also { it.migrateOnce() } }
    var sourceShown by remember { mutableStateOf(sourceStore.snapshot()) }
    val sourceRows = app.pane.android.ui.home.rememberSourceRows(sourceShown, youTubeAgreedAt, tikTokAgreedAt)
    var browserTrampoline by remember { mutableStateOf(BrowserTrampolinePreferences.read(context)) }
    var showSamples by remember {
        mutableStateOf(BuildConfig.DEBUG && SampleRecentsPreferences.read(context))
    }
    val sampleGroups = remember {
        if (!BuildConfig.DEBUG) {
            emptyList()
        } else {
            SamplePosts.entries.groupBy { it.group }.map { (title, rows) ->
                SamplePickerGroup(
                    title = title,
                    samples = rows.map { row -> SamplePickerRow(row.id, row.label, row.canonicalUrl) },
                )
            }
        }
    }
    // The developer switch bulk-seeds every sample. It must not wipe Recents on launch
    // when it is off: a successful pane://sample open writes its own row and should stay.
    var sampleSeedApplied by remember { mutableStateOf(false) }
    LaunchedEffect(showSamples) {
        if (!BuildConfig.DEBUG) return@LaunchedEffect
        if (showSamples) {
            SamplePosts.entries.asReversed().forEach { entry ->
                container.recentLinksRepository.markOpened(entry.canonicalUrl)
            }
            sampleSeedApplied = true
        } else if (sampleSeedApplied) {
            SamplePosts.entries.forEach { entry ->
                container.recentLinksRepository.remove(entry.canonicalUrl)
                container.recentLinksRepository.remove(SamplePosts.deepLink(entry.id))
            }
            sampleSeedApplied = false
        }
    }
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
    val homeViewModel: HomeViewModel = viewModel(
        factory = HomeViewModel.Factory(
            container.observeRecentContent,
            container.homeUiMapper,
            container.recentLinksRepository,
            container.historyRepository,
        ),
    )
    val homeUiState by homeViewModel.uiState.collectAsStateWithLifecycle()

    fun handleBack() {
        when (
            peekBackAction(
                launchedFromViewLink = launchedFromViewLink,
                stackSize = backStack.size,
                topIsPlayer = backStack.lastOrNull() is PlayerKey,
            )
        ) {
            PeekBackAction.Pop -> if (backStack.size > 1) backStack.removeLastOrNull()
            PeekBackAction.ClearToHome -> while (backStack.size > 1) backStack.removeLastOrNull()
            PeekBackAction.Finish -> activity?.finish()
            PeekBackAction.DeferToSystem -> Unit
        }
    }

    fun sourceHandsOff(url: String): Boolean {
        val stored = app.pane.android.domain.source.SourceSwitches.rows.associate {
            app.pane.android.domain.source.SourceSwitches.key(it) to (sourceShown[it] != false)
        }
        val source = app.pane.android.data.history.HistoryUrls.sourceApp(url)
        return app.pane.android.domain.source.SourceSwitches.handsOff(source, stored)
    }

    fun deliver(url: String, finishAfter: Boolean) {
        val profile = app.pane.android.data.links.paneEntry(url) == app.pane.android.data.links.PaneEntry.ProfileHandoff
        if (profile || sourceHandsOff(url)) {
            val started = openExternally(context, url, finishAfter = finishAfter)
            if (finishAfter && app.pane.android.data.links.profileHandoffFinishes(fromExternal = launchedFromViewLink, started = started)) {
                activity?.finish()
            } else if (!started) {
                Toast.makeText(context, context.getString(R.string.action_failed), Toast.LENGTH_SHORT).show()
            }
        } else {
            backStack.add(ViewerKey(url))
        }
    }

    fun openFromHub(url: String) {
        deliver(url, finishAfter = false)
    }

    fun openLinked(url: String) {
        if (app.pane.android.data.links.paneEntry(url) == app.pane.android.data.links.PaneEntry.ProfileHandoff ||
            BrowserTrampoline.openablePost(url) == null ||
            sourceHandsOff(url)
        ) {
            val started = openExternally(context, url, finishAfter = false)
            if (!started) {
                Toast.makeText(context, context.getString(R.string.action_failed), Toast.LENGTH_SHORT).show()
            }
        } else {
            backStack.add(ViewerKey(url))
        }
    }

    LaunchedEffect(viewIntentUrl.value) {
        val url = viewIntentUrl.value ?: return@LaunchedEffect
        viewIntentUrl.value = null
        deliver(url, finishAfter = true)
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
                    onOpenLink = { url -> openFromHub(url) },
                    themeMode = themeMode,
                    onThemeMode = onThemeMode,
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
                    onOpenHistory = { backStack.add(HistoryKey) },
                    onSwipeRecent = homeViewModel::swipe,
                    notices = homeViewModel.notice,
                    onUndoRecent = homeViewModel::undoRecent,
                    onUndoHistory = homeViewModel::undoHistory,
                    showDeveloperTools = BuildConfig.DEBUG,
                    sampleGroups = sampleGroups,
                    showSamplesInRecents = showSamples,
                    onShowSamplesInRecents = { enabled ->
                        showSamples = enabled
                        if (BuildConfig.DEBUG) SampleRecentsPreferences.write(context, enabled)
                    },
                    onOpenSample = { url -> openFromHub(url) },
                    sourceRows = sourceRows,
                    onSourceShown = { id, on ->
                        val app = app.pane.android.domain.model.SourceApp.valueOf(id)
                        sourceStore.set(app, on)
                        sourceShown = sourceStore.snapshot()
                    },
                    onWithdrawSource = { id ->
                        when (id) {
                            "YouTube" -> {
                                container.youtube.withdraw()
                                youTubeConsent = false
                                youTubeAgreedAt = null
                                scope.launch { container.historyRepository.stripSourceDisplayCache(app.pane.android.domain.model.SourceApp.YouTube) }
                                container.youtube.clearedAllSiteData()
                            }
                            "TikTok" -> {
                                container.tiktok.withdraw()
                                tikTokConsent = false
                                tikTokAgreedAt = null
                                scope.launch { container.historyRepository.stripSourceDisplayCache(app.pane.android.domain.model.SourceApp.TikTok) }
                                container.tiktok.clearedAllSiteData()
                            }
                            else -> false
                        }
                    },
                    modifier = Modifier.safeDrawingPadding(),
                )
            }
            entry<HistoryKey> {
                val historyViewModel: HistoryViewModel = viewModel(
                    factory = HistoryViewModel.Factory(
                        container.historyRepository,
                        container.readHistoryFilter(),
                        container::writeHistoryFilter,
                        refreshTikTok = { id, pageUrl -> container.refreshVisibleTikTok(id, pageUrl) },
                        canRefreshTikTok = { container.tiktok.hasConsent() },
                    ),
                )
                HistoryRoute(
                    viewModel = historyViewModel,
                    onBack = ::handleBack,
                    onOpen = { url -> openFromHub(url) },
                    modifier = Modifier.safeDrawingPadding(),
                )
            }
            entry<ViewerKey> { key ->
                val viewerViewModel: ViewerViewModel = viewModel(
                    factory = ViewerViewModel.Factory(
                        key.url,
                        container.openLink,
                        container.refreshLink,
                        container.loadMoreComments,
                        container.viewerUiMapper,
                        container.historyRepository,
                    ),
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
                    onOpenMedia = { ownerId, mediaIndex -> backStack.add(PlayerKey(key.url, mediaIndex, ownerId)) },
                    onOpenLinked = { url -> openLinked(url) },
                    youTubeConsented = youTubeConsent,
                    onAcceptYouTube = { videoId ->
                        container.youtube.accept(videoId, System.currentTimeMillis())
                        youTubeConsent = true
                        youTubeAgreedAt = container.youtube.acceptedAtEpochMillis()
                    },
                    onYouTubePlayerShown = { videoId ->
                        container.youtube.open(videoId, app.pane.android.domain.youtube.YouTubeEntry.View)
                    },
                    tikTokConsented = tikTokConsent,
                    onAcceptTikTok = {
                        val now = System.currentTimeMillis()
                        container.tiktok.allow(now)
                        tikTokConsent = true
                        tikTokAgreedAt = now
                    },
                    onTikTokPlayerShown = { videoId ->
                        container.tiktok.open(videoId, app.pane.android.domain.tiktok.TikTokEntry.View)
                    },
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
                    ownerId = key.ownerId,
                    videoQuality = videoQuality,
                    startMuted = { SoundPreferences.startMuted(context) },
                    onMutedChange = { muted -> SoundPreferences.rememberMuted(context, muted) },
                    onBack = ::handleBack,
                    onLeave = {
                        if (peekLeaveVideoAction(launchedFromViewLink) == PeekBackAction.Finish) {
                            activity?.finish()
                        } else {
                            while (backStack.size > 1) backStack.removeLastOrNull()
                        }
                    },
                )
            }
        },
    )
}
