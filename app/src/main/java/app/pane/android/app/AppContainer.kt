package app.pane.android.app

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import app.pane.android.data.cache.LinkContentCacheDocument
import app.pane.android.data.cache.LinkContentCacheSerializer
import app.pane.android.data.cache.LinkContentCacheStore
import app.pane.android.data.history.AndroidHistoryPreferences
import app.pane.android.data.history.AndroidHistorySql
import app.pane.android.data.history.AndroidStarImageCompressor
import app.pane.android.data.history.FileStarImageStore
import app.pane.android.data.history.SqliteHistoryRepository
import app.pane.android.data.facebook.AndroidFacebookPageLoader
import app.pane.android.data.facebook.FacebookDirectPageLoader
import app.pane.android.data.facebook.FacebookLinkContentRepository
import app.pane.android.data.instagram.AndroidInstagramPageLoader
import app.pane.android.data.instagram.InstagramDirectPageLoader
import app.pane.android.data.instagram.InstagramLinkContentRepository
import app.pane.android.data.media.AndroidMediaRepository
import app.pane.android.data.recent.DataStoreRecentLinksRepository
import app.pane.android.data.recent.RecentLinksDocument
import app.pane.android.data.recent.RecentLinksSerializer
import app.pane.android.data.reddit.AndroidRedditPageLoader
import app.pane.android.data.reddit.RedditDirectPageLoader
import app.pane.android.data.reddit.RedditLinkContentRepository
import app.pane.android.data.resolver.RoutingLinkContentRepository
import app.pane.android.data.sample.SampleLinkContentRepository
import app.pane.android.data.sample.SampleMedia
import app.pane.android.data.x.XDirectPageLoader
import app.pane.android.data.x.XLinkContentRepository
import app.pane.android.data.youtube.AndroidYouTubeConsentStore
import app.pane.android.data.youtube.AndroidYouTubeSiteData
import app.pane.android.data.youtube.YouTubeDataApiClient
import app.pane.android.data.youtube.YouTubeLinkContentRepository
import app.pane.android.data.tiktok.AndroidTikTokConsentStore
import app.pane.android.data.tiktok.AndroidTikTokSiteData
import app.pane.android.data.tiktok.FileTikTokOEmbedDisk
import app.pane.android.data.tiktok.HttpTikTokTransport
import app.pane.android.data.bluesky.BlueskyLinkContentRepository
import app.pane.android.data.tiktok.TikTokLinkContentRepository
import app.pane.android.data.tiktok.TikTokOEmbedClient
import app.pane.android.data.tiktok.TikTokRedirectResolver
import app.pane.android.domain.tiktok.TikTokCaption
import app.pane.android.domain.tiktok.TikTokCopyRetention
import app.pane.android.domain.tiktok.TikTokOEmbedResult
import app.pane.android.domain.tiktok.TikTokSession
import app.pane.android.domain.youtube.YouTubeSession
import app.pane.android.domain.model.Clock
import app.pane.android.domain.model.HistoryQuery
import app.pane.android.domain.model.RecentContent
import app.pane.android.domain.model.SystemClock
import app.pane.android.domain.repository.HistoryRepository
import app.pane.android.domain.repository.LinkContentRepository
import app.pane.android.domain.repository.RecentLinksRepository
import app.pane.android.BuildConfig
import app.pane.android.domain.usecase.ObserveRecentContentUseCase
import app.pane.android.domain.usecase.OpenLinkUseCase
import app.pane.android.domain.usecase.RefreshLinkUseCase
import app.pane.android.domain.usecase.LoadMoreCommentsUseCase
import app.pane.android.domain.usecase.DownloadMediaUseCase
import app.pane.android.domain.usecase.PrepareMediaForSharingUseCase
import app.pane.android.ui.mapper.HomeUiMapper
import app.pane.android.ui.mapper.UiImageMapper
import app.pane.android.ui.mapper.ViewerUiMapper
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

