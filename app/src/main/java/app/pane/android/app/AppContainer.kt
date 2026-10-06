package app.pane.android.app

import android.content.Context
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import app.pane.android.data.cache.LinkContentCacheDocument
import app.pane.android.data.cache.LinkContentCacheSerializer
import app.pane.android.data.cache.LinkContentCacheStore
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
import app.pane.android.domain.model.Clock
import app.pane.android.domain.model.SystemClock
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
}

class DefaultAppContainer(
    context: Context,
    clock: Clock = SystemClock,
) : AppContainer {
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

    override val observeRecentContent = ObserveRecentContentUseCase(
        contentRepository = contentRepository,
        recentLinksRepository = recentLinksStore,
    )
    override val openLink = OpenLinkUseCase(
        contentRepository = contentRepository,
        recentLinksRepository = recentLinksStore,
    )
    override val refreshLink = RefreshLinkUseCase(
        contentRepository = contentRepository,
        recentLinksRepository = recentLinksStore,
    )
    override val loadMoreComments = LoadMoreCommentsUseCase(contentRepository)
    override val prepareMediaForSharing = PrepareMediaForSharingUseCase(mediaRepository)
    override val downloadMedia = DownloadMediaUseCase(mediaRepository)
    override val homeUiMapper = HomeUiMapper(imageMapper, clock)
    override val viewerUiMapper = ViewerUiMapper(imageMapper)
    override val recentLinksRepository = recentLinksStore

}
