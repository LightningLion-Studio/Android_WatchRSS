package com.lightningstudio.watchrss.data

import com.lightningstudio.watchrss.data.telemetry.WatchInstallationIdentity
import com.lightningstudio.watchrss.data.telemetry.WatchUsageTelemetry

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import androidx.room.withTransaction
import com.lightningstudio.watchrss.data.bili.BiliPlaybackCacheManager
import com.lightningstudio.watchrss.data.cache.ManagedCacheService
import com.lightningstudio.watchrss.data.bili.BiliRepository
import com.lightningstudio.watchrss.data.bili.BiliRepositoryContract
import com.lightningstudio.watchrss.data.db.BuiltinChannelSeed
import com.lightningstudio.watchrss.data.db.RssChannelEntity
import com.lightningstudio.watchrss.data.douyin.DouyinFeedCacheStore
import com.lightningstudio.watchrss.data.douyin.DouyinFeedCacheStoreContract
import com.lightningstudio.watchrss.data.douyin.DouyinPlaybackPreviewCache
import com.lightningstudio.watchrss.data.douyin.DouyinPlaybackSourceCoordinator
import com.lightningstudio.watchrss.data.douyin.DouyinPlaybackSourceCoordinatorContract
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
import com.lightningstudio.watchrss.data.llm.LlmTokenUsageRepository
import com.lightningstudio.watchrss.data.media.AudioManagerMediaPlaybackStartVolumeLimiter
import com.lightningstudio.watchrss.data.network.DefaultInternetAvailabilityMonitor
import com.lightningstudio.watchrss.data.network.InternetAvailabilityMonitor
import com.lightningstudio.watchrss.data.rss.DefaultRssRepository
import com.lightningstudio.watchrss.data.rss.FileArticleContentStore
import com.lightningstudio.watchrss.data.rss.RssReadableService
import com.lightningstudio.watchrss.data.rss.RssFetchService
import com.lightningstudio.watchrss.data.rss.RssOfflineStore
import com.lightningstudio.watchrss.data.rss.RssParseService
import com.lightningstudio.watchrss.data.rss.RssRepository
import com.lightningstudio.watchrss.data.reader.ReaderPresetRepository
import com.lightningstudio.watchrss.data.account.AccountStore
import com.lightningstudio.watchrss.data.account.WatchAccountStore
import com.lightningstudio.watchrss.data.account.WatchTokenManager
import com.lightningstudio.watchrss.data.settings.LlmApiKeyStore
import com.lightningstudio.watchrss.data.settings.SettingsRepository
import com.lightningstudio.watchrss.data.settings.TtsApiKeyStore
import com.lightningstudio.watchrss.data.tts.ReadAloudController
import com.lightningstudio.watchrss.data.tts.engine.TtsEngineFactory
import com.lightningstudio.watchrss.data.note.WatchNoteRepository
import com.lightningstudio.watchrss.phoneconnection.WatchDeviceIdentity
import com.lightningstudio.watchrss.ui.util.RssImageLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

interface AppContainer {
    val watchUsageTelemetry: WatchUsageTelemetry
    val rssRepository: RssRepository
    val settingsRepository: SettingsRepository
    val llmApiKeyStore: LlmApiKeyStore
    val ttsApiKeyStore: TtsApiKeyStore
    val readAloudController: ReadAloudController
    val watchAccountStore: WatchAccountStore
    val watchTokenManager: WatchTokenManager
    val managedCacheService: ManagedCacheService
    val biliPlaybackCacheManager: BiliPlaybackCacheManager
    val biliRepository: BiliRepositoryContract
    val douyinRepository: DouyinRepositoryContract
    val douyinPreloadManager: DouyinPreloadManagerContract
    val douyinPlaybackTransport: DouyinPlaybackTransportContract
    val douyinPlaybackSourceCoordinator: DouyinPlaybackSourceCoordinatorContract
    val douyinFeedCacheStore: DouyinFeedCacheStoreContract
    val douyinWatchHistoryStore: DouyinWatchHistoryStoreContract
    val douyinRecentWindowStore: DouyinRecentWindowStoreContract
    val douyinRecentWindowCacheCoordinator: DouyinRecentWindowCacheCoordinatorContract
    val internetAvailabilityMonitor: InternetAvailabilityMonitor
    val readerPresetRepository: ReaderPresetRepository
    val llmTokenUsageRepository: LlmTokenUsageRepository
    val watchNoteRepository: WatchNoteRepository
}

