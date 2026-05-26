package com.lightningstudio.watchrss.data

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import com.lightningstudio.watchrss.data.bili.BiliPlaybackCacheManager
import com.lightningstudio.watchrss.data.cache.ManagedCacheService
import com.lightningstudio.watchrss.data.bili.BiliRepository
import com.lightningstudio.watchrss.data.bili.BiliRepositoryContract
import com.lightningstudio.watchrss.data.db.BuiltinChannelSeed
import com.lightningstudio.watchrss.data.douyin.DouyinFeedCacheStore
import com.lightningstudio.watchrss.data.douyin.DouyinFeedCacheStoreContract
import com.lightningstudio.watchrss.data.douyin.DouyinPlaybackPreviewCache
import com.lightningstudio.watchrss.data.douyin.DouyinPlaybackTransport
import com.lightningstudio.watchrss.data.douyin.DouyinPlaybackTransportContract
import com.lightningstudio.watchrss.data.douyin.DouyinPreloadManager
import com.lightningstudio.watchrss.data.douyin.DouyinPreloadManagerContract
import com.lightningstudio.watchrss.data.douyin.DouyinRecentWindowCacheCoordinator
import com.lightningstudio.watchrss.data.douyin.DouyinRecentWindowCacheCoordinatorContract
import com.lightningstudio.watchrss.data.douyin.DouyinRecentWindowStore
import com.lightningstudio.watchrss.data.douyin.DouyinRecentWindowStoreContract
import com.lightningstudio.watchrss.data.douyin.DouyinRepository
import com.lightningstudio.watchrss.data.douyin.DouyinRepositoryContract
import com.lightningstudio.watchrss.data.douyin.DouyinWatchHistoryStore
import com.lightningstudio.watchrss.data.douyin.DouyinWatchHistoryStoreContract
import com.lightningstudio.watchrss.data.db.WatchRssDatabase
import com.lightningstudio.watchrss.data.network.DefaultInternetAvailabilityMonitor
import com.lightningstudio.watchrss.data.network.InternetAvailabilityMonitor
import com.lightningstudio.watchrss.data.rss.DefaultRssRepository
import com.lightningstudio.watchrss.data.rss.FileArticleContentStore
import com.lightningstudio.watchrss.data.rss.RssReadableService
import com.lightningstudio.watchrss.data.rss.RssFetchService
import com.lightningstudio.watchrss.data.rss.RssOfflineStore
import com.lightningstudio.watchrss.data.rss.RssParseService
import com.lightningstudio.watchrss.data.rss.RssRepository
import com.lightningstudio.watchrss.data.settings.LlmApiKeyStore
import com.lightningstudio.watchrss.data.settings.ReadAloudApiKeyStore
import com.lightningstudio.watchrss.data.settings.SettingsRepository
import com.lightningstudio.watchrss.data.tts.ReadAloudController
import com.lightningstudio.watchrss.data.tts.ReadAloudSynthesisService
import com.lightningstudio.watchrss.phoneconnection.WatchDeviceIdentity
import com.lightningstudio.watchrss.ui.util.RssImageLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

interface AppContainer {
    val rssRepository: RssRepository
    val settingsRepository: SettingsRepository
    val llmApiKeyStore: LlmApiKeyStore
    val readAloudApiKeyStore: ReadAloudApiKeyStore
    val readAloudSynthesisService: ReadAloudSynthesisService
    val readAloudController: ReadAloudController
    val managedCacheService: ManagedCacheService
    val biliPlaybackCacheManager: BiliPlaybackCacheManager
    val biliRepository: BiliRepositoryContract
    val douyinRepository: DouyinRepositoryContract
    val douyinPreloadManager: DouyinPreloadManagerContract
    val douyinPlaybackTransport: DouyinPlaybackTransportContract
    val douyinFeedCacheStore: DouyinFeedCacheStoreContract
    val douyinWatchHistoryStore: DouyinWatchHistoryStoreContract
    val douyinRecentWindowStore: DouyinRecentWindowStoreContract
    val douyinRecentWindowCacheCoordinator: DouyinRecentWindowCacheCoordinatorContract
    val internetAvailabilityMonitor: InternetAvailabilityMonitor
}

class DefaultAppContainer(context: Context) : AppContainer {
    private val appContext = context.applicationContext
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val deviceIdentity: WatchDeviceIdentity by lazy {
        WatchDeviceIdentity(appContext)
    }

    init {
        DouyinPlaybackPreviewCache.configure(appContext)
    }

