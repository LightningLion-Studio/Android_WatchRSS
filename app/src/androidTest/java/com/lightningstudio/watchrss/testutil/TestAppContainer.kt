package com.lightningstudio.watchrss.testutil

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.lightningstudio.watchrss.WatchRssApplication
import com.lightningstudio.watchrss.data.AppContainer
import com.lightningstudio.watchrss.data.DefaultAppContainer
import com.lightningstudio.watchrss.data.bili.BiliRepositoryContract
import com.lightningstudio.watchrss.data.cache.ManagedCacheService
import com.lightningstudio.watchrss.data.douyin.DouyinRepositoryContract
import com.lightningstudio.watchrss.data.rss.RssRepository
import com.lightningstudio.watchrss.data.settings.SettingsRepository
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.rules.ExternalResource
import java.io.File
import java.util.UUID

class TestAppContainer(
    context: Context,
    override val rssRepository: RssRepository,
    override val settingsRepository: SettingsRepository,
    private val biliRepositoryOverride: BiliRepositoryContract? = null,
    private val douyinRepositoryOverride: DouyinRepositoryContract? = null
) : AppContainer {
    private val fallback by lazy { DefaultAppContainer(context.applicationContext) }

    override val managedCacheService: ManagedCacheService
        get() = fallback.managedCacheService

    override val biliRepository: BiliRepositoryContract
        get() = biliRepositoryOverride ?: fallback.biliRepository

    override val douyinRepository: DouyinRepositoryContract
        get() = douyinRepositoryOverride ?: fallback.douyinRepository
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
