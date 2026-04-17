package com.lightningstudio.watchrss.testutil

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.lightningstudio.watchrss.WatchRssApplication
import com.lightningstudio.watchrss.data.AppContainer
import com.lightningstudio.watchrss.data.DefaultAppContainer
import com.lightningstudio.watchrss.data.bili.BiliPlaybackCacheManager
import com.lightningstudio.watchrss.data.bili.BiliRepositoryContract
import com.lightningstudio.watchrss.data.cache.ManagedCacheService
import com.lightningstudio.watchrss.data.douyin.DouyinFeedCacheStoreContract
import com.lightningstudio.watchrss.data.douyin.DouyinPlaybackTransportContract
import com.lightningstudio.watchrss.data.douyin.DouyinPreloadManagerContract
import com.lightningstudio.watchrss.data.douyin.DouyinRecentWindowCacheCoordinatorContract
import com.lightningstudio.watchrss.data.douyin.DouyinRecentWindowStoreContract
import com.lightningstudio.watchrss.data.douyin.DouyinRepositoryContract
import com.lightningstudio.watchrss.data.douyin.DouyinWatchHistoryStoreContract
import com.lightningstudio.watchrss.data.network.InternetAvailabilityMonitor
import com.lightningstudio.watchrss.data.rss.RssRepository
import com.lightningstudio.watchrss.data.settings.LlmApiKeyStore
import com.lightningstudio.watchrss.data.settings.ReadAloudApiKeyStore
import com.lightningstudio.watchrss.data.settings.SettingsRepository
import com.lightningstudio.watchrss.data.tts.ReadAloudController
import com.lightningstudio.watchrss.data.tts.ReadAloudSynthesisService
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.rules.ExternalResource
import java.io.File
import java.util.UUID

class TestAppContainer(
    context: Context,
    override val rssRepository: RssRepository,
    override val settingsRepository: SettingsRepository,
    private val biliRepositoryOverride: BiliRepositoryContract? = null,
    private val douyinRepositoryOverride: DouyinRepositoryContract? = null,
    private val internetAvailabilityMonitorOverride: InternetAvailabilityMonitor? = null
) : AppContainer {
    private val fallback by lazy { DefaultAppContainer(context.applicationContext) }

    override val llmApiKeyStore: LlmApiKeyStore
        get() = fallback.llmApiKeyStore

    override val readAloudApiKeyStore: ReadAloudApiKeyStore
        get() = fallback.readAloudApiKeyStore

    override val readAloudSynthesisService: ReadAloudSynthesisService
        get() = fallback.readAloudSynthesisService

    override val readAloudController: ReadAloudController
        get() = fallback.readAloudController

    override val managedCacheService: ManagedCacheService
        get() = fallback.managedCacheService

    override val biliPlaybackCacheManager: BiliPlaybackCacheManager
        get() = fallback.biliPlaybackCacheManager

    override val biliRepository: BiliRepositoryContract
        get() = biliRepositoryOverride ?: fallback.biliRepository

    override val douyinRepository: DouyinRepositoryContract
        get() = douyinRepositoryOverride ?: fallback.douyinRepository

    override val douyinPreloadManager: DouyinPreloadManagerContract
        get() = fallback.douyinPreloadManager

    override val douyinPlaybackTransport: DouyinPlaybackTransportContract
        get() = fallback.douyinPlaybackTransport

    override val douyinFeedCacheStore: DouyinFeedCacheStoreContract
        get() = fallback.douyinFeedCacheStore

    override val douyinWatchHistoryStore: DouyinWatchHistoryStoreContract
        get() = fallback.douyinWatchHistoryStore

    override val douyinRecentWindowStore: DouyinRecentWindowStoreContract
        get() = fallback.douyinRecentWindowStore

    override val douyinRecentWindowCacheCoordinator: DouyinRecentWindowCacheCoordinatorContract
        get() = fallback.douyinRecentWindowCacheCoordinator

    override val internetAvailabilityMonitor: InternetAvailabilityMonitor
        get() = internetAvailabilityMonitorOverride ?: fallback.internetAvailabilityMonitor
}

class TestAppContainerRule(
    private val containerFactory: (Context) -> AppContainer
) : ExternalResource() {
    private var application: WatchRssApplication? = null

    override fun before() {
        val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        val targetApplication = targetContext.applicationContext as WatchRssApplication
        targetApplication.setContainerForTesting(containerFactory(targetContext))
        application = targetApplication
    }

    override fun after() {
        application?.setContainerForTesting(null)
        application = null
    }
}

fun createTestSettingsRepository(
    context: Context,
    prefix: String
): SettingsRepository {
    val file = File(
        context.filesDir,
        "$prefix-${UUID.randomUUID()}.preferences_pb"
    ).also(File::delete)
    val dataStore = PreferenceDataStoreFactory.create(
        produceFile = { file }
    )
    return SettingsRepository(dataStore)
}
