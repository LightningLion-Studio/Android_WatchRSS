package com.lightningstudio.watchrss.data

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import com.lightningstudio.watchrss.data.bili.BiliRepository
import com.lightningstudio.watchrss.data.douyin.DouyinRepository
import com.lightningstudio.watchrss.data.db.WatchRssDatabase
import com.lightningstudio.watchrss.data.rss.DefaultRssRepository
import com.lightningstudio.watchrss.data.rss.RssReadableService
import com.lightningstudio.watchrss.data.rss.RssFetchService
import com.lightningstudio.watchrss.data.rss.RssOfflineStore
import com.lightningstudio.watchrss.data.rss.RssParseService
import com.lightningstudio.watchrss.data.rss.RssRepository
import com.lightningstudio.watchrss.data.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

interface AppContainer {
    val rssRepository: RssRepository
    val settingsRepository: SettingsRepository
    val biliRepository: BiliRepository
    val douyinRepository: DouyinRepository
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

    override val biliRepository: BiliRepository by lazy {
        val dataStore = PreferenceDataStoreFactory.create(
            produceFile = { appContext.preferencesDataStoreFile("bili_cache.preferences_pb") }
        )
        BiliRepository(appContext, dataStore)
    }

    override val douyinRepository: DouyinRepository by lazy {
        DouyinRepository(appContext)
    }

    override val rssRepository: RssRepository by lazy {
        val fetchService = RssFetchService()
        val readableService = RssReadableService()
        val parseService = RssParseService()
        val offlineStore = RssOfflineStore(
            appContext,
            database.offlineMediaDao(),
            fetchService
        )
        DefaultRssRepository(
            channelDao = database.rssChannelDao(),
            itemDao = database.rssItemDao(),
            savedEntryDao = database.savedEntryDao(),
            offlineMediaDao = database.offlineMediaDao(),
            settingsRepository = settingsRepository,
            appScope = appScope,
            fetchService = fetchService,
            readableService = readableService,
            parseService = parseService,
            offlineStore = offlineStore
        )
    }
}