class DefaultAppContainer(
    context: Context,
    override val watchAccountStore: WatchAccountStore = WatchAccountStore(context, enforceIdentity = true)
) : AppContainer {
    internal val accountIdentity = watchAccountStore.state.value?.let { it.backendBaseUrl.trimEnd('/') to it.userId }
    internal val libraryContext: Context = com.lightningstudio.watchrss.data.account.workspace.accountDataContext(
        context, accountIdentity?.first ?: "unbound", accountIdentity?.second
    )
    private val appContext = context.applicationContext
    private val accountJob = SupervisorJob()
    private val appScope = CoroutineScope(accountJob + Dispatchers.IO)
    private val deviceIdentity: WatchDeviceIdentity by lazy {
        WatchDeviceIdentity(appContext)
    }

    init {
        DouyinPlaybackPreviewCache.configure(libraryContext)
    }

    private val databaseDelegate = lazy {
        Room.databaseBuilder(
            libraryContext,
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
            WatchRssDatabase.MIGRATION_12_13,
            WatchRssDatabase.MIGRATION_13_14,
            WatchRssDatabase.MIGRATION_14_15,
            WatchRssDatabase.MIGRATION_15_16,
            WatchRssDatabase.MIGRATION_16_17,
            WatchRssDatabase.MIGRATION_17_18
        )
            .addCallback(BuiltinChannelSeed.callback)
            .build()
    }

    private val database: WatchRssDatabase by databaseDelegate

    /** Inserts an idempotent ten-page novel fixture for debug-only device verification. */
    internal suspend fun debugImportTenPageNovel(): Long = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val sourceUrl = "${com.lightningstudio.watchrss.data.rss.ImportedContentIds.TXT_NOVEL_SOURCE_ROOT_URL}/debug-10-pages"
        val channelDao = database.rssChannelDao()
        val itemDao = database.rssItemDao()
        database.withTransaction {
            val channel = channelDao.getChannelByUrl(sourceUrl) ?: run {
                val entity = RssChannelEntity(
                    url = sourceUrl,
                    title = "调试小说：手表上的微光（10页）",
                    description = "由调试 Intent 导入的本地十页小说，每章对应一页。",
                    imageUrl = null,
                    lastFetchedAt = now,
                    createdAt = now,
                    sortOrder = now,
                    isPinned = false,
                    useOriginalContent = true
                )
                val id = channelDao.insertChannel(entity)
                if (id > 0L) entity.copy(id = id) else channelDao.getChannelByUrl(sourceUrl)
                    ?: error("创建调试小说失败")
            }
            val pages = listOf(
                "雨夜的信号" to "林澈在旧车站捡到一只停摆的手表。表盘没有指针，玻璃下却映着一粒微弱的蓝光。午夜时分，蓝光沿着刻度缓缓移动，像有人从很远的地方敲门。\n\n那天夜里，雨下得很密。末班车驶离站台之后，整座车站像被一只看不见的手轻轻按住，广播停止了，灯管也只剩下候车室角落里一盏忽明忽暗的旧灯。林澈把工具箱放在长椅上，准备等雨小一些再走。他刚从城南的修表铺下班，袖口还沾着机油，口袋里只有几枚硬币和一张被雨水泡软的车票。\n\n他是在长椅下面发现那只表的。先是一声极轻的金属碰响，接着一团微光从木板缝里露出来。手表外壳暗得发乌，边缘有细密的划痕，表带则像被人匆忙扯断，只剩一截扣环。奇怪的是，表盘没有指针，玻璃内侧也没有数字，只有一道淡蓝色的光，在空白的圆面上缓慢游动。林澈以为那是某种夜光涂层，便拿出放大镜仔细查看。可光并没有固定在任何一处，它像水面上的倒影，随着他的呼吸微微起伏。\n\n修表多年，林澈见过停走的老怀表，见过被盐水泡坏的潜水表，也见过主人离世后仍准时响铃的闹钟。他习惯先听机芯的声音，再决定从哪里下手。于是他把表贴近耳边。里面没有滴答声，却传来一阵很远很远的回响，像有人隔着厚厚的墙敲了三下。第一下沉闷，第二下清楚，第三下仿佛就在他身后。林澈猛然回头，候车室空荡荡的，只有雨水沿着玻璃往下爬。\n\n他用小螺丝刀打开表背，里面没有常见的齿轮。金属底板中央嵌着一圈细如发丝的铜线，铜线围着一颗透明的石英，石英上刻着一个几乎看不见的字：澈。林澈的手指顿住了。他的名字并不罕见，但那道刻痕很新，像刚刚才被针尖划上去。工具箱里的手机忽然震动，屏幕显示凌晨零点整。与此同时，车站的旧钟开始走动，秒针从十二点的位置跳了一格。\n\n手表上的蓝光也在这一刻变亮。它没有指向时间，而是沿着表盘边缘一寸寸绕行，最后停在七点十二分的位置。林澈抬头看墙上的钟，那是一只早已拆掉电池的老式挂钟，玻璃蒙着灰，指针却同样停在七点十二分。两只钟隔着几十米，像突然认出了彼此。蓝光闪烁两次，候车室外的雨声短暂消失，站台尽头随即传来一声列车进站提示。\n\n广播里没有报出车次，只念了一个名字。声音沙哑而遥远，像从旧磁带里传出来：“林澈，请在北门下车。”他从没听过这样的广播。车站的北门早在十年前就封了，门外是一条拆迁后留下的窄巷，尽头只有一座停用的钟楼。林澈把手表塞进口袋，提起工具箱走向出口。他告诉自己，或许有人在恶作剧，或许这只表连接着某种无线设备。可当他走出候车室，站台上的灯一盏接一盏亮起，照出一排湿漉漉的脚印。脚印从北门方向延伸而来，却在他脚边突然中断。\n\n林澈没有立刻追过去。他先回到长椅旁，把刚才发现手表的位置记在便签上，又在表背上垫了一小片软布，免得那些奇异的刻痕被钥匙划伤。做完这些，他才顺着脚印走到北门。门锁锈得发红，门缝里却透出温暖的黄色灯光。林澈伸手触碰门把，口袋里的手表轻轻震了一下。那团蓝光重新沿着空白表盘移动，这次速度快了许多，像在催促他赶紧作出决定。\n\n门外的巷子比记忆里长。雨水顺着屋檐汇成细流，积水映出远处钟楼的轮廓。林澈刚跨出门槛，身后的车站便恢复了沉寂，广播和灯光一起熄灭。他回头看去，北门仍在原处，门内却不再是候车室，而是一面结着水雾的砖墙。手表的玻璃上浮现出一行极浅的字：沿河向北，找到没有寄件人的地图。\n\n他抬头确认方向，发现巷口的路牌也在雨中慢慢转动。原本指向南方的箭头转了一圈，最后稳稳朝向钟楼。林澈把工具箱换到左手，右手护住口袋里的手表。远处的钟楼顶端亮起一粒蓝光，与表盘上的光遥遥相应。他不知道这条路通向什么，也不知道那个叫他名字的人是谁，只知道自己第一次遇到一件无法拆开、无法解释、却确实正在等待修理的东西。雨还没有停，他却已经迈出了第一步。",
                "没有寄件人的地图" to "第二天，手表背面浮出一张细小地图，终点标在城北的钟楼。林澈带上修表工具出发，地图上的街巷却每隔几分钟就悄悄改变一次。",
                "钟楼里的回声" to "钟楼早已停用多年。推开沉重的门，林澈听见自己的脚步声之外，还有另一串脚步从楼上传来。那声音总比他慢半拍，像在模仿一个尚未发生的动作。",
                "迟到的留言" to "顶层的木柜里放着一封信，纸张崭新，落款却是二十年前失踪的钟表匠。信上只有一句话：当蓝光熄灭，请替我把时间交还给河流。",
                "河岸的旧工坊" to "地图把林澈带到河边的工坊。机器之间挂满拆开的表芯，每一枚齿轮都刻着一个人的名字。最里面那枚齿轮刻着林澈的名字，旁边还有今天的日期。",
                "停在七点十二分" to "手表忽然停在七点十二分。整座工坊安静下来，窗外的雨滴悬在半空。林澈终于明白，这只表保存的不是过去，而是每个人错过的一小段时间。",
                "修表人的选择" to "老钟表匠从暗门后走出。他请林澈拆下蓝色发光的游丝，代价是忘掉一段最珍贵的回忆。林澈握紧工具，决定先找出有没有第三种办法。",
                "把光分给所有人" to "林澈没有拆下游丝，而是将十枚小齿轮重新排成圆环。蓝光被分成许多细小的光点，沿着河岸亮起，照见那些被困在旧日时刻里的人们。",
                "重新走动的钟" to "钟楼响起第一声钟鸣。悬在半空的雨滴落回地面，工坊里的名字逐一淡去。老钟表匠把一枚普通指针交给林澈，说真正的时间要由活着的人继续走下去。",
                "清晨的下一页" to "清晨，林澈回到车站，手表已经恢复寻常。表盘下夹着一张空白纸，边缘透出新的蓝光。他把纸收进口袋，踏上回家的路，知道故事还有下一页。"
            )
            val items = pages.mapIndexed { index, (title, text) ->
                val chapter = index + 1
                val content = "<h2>第${chapter}页　$title</h2><p>$text</p><p>这是供真机阅读与章节恢复验证使用的原创测试内容。阅读到这一页后返回目录，再通过继续阅读即可检查阅读位置是否保存。</p>"
                com.lightningstudio.watchrss.data.db.RssItemEntity(
                    channelId = channel.id,
                    title = "第${chapter}页　$title",
                    description = text,
                    content = content,
                    originalContent = content,
                    link = "$sourceUrl/chapter/${chapter}-${chapter.toString().padStart(2, '0')}",
                    guid = "debug-novel-10-pages-$chapter",
                    pubDate = null,
                    imageUrl = null,
                    audioUrl = null,
                    videoUrl = null,
                    summary = text.take(100),
                    previewImageUrl = null,
                    isRead = false,
                    isLiked = false,
                    readingProgress = 0f,
                    dedupKey = "debug-novel-10-pages-$chapter",
                    fetchedAt = now + chapter,
                    contentSizeBytes = content.toByteArray(Charsets.UTF_8).size.toLong()
                )
            }
            itemDao.deleteDebugNovelFixtureItems(channel.id)
            itemDao.insertItems(items)
            channel.id
        }
    }

    suspend fun stopAccountTasks() {
        kotlinx.coroutines.withContext(Dispatchers.Main.immediate) {
            if (readAloudDelegate.isInitialized()) readAloudController.release()
        }
        accountJob.cancel()
        accountJob.join()
    }
    fun closeAccountDatabase() { if (databaseDelegate.isInitialized()) database.close() }

    private val installationIdentity: WatchInstallationIdentity by lazy {
        WatchInstallationIdentity(appContext)
    }

    override val watchUsageTelemetry: WatchUsageTelemetry by lazy {
        WatchUsageTelemetry(
            context = appContext,
            installationIdentity = installationIdentity,
            deviceId = deviceIdentity.deviceId,
            accountStore = watchAccountStore,
            tokenManager = watchTokenManager,
            appScope = appScope,
        )
    }

    override val settingsRepository: SettingsRepository by lazy {
        val dataStore = PreferenceDataStoreFactory.create(
            scope = appScope,
            produceFile = { libraryContext.preferencesDataStoreFile("settings.preferences_pb") }
        )
        SettingsRepository(dataStore)
    }

    override val llmApiKeyStore: LlmApiKeyStore by lazy {
        LlmApiKeyStore(libraryContext)
    }

    override val ttsApiKeyStore: TtsApiKeyStore by lazy {
        TtsApiKeyStore(libraryContext)
    }

    override val readerPresetRepository: ReaderPresetRepository by lazy {
        ReaderPresetRepository(
            context = libraryContext,
            database = database,
            dao = database.readerPresetDao(),
            deviceId = deviceIdentity.deviceId,
            scope = appScope
        ).also { repository ->
            appScope.launch {
                repository.ensureSeeded(
                    legacyDark = settingsRepository.readingThemeDark.first(),
                    legacyFontSizeSp = settingsRepository.readingFontSizeSp.first()
                )
            }
        }
    }

    override val watchTokenManager: WatchTokenManager by lazy {
        WatchTokenManager(watchAccountStore)
    }

    override val managedCacheService: ManagedCacheService by lazy {
        ManagedCacheService(libraryContext, settingsRepository, appScope).also { cacheService ->
            RssImageLoader.configure(cacheService)
        }
    }

    override val biliPlaybackCacheManager: BiliPlaybackCacheManager by lazy {
        BiliPlaybackCacheManager(libraryContext)
    }

    override val biliRepository: BiliRepositoryContract by lazy {
        val dataStore = PreferenceDataStoreFactory.create(
            scope = appScope,
            produceFile = { libraryContext.preferencesDataStoreFile("bili_cache.preferences_pb") }
        )
        BiliRepository(
            context = libraryContext,
            dataStore = dataStore,
            cacheService = managedCacheService,
            playbackCacheManager = biliPlaybackCacheManager
        )
    }

    override val douyinFeedCacheStore: DouyinFeedCacheStoreContract by lazy {
        DouyinFeedCacheStore(libraryContext)
    }

    override val douyinWatchHistoryStore: DouyinWatchHistoryStoreContract by lazy {
        DouyinWatchHistoryStore(libraryContext)
    }

    override val douyinRecentWindowStore: DouyinRecentWindowStoreContract by lazy {
        DouyinRecentWindowStore(libraryContext)
    }

    override val douyinPreloadManager: DouyinPreloadManagerContract by lazy {
        DouyinPreloadManager(libraryContext, managedCacheService)
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
            context = libraryContext,
            cacheService = managedCacheService,
            recentWindowStore = douyinRecentWindowStore
        )
    }

    override val douyinPlaybackSourceCoordinator: DouyinPlaybackSourceCoordinatorContract by lazy {
        DouyinPlaybackSourceCoordinator(
            appScope = appScope,
            repository = douyinRepository,
            feedCacheStore = douyinFeedCacheStore,
            recentWindowStore = douyinRecentWindowStore
        )
    }

    override val internetAvailabilityMonitor: InternetAvailabilityMonitor by lazy {
        DefaultInternetAvailabilityMonitor(appContext)
    }

    override val rssRepository: RssRepository by lazy {
        val fetchService = RssFetchService()
        val readableService = RssReadableService()
        val parseService = RssParseService()
        val articleContentStore = FileArticleContentStore(libraryContext)
        val offlineStore = RssOfflineStore(
            libraryContext,
            database.offlineMediaDao(),
            fetchService,
            managedCacheService
        )
        DefaultRssRepository(
            database = database,
            channelDao = database.rssChannelDao(),
            itemDao = database.rssItemDao(),
            savedEntryDao = database.savedEntryDao(),
            savedSyncStateDao = database.savedSyncStateDao(),
            offlineMediaDao = database.offlineMediaDao(),
            syncChangeLogDao = database.syncChangeLogDao(),
            syncPeerStateDao = database.syncPeerStateDao(),
            rssSourceSyncStateDao = database.rssSourceSyncStateDao(),
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

    override val llmTokenUsageRepository: LlmTokenUsageRepository by lazy {
        LlmTokenUsageRepository(database.llmTokenUsageDao())
    }

    override val watchNoteRepository: WatchNoteRepository by lazy { WatchNoteRepository(database.watchNoteDao()) }

    private val readAloudDelegate = lazy {
        ReadAloudController(
            context = appContext,
            appScope = appScope,
            rssRepository = rssRepository,
            ttsEngineFactory = TtsEngineFactory(
                context = appContext,
                settingsRepository = settingsRepository,
                ttsApiKeyProvider = ttsApiKeyStore,
                watchAccountStore = watchAccountStore
            ),
            playbackStartVolumeLimiter = AudioManagerMediaPlaybackStartVolumeLimiter(appContext),
            playbackStartVolumeLimitPercentProvider = {
                kotlinx.coroutines.runBlocking { settingsRepository.mediaPlaybackStartVolumeLimitPercent.first() }
            },
            onBeforeNewSession = {
                appContext.stopService(android.content.Intent(appContext, com.lightningstudio.watchrss.data.media.BiliAudioService::class.java))
            }
        )
    }
    override val readAloudController: ReadAloudController by readAloudDelegate
}
