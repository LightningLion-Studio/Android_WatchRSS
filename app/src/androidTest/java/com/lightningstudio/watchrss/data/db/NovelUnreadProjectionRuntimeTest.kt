package com.lightningstudio.watchrss.data.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import android.content.Context
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NovelUnreadProjectionRuntimeTest {
    private lateinit var database: WatchRssDatabase

    @Before
    fun createDatabase() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            WatchRssDatabase::class.java
        ).allowMainThreadQueries().build()
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun bookUnreadCountTracksChapterReadStateFromTheSameRoomRows() = runBlocking {
        val now = System.currentTimeMillis()
        val channelDao = database.rssChannelDao()
        val itemDao = database.rssItemDao()
        val bookUrl = "https://watchrss.local/import-epub/bug-005-book"
        val channelId = channelDao.insertChannel(
            RssChannelEntity(
                url = bookUrl,
                title = "BUG-005 小说",
                description = null,
                imageUrl = null,
                lastFetchedAt = now,
                createdAt = now,
                sortOrder = now,
                isPinned = false
            )
        )
        itemDao.insertItems((1..2).map { chapter ->
            RssItemEntity(
                channelId = channelId,
                title = "第 $chapter 章",
                description = null,
                content = "正文 $chapter",
                originalContent = null,
                link = "$bookUrl/chapter/$chapter-chapter-$chapter",
                guid = "bug-005-chapter-$chapter",
                pubDate = null,
                imageUrl = null,
                audioUrl = null,
                videoUrl = null,
                summary = null,
                previewImageUrl = null,
                isRead = false,
                isLiked = false,
                readingProgress = 0f,
                dedupKey = "bug-005-chapter-$chapter",
                fetchedAt = now + chapter,
                contentSizeBytes = 8
            )
        })
        val chapters = itemDao.observeNovelChapters(channelId).first()
        assertEquals(2, chapters.size)
        assertEquals(2, channelDao.observeChannelWithUnreadCount(channelId).first()!!.unreadCount)

        itemDao.markRead(chapters.first().id)

        assertTrue(itemDao.observeItem(chapters.first().id).first()!!.isRead)
        assertEquals(1, channelDao.observeChannelWithUnreadCount(channelId).first()!!.unreadCount)

        itemDao.markReadByChannel(channelId)

        assertTrue(itemDao.observeNovelChapters(channelId).first().isNotEmpty())
        assertEquals(0, channelDao.observeChannelWithUnreadCount(channelId).first()!!.unreadCount)
    }
}
