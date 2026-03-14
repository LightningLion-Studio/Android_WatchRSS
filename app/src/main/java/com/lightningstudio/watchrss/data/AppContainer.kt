package com.lightningstudio.watchrss.data

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import com.lightningstudio.watchrss.data.cache.ManagedCacheService
import com.lightningstudio.watchrss.data.bili.BiliRepository
import com.lightningstudio.watchrss.data.bili.BiliRepositoryContract
import com.lightningstudio.watchrss.data.douyin.DouyinRepository
import com.lightningstudio.watchrss.data.douyin.DouyinRepositoryContract
import com.lightningstudio.watchrss.data.db.WatchRssDatabase
import com.lightningstudio.watchrss.data.rss.DefaultRssRepository
import com.lightningstudio.watchrss.data.rss.RssReadableService
import com.lightningstudio.watchrss.data.rss.RssFetchService
import com.lightningstudio.watchrss.data.rss.RssOfflineStore
import com.lightningstudio.watchrss.data.rss.RssParseService
import com.lightningstudio.watchrss.data.rss.RssRepository
import com.lightningstudio.watchrss.data.settings.SettingsRepository
import com.lightningstudio.watchrss.ui.util.RssImageLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

interface AppContainer {
    val rssRepository: RssRepository
    val settingsRepository: SettingsRepository
    val managedCacheService: ManagedCacheService
    val biliRepository: BiliRepositoryContract
    val douyinRepository: DouyinRepositoryContract
}

class DefaultAppContainer(context: Context) : AppContainer {
    private val appContext = context.applicationContext
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

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
            WatchRssDatabase.MIGRATION_5_6
        )
            .build()
    }

    override val settingsRepository: SettingsRepository by lazy {
        val dataStore = PreferenceDataStoreFactory.create(
            produceFile = { appContext.preferencesDataStoreFile("settings.preferences_pb") }
        )
        SettingsRepository(dataStore)
    }

    override val managedCacheService: ManagedCacheService by lazy {
        ManagedCacheService(appContext, settingsRepository, appScope).also { cacheService ->
            RssImageLoader.configure(cacheService)
        }
    }

    override val biliRepository: BiliRepositoryContract by lazy {
        val dataStore = PreferenceDataStoreFactory.create(
            produceFile = { appContext.preferencesDataStoreFile("bili_cache.preferences_pb") }
        )
        BiliRepository(appContext, dataStore, managedCacheService)
    }

    override val douyinRepository: DouyinRepositoryContract by lazy {
        DouyinRepository(appContext, managedCacheService)
    }

    override val rssRepository: RssRepository by lazy {
        val fetchService = RssFetchService()
        val readableService = RssReadableService()
        val parseService = RssParseService()
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
            offlineMediaDao = database.offlineMediaDao(),
            settingsRepository = settingsRepository,
            cacheService = managedCacheService,
            appScope = appScope,
            fetchService = fetchService,
            readableService = readableService,
            parseService = parseService,
            offlineStore = offlineStore
        )
    }
}