    private val database: WatchRssDatabase by lazy {
        Room.databaseBuilder(
            appContext,
            WatchRssDatabase::class.java,
            "watchrss.db"
        ).addMigrations(
            WatchRssDatabase.MIGRATION_1_2,
            WatchRssDatabase.MIGRATION_2_3,
            WatchRssDatabase.MIGRATION_3_4,
            WatchRssDatabase.MIGRATION_4_5,
            WatchRssDatabase.MIGRATION_5_6,
            WatchRssDatabase.MIGRATION_6_7,
            WatchRssDatabase.MIGRATION_7_8,
            WatchRssDatabase.MIGRATION_8_9,
            WatchRssDatabase.MIGRATION_9_10,
            WatchRssDatabase.MIGRATION_10_11,
            WatchRssDatabase.MIGRATION_11_12,
            WatchRssDatabase.MIGRATION_12_13
        )
            .addCallback(BuiltinChannelSeed.callback)
            .build()
    }

    override val settingsRepository: SettingsRepository by lazy {
        val dataStore = PreferenceDataStoreFactory.create(
            produceFile = { appContext.preferencesDataStoreFile("settings.preferences_pb") }
        )
        SettingsRepository(dataStore)
    }

    override val llmApiKeyStore: LlmApiKeyStore by lazy {
        LlmApiKeyStore(appContext)
    }

    override val readAloudApiKeyStore: ReadAloudApiKeyStore by lazy {
        ReadAloudApiKeyStore(appContext)
    }

    override val managedCacheService: ManagedCacheService by lazy {
        ManagedCacheService(appContext, settingsRepository, appScope).also { cacheService ->
            RssImageLoader.configure(cacheService)
        }
    }

    override val biliPlaybackCacheManager: BiliPlaybackCacheManager by lazy {
        BiliPlaybackCacheManager(appContext)
    }

    override val biliRepository: BiliRepositoryContract by lazy {
        val dataStore = PreferenceDataStoreFactory.create(
            produceFile = { appContext.preferencesDataStoreFile("bili_cache.preferences_pb") }
        )
        BiliRepository(
            context = appContext,
            dataStore = dataStore,
            cacheService = managedCacheService,
            playbackCacheManager = biliPlaybackCacheManager
        )
    }

    override val douyinFeedCacheStore: DouyinFeedCacheStoreContract by lazy {
        DouyinFeedCacheStore(appContext)
    }

    override val douyinWatchHistoryStore: DouyinWatchHistoryStoreContract by lazy {
        DouyinWatchHistoryStore(appContext)
    }

    override val douyinRecentWindowStore: DouyinRecentWindowStoreContract by lazy {
        DouyinRecentWindowStore(appContext)
    }

    override val douyinPreloadManager: DouyinPreloadManagerContract by lazy {
        DouyinPreloadManager(appContext, managedCacheService)
    }

    override val douyinPlaybackTransport: DouyinPlaybackTransportContract by lazy {
        DouyinPlaybackTransport()
    }

    override val douyinRecentWindowCacheCoordinator: DouyinRecentWindowCacheCoordinatorContract by lazy {
        DouyinRecentWindowCacheCoordinator(
            appScope = appScope,
            preloadManager = douyinPreloadManager
        )
    }

    override val douyinRepository: DouyinRepositoryContract by lazy {
        DouyinRepository(
            context = appContext,
            cacheService = managedCacheService,
            recentWindowStore = douyinRecentWindowStore
        )
    }

    override val internetAvailabilityMonitor: InternetAvailabilityMonitor by lazy {
        DefaultInternetAvailabilityMonitor(appContext)
    }

    override val readAloudSynthesisService: ReadAloudSynthesisService by lazy {
        ReadAloudSynthesisService(
            cacheDir = appContext.cacheDir.resolve("read_aloud")
        )
    }

    override val rssRepository: RssRepository by lazy {
        val fetchService = RssFetchService()
        val readableService = RssReadableService()
        val parseService = RssParseService()
        val articleContentStore = FileArticleContentStore(appContext)
        val offlineStore = RssOfflineStore(
            appContext,
            database.offlineMediaDao(),
            fetchService,
            managedCacheService
        )
        DefaultRssRepository(
            channelDao = database.rssChannelDao(),
            itemDao = database.rssItemDao(),
            savedEntryDao = database.savedEntryDao(),
            savedSyncStateDao = database.savedSyncStateDao(),
            offlineMediaDao = database.offlineMediaDao(),
            cacheService = managedCacheService,
            appScope = appScope,
            fetchService = fetchService,
            readableService = readableService,
            parseService = parseService,
            offlineStore = offlineStore,
            deviceId = deviceIdentity.deviceId,
            articleContentStore = articleContentStore
        )
    }

    override val readAloudController: ReadAloudController by lazy {
        ReadAloudController(
            context = appContext,
            appScope = appScope,
            rssRepository = rssRepository,
            settingsRepository = settingsRepository,
            apiKeyStore = readAloudApiKeyStore,
            synthesisService = readAloudSynthesisService
        )
    }
}