interface AppContainer {
    val observeRecentContent: ObserveRecentContentUseCase
    val openLink: OpenLinkUseCase
    val refreshLink: RefreshLinkUseCase
    val loadMoreComments: LoadMoreCommentsUseCase
    val prepareMediaForSharing: PrepareMediaForSharingUseCase
    val downloadMedia: DownloadMediaUseCase
    val homeUiMapper: HomeUiMapper
    val viewerUiMapper: ViewerUiMapper
    val recentLinksRepository: RecentLinksRepository
    val historyRepository: HistoryRepository
    fun readHistoryFilter(): HistoryQuery
    fun writeHistoryFilter(query: HistoryQuery)
    val youtube: YouTubeSession
    val tiktok: TikTokSession
    suspend fun refreshVisibleTikTok(videoId: String, pageUrl: String)
}

class DefaultAppContainer(
    context: Context,
    private val clock: Clock = SystemClock,
) : AppContainer {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val seedCacheDocument = LinkContentCacheDocument(entries = emptyList())
    private val linkContentCacheDataStore = DataStoreFactory.create(
        serializer = LinkContentCacheSerializer(seedCacheDocument),
        corruptionHandler = ReplaceFileCorruptionHandler { seedCacheDocument },
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
        produceFile = { File(context.filesDir, "link_content_cache.json") },
    )
    private val linkContentCacheStore = LinkContentCacheStore(linkContentCacheDataStore)
    private val instagramRepository = InstagramLinkContentRepository(
        pageLoaders = listOf(
            InstagramDirectPageLoader(),
            AndroidInstagramPageLoader(context),
        ),
        cacheStore = linkContentCacheStore,
    )
    private val redditRepository = RedditLinkContentRepository(
        // JSON first. The WebView runs only after that loader fails, so a successful document skips it.
        pageLoaders = listOf(
            RedditDirectPageLoader(),
            AndroidRedditPageLoader(context),
        ),
        cacheStore = linkContentCacheStore,
    )
    private val facebookRepository = FacebookLinkContentRepository(
        pageLoaders = listOf(
            FacebookDirectPageLoader(),
            AndroidFacebookPageLoader(context),
        ),
        cacheStore = linkContentCacheStore,
    )
    private val xRepository = XLinkContentRepository(
        pageLoaders = listOf(XDirectPageLoader()),
        cacheStore = linkContentCacheStore,
    )
    private val youtubeStore = AndroidYouTubeConsentStore(context)
    private val youtubeApi = YouTubeDataApiClient(BuildConfig.YOUTUBE_API_KEY)
    private val youtubeRepository = YouTubeLinkContentRepository(youtubeStore, youtubeApi)
    override val youtube = YouTubeSession(
        store = youtubeStore,
        api = youtubeApi,
        siteData = AndroidYouTubeSiteData(),
    )
    private val tiktokStore = AndroidTikTokConsentStore(context)
    private val tiktokApi = TikTokOEmbedClient(
        transport = HttpTikTokTransport,
        disk = FileTikTokOEmbedDisk(File(context.cacheDir, "tiktok-oembed.json")),
    )
    private val tiktokRepository = TikTokLinkContentRepository(
        consent = tiktokStore,
        api = tiktokApi,
        redirects = TikTokRedirectResolver(HttpTikTokTransport),
    )
    private val blueskyRepository = BlueskyLinkContentRepository()
    override val tiktok = TikTokSession(
        store = tiktokStore,
        api = tiktokApi,
        siteData = AndroidTikTokSiteData(),
    )
    private val contentRepository: LinkContentRepository = RoutingLinkContentRepository(
        buildList {
            if (BuildConfig.DEBUG) {
                val samples = SampleLinkContentRepository(SampleMedia.install(context))
                add(RoutingLinkContentRepository.Route(samples::supports, samples))
            }
            add(RoutingLinkContentRepository.Route(instagramRepository::supports, instagramRepository))
            add(RoutingLinkContentRepository.Route(redditRepository::supports, redditRepository))
            add(RoutingLinkContentRepository.Route(facebookRepository::supports, facebookRepository))
            add(RoutingLinkContentRepository.Route(xRepository::supports, xRepository))
            add(RoutingLinkContentRepository.Route(youtubeRepository::supports, youtubeRepository))
            add(RoutingLinkContentRepository.Route(tiktokRepository::supports, tiktokRepository))
            add(RoutingLinkContentRepository.Route(blueskyRepository::supports, blueskyRepository))
        },
    )
    private val seedDocument = RecentLinksDocument(links = emptyList())
    private val recentLinksDataStore = DataStoreFactory.create(
        serializer = RecentLinksSerializer(seedDocument),
        corruptionHandler = ReplaceFileCorruptionHandler { seedDocument },
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
        produceFile = { File(context.filesDir, "recent_links.json") },
    )
    private val recentLinksStore = DataStoreRecentLinksRepository(
        recentLinksDataStore,
        clock,
        maximumEntries = if (BuildConfig.DEBUG) 32 else 8,
    )
    private val mediaRepository = AndroidMediaRepository(context)
    private val imageMapper = UiImageMapper()
    private val historyPreferences = AndroidHistoryPreferences(context)
    override val historyRepository: HistoryRepository = SqliteHistoryRepository(
        sql = AndroidHistorySql(context),
        imageStore = FileStarImageStore(
            root = File(context.filesDir, "stars"),
            compressor = AndroidStarImageCompressor(),
        ),
        clock = clock,
    )

    override val observeRecentContent = ObserveRecentContentUseCase(
        contentRepository = contentRepository,
        recentLinksRepository = recentLinksStore,
    )
    override val openLink = OpenLinkUseCase(
        contentRepository = contentRepository,
        recentLinksRepository = recentLinksStore,
        historyRepository = historyRepository,
        clock = clock,
    )
    override val refreshLink = RefreshLinkUseCase(
        contentRepository = contentRepository,
        recentLinksRepository = recentLinksStore,
        historyRepository = historyRepository,
        clock = clock,
    )
    override val loadMoreComments = LoadMoreCommentsUseCase(contentRepository)
    override val prepareMediaForSharing = PrepareMediaForSharingUseCase(mediaRepository)
    override val downloadMedia = DownloadMediaUseCase(mediaRepository)
    override val homeUiMapper = HomeUiMapper(imageMapper, clock)
    override val viewerUiMapper = ViewerUiMapper(imageMapper)
    override val recentLinksRepository = recentLinksStore

    override fun readHistoryFilter(): HistoryQuery = historyPreferences.readFilter()

    override fun writeHistoryFilter(query: HistoryQuery) {
        historyPreferences.writeFilter(query)
    }

    override suspend fun refreshVisibleTikTok(videoId: String, pageUrl: String) {
        if (!tiktok.hasConsent() || videoId.isBlank()) return
        when (val result = kotlinx.coroutines.withContext(Dispatchers.IO) { tiktokApi.fetch(videoId, pageUrl) }) {
            is TikTokOEmbedResult.Ready -> {
                val caption = TikTokCopyRetention.caption(result.embed.caption)
                val handle = TikTokCaption.handleFromAuthorUrl(result.embed.authorUrl)
                historyRepository.replaceDisplayCache(
                    url = pageUrl,
                    title = caption,
                    authorName = result.embed.authorName,
                    handle = handle,
                    caption = caption,
                    thumbUrl = result.embed.thumbnailUrl,
                    fetchedAtEpochMillis = clock.nowEpochMillis(),
                )
            }
            is TikTokOEmbedResult.Removed -> historyRepository.stripDisplayCache(pageUrl)
            is TikTokOEmbedResult.Failed -> Unit
        }
    }

    init {
        historyPreferences.readSwipeAction()
        appScope.launch {
            runCatching {
                val recents = recentLinksStore.observeRecents().first()
                historyRepository.seedFromCached(
                    recents.map { recent -> RecentContent(recent, contentRepository.peekCached(recent.url)) },
                )
            }.onFailure { error ->
                Log.w("PaneHistory", "History seed failed", error)
            }
        }
    }
}
