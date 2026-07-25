package com.lightningstudio.watchrss.testutil

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import com.lightningstudio.watchrss.WatchRssApplication
import com.lightningstudio.watchrss.data.db.RssChannelDao
import com.lightningstudio.watchrss.data.db.RssChannelEntity
import com.lightningstudio.watchrss.data.db.RssItemDao
import com.lightningstudio.watchrss.data.db.RssItemEntity
import com.lightningstudio.watchrss.data.rss.DefaultRssRepository
import com.lightningstudio.watchrss.data.settings.CURRENT_OOBE_VERSION
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * 在真实 App 数据库中插入/清理测试数据。
 *
 * 这里不使用 mock Repository 或 Container，而是直接操作
 * [WatchRssApplication] 的真实 Room DAO，确保截图测试看到的数据
 * 与真实运行环境一致。
 */
object RealDataTestHelper {

    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    private val application: WatchRssApplication
        get() = context.applicationContext as WatchRssApplication

    private val channelDao: RssChannelDao
        get() = (application.container.rssRepository as DefaultRssRepository).testChannelDao

    private val itemDao: RssItemDao
        get() = (application.container.rssRepository as DefaultRssRepository).testItemDao

    /**
     * 清空与截图测试相关的数据表，并把 OOBE 标记为已看过，
     * 避免冷启动时跳转到 OOBE 页面。
     */
    fun clearTestData() = runBlocking {
        val channels = channelDao.observeChannels().first()
        channels.forEach { channel ->
            itemDao.deleteByChannel(channel.id)
            channelDao.deleteChannel(channel.id)
        }
        application.container.settingsRepository.setOobeSeenVersion(CURRENT_OOBE_VERSION)
    }

    /**
     * 构造一个稳定的 RSS 频道，字段使用固定时间戳，避免基线漂移。
     */
    fun sampleChannel(
        id: Long = 0L,
        url: String = "https://example.com/feed.xml",
        title: String = "示例 RSS 源",
        description: String = "用于截图测试的示例源",
        isPinned: Boolean = false,
        sortOrder: Long = FIXED_TIMESTAMP
    ): RssChannelEntity = RssChannelEntity(
        id = id,
        url = url,
        title = title,
        description = description,
        imageUrl = null,
        lastFetchedAt = FIXED_TIMESTAMP,
        createdAt = FIXED_TIMESTAMP,
        sortOrder = sortOrder,
        isPinned = isPinned,
        useOriginalContent = false
    )

    /**
     * 构造一篇稳定的 RSS 文章。
     */
    fun sampleItem(
        id: Long = 0L,
        channelId: Long,
        title: String = "示例文章标题",
        description: String = "这是用于截图测试的文章摘要。",
        isRead: Boolean = false,
        dedupKey: String = "screenshot-item-1"
    ): RssItemEntity = RssItemEntity(
        id = id,
        channelId = channelId,
        title = title,
        description = description,
        content = "<p>这是用于截图测试的文章正文。</p>",
        originalContent = null,
        link = "https://example.com/article-1",
        guid = dedupKey,
        pubDate = "2026-07-25",
        imageUrl = null,
        audioUrl = null,
        videoUrl = null,
        summary = description,
        previewImageUrl = null,
        isRead = isRead,
        isLiked = false,
        readingProgress = 0f,
        dedupKey = dedupKey,
        fetchedAt = FIXED_TIMESTAMP,
        contentSizeBytes = 0L
    )

    /**
     * 插入一组完整的截图测试数据：
     * - 2 个普通 RSS 源（一个有未读文章、一个已读）
     * - 1 个置顶 RSS 源
     */
    fun seedPopulatedLibrary() = runBlocking {
        clearTestData()

        val channel1 = sampleChannel(
            url = "https://example.com/feed.xml",
            title = "示例 RSS 源",
            description = "用于截图测试的示例源",
            sortOrder = FIXED_TIMESTAMP
        )
        val channel1Id = channelDao.insertChannel(channel1)

        val channel2 = sampleChannel(
            url = "https://example.com/feed2.xml",
            title = "第二个示例源",
            description = "第二个用于截图测试的示例源",
            sortOrder = FIXED_TIMESTAMP + 1
        )
        val channel2Id = channelDao.insertChannel(channel2)

        val channel3 = sampleChannel(
            url = "https://example.com/pinned.xml",
            title = "置顶示例源",
            description = "置顶的示例 RSS 源",
            isPinned = true,
            sortOrder = FIXED_TIMESTAMP + 2
        )
        val channel3Id = channelDao.insertChannel(channel3)

        if (channel1Id > 0) {
            itemDao.insertItems(
                listOf(
                    sampleItem(
                        channelId = channel1Id,
                        title = "示例文章一",
                        description = "这是第一篇示例文章的摘要内容。",
                        dedupKey = "screenshot-item-1"
                    ),
                    sampleItem(
                        channelId = channel1Id,
                        title = "示例文章二",
                        description = "这是第二篇示例文章的摘要内容。",
                        isRead = true,
                        dedupKey = "screenshot-item-2"
                    )
                )
            )
        }

        if (channel3Id > 0) {
            itemDao.insertItems(
                listOf(
                    sampleItem(
                        channelId = channel3Id,
                        title = "置顶源文章",
                        description = "置顶源中的示例文章摘要。",
                        dedupKey = "screenshot-item-3"
                    )
                )
            )
        }
    }

    /**
     * 插入一个空的资料库（仅清空，用于空状态截图）。
     */
    fun seedEmptyLibrary() = runBlocking {
        clearTestData()
    }

    private const val FIXED_TIMESTAMP = 1_725_000_000_000L
}
