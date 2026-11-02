package com.lightningstudio.watchrss.ui.activity

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import java.io.File
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.lifecycle.Lifecycle
import androidx.room.Room
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.lightningstudio.watchrss.ChannelActionsActivity
import com.lightningstudio.watchrss.FeedActivity
import com.lightningstudio.watchrss.DetailActivity
import com.lightningstudio.watchrss.data.db.*
import com.lightningstudio.watchrss.data.rss.*
import com.lightningstudio.watchrss.testutil.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import java.util.UUID

/** Synthetic two-book library in an independent database; user read flags are never changed. */
class NovelReadStateTest {
    private val compose = createEmptyComposeRule()
    private lateinit var repository: Fixture
    private val container = TestAppContainerRule { context ->
        repository = Fixture(context)
        TestAppContainer(context, repository, createTestSettingsRepository(context, "novel-read-test"))
    }
    @get:Rule val rules: RuleChain = RuleChain.outerRule(container).around(compose)
    private var scenario: ActivityScenario<*>? = null
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @After fun cleanup() {
        currentResumedActivity()?.takeIf { it is DetailActivity }?.let { activity ->
            InstrumentationRegistry.getInstrumentation().runOnMainSync { activity.finish() }
        }
        scenario?.close()
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        repository.close()
    }

    @Test fun markWholeBook_keepsMenuOpenUntilWriteAndUnreadCountConfirm() {
        scenario = ActivityScenario.launch<ChannelActionsActivity>(
            Intent(context, ChannelActionsActivity::class.java).putExtra(ChannelActionsActivity.EXTRA_CHANNEL_ID, 7L)
        )
        waitForText("标记已读")
        compose.onNodeWithText("标记已读").performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals("Menu must not finish and cancel the pending write", Lifecycle.State.RESUMED, scenario!!.state)
        assertFalse(runBlocking { repository.database.rssItemDao().getItem(71)!!.isRead })
        capture("01-book-write-pending.png")
        repository.allowWrite.complete(Unit)
        waitUntil(5_000) { scenario!!.state == Lifecycle.State.DESTROYED }
        assertTrue(runBlocking { repository.database.rssItemDao().getItem(71)!!.isRead })
        assertTrue(runBlocking { repository.database.rssItemDao().getItem(73)!!.isRead })
        assertFalse(runBlocking { repository.database.rssItemDao().getItem(81)!!.isRead })
        assertEquals(0, runBlocking { repository.observeChannel(7).first()!!.unreadCount })
        repository.reopen()
        assertEquals(0, runBlocking { repository.observeChannel(7).first()!!.unreadCount })
        assertEquals(1, runBlocking { repository.observeChannel(8).first()!!.unreadCount })
    }

    @Test fun enteringChapter_waitsForReadPersistence_andOtherUnreadChapterStaysUnread() {
        scenario = ActivityScenario.launch<FeedActivity>(
            Intent(context, FeedActivity::class.java).putExtra(FeedActivity.EXTRA_CHANNEL_ID, 7L)
        )
        compose.waitUntil(5_000) { compose.onAllNodesWithContentDescription("章节：章节73，未读").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("章节：章节73，未读").performScrollTo().performClick()
        compose.waitForIdle()
        assertTrue("Do not open the reader before read-state persistence", currentResumedActivity() is FeedActivity)
        repository.allowWrite.complete(Unit)
        waitUntil(5_000) { currentResumedActivity() is DetailActivity }
        assertTrue(runBlocking { repository.database.rssItemDao().getItem(73)!!.isRead })
        assertFalse(runBlocking { repository.database.rssItemDao().getItem(71)!!.isRead })
        assertEquals(1, runBlocking { repository.observeChannel(7).first()!!.unreadCount })
        val detail = currentResumedActivity()!!
        InstrumentationRegistry.getInstrumentation().runOnMainSync { detail.finish() }
        waitUntil(5_000) { currentResumedActivity() is FeedActivity }
        compose.onNodeWithContentDescription("章节：章节73").assertExists()
        compose.onNodeWithContentDescription("章节：章节73，未读").assertDoesNotExist()
        capture("02-chapter-return.png")
    }

    @Test fun failedWholeBookWrite_keepsUnreadFlagsAndAllowsRetry() {
        repository.failWrites = true
        repository.allowWrite.complete(Unit)
        scenario = ActivityScenario.launch<ChannelActionsActivity>(
            Intent(context, ChannelActionsActivity::class.java).putExtra(ChannelActionsActivity.EXTRA_CHANNEL_ID, 7L)
        )
        waitForText("标记已读")
        compose.onNodeWithText("标记已读").performScrollTo().performClick()
        waitForText("标记已读失败，请重试")
        assertEquals(Lifecycle.State.RESUMED, scenario!!.state)
        assertFalse(runBlocking { repository.database.rssItemDao().getItem(71)!!.isRead })
        repository.failWrites = false
        compose.onNodeWithText("标记已读").performScrollTo().performClick()
        waitUntil(5_000) { scenario!!.state == Lifecycle.State.DESTROYED }
        assertEquals(0, runBlocking { repository.observeChannel(7).first()!!.unreadCount })
    }

    @Test fun persistedWrite_waitsForDelayedObservableCountBeforeClosing() {
        repository.holdConfirmation = true
        repository.allowWrite.complete(Unit)
        scenario = ActivityScenario.launch<ChannelActionsActivity>(
            Intent(context, ChannelActionsActivity::class.java).putExtra(ChannelActionsActivity.EXTRA_CHANNEL_ID, 7L)
        )
        waitForText("标记已读")
        compose.onNodeWithText("标记已读").performScrollTo().performClick()
        waitUntil(5_000) { runBlocking { repository.database.rssItemDao().getItem(71)!!.isRead } }
        assertEquals(Lifecycle.State.RESUMED, scenario!!.state)
        repository.releaseConfirmation()
        waitUntil(5_000) { scenario!!.state == Lifecycle.State.DESTROYED }
    }

    @Test fun normalRssArticleNavigation_doesNotOptIntoNovelWriteGate() {
        repository.rssMode = true
        scenario = ActivityScenario.launch<FeedActivity>(
            Intent(context, FeedActivity::class.java).putExtra(FeedActivity.EXTRA_CHANNEL_ID, 7L)
        )
        compose.waitUntil(5_000) { compose.onAllNodesWithContentDescription("文章：章节73，未读").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("文章：章节73，未读").performScrollTo().performClick()
        waitUntil(5_000) { currentResumedActivity() is DetailActivity }
        assertFalse(repository.allowWrite.isCompleted)
    }

    @Test fun chapteredTxtAlsoUsesReadConfirmationGate() {
        repository.txtMode = true
        scenario = ActivityScenario.launch<FeedActivity>(
            Intent(context, FeedActivity::class.java).putExtra(FeedActivity.EXTRA_CHANNEL_ID, 7L)
        )
        compose.waitUntil(5_000) { compose.onAllNodesWithContentDescription("章节：章节73，未读").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("章节：章节73，未读").performScrollTo().performClick()
        compose.waitForIdle()
        assertTrue(currentResumedActivity() is FeedActivity)
        repository.allowWrite.complete(Unit)
        waitUntil(5_000) { currentResumedActivity() is DetailActivity }
        assertTrue(runBlocking { repository.database.rssItemDao().getItem(73)!!.isRead })
    }

    private fun capture(name: String) {
        val directory = File(context.getExternalFilesDir(null), "bug005").apply { mkdirs() }
        File(directory, name).outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    private fun waitForText(text: String) {
        compose.waitUntil(5_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    }

    private class Fixture(private val context: Context) : RssRepository by FakeRssRepository() {
        private val name = "novel-read-${UUID.randomUUID()}"
        var database = Room.databaseBuilder(context, WatchRssDatabase::class.java, name).build()
        val allowWrite = CompletableDeferred<Unit>()
        @Volatile var failWrites = false
        @Volatile var holdConfirmation = false
        @Volatile var rssMode = false
        @Volatile var txtMode = false
        private val changed = MutableStateFlow(0)
        init {
            runBlocking {
                for (book in listOf(7L, 8L)) {
                    database.rssChannelDao().insertChannel(RssChannelEntity(book,
                        "${ImportedContentIds.EPUB_SOURCE_ROOT_URL}/book-$book", "小说$book", null, null,
                        null, 0, book, false))
                }
                database.rssItemDao().insertItems(listOf(71L, 72L, 73L, 81L).map { id ->
                    RssItemEntity(id, id / 10, "章节$id", null, "<p>合成正文$id</p>", null,
                        "${ImportedContentIds.EPUB_SOURCE_ROOT_URL}/book-${id / 10}/$id", null, null,
                        null, null, null, "测试章节", null, id == 72L, false, 0f, "test-$id", id, 40)
                })
            }
        }
        override fun observeChannel(channelId: Long): Flow<RssChannel?> = changed.map {
            val channel = database.rssChannelDao().getChannel(channelId) ?: return@map null
            val unread = if (holdConfirmation && channelId == 7L) 2 else
                database.rssItemDao().getItemsForChannelSync(channelId, 100).count { !it.isRead }
            RssChannel(channel.id, if (rssMode) "https://example.invalid/feed" else if (txtMode) channel.url.replace("import-epub", "import-txt-novel") else channel.url, channel.title, null, null, null, channelId, false, false, unread)
        }
        override fun observeItemsPaged(channelId: Long, limit: Int) = changed.map {
            database.rssItemDao().getItemsForChannelSync(channelId, limit).map { it.model() }
        }
        override fun observeItemCount(channelId: Long) = changed.map {
            database.rssItemDao().getItemsForChannelSync(channelId, 100).size
        }
        override fun observeItem(itemId: Long) = changed.map { database.rssItemDao().getItem(itemId)?.model() }
        override suspend fun markChannelRead(channelId: Long) {
            allowWrite.await()
            check(!failWrites) { "injected write failure" }
            database.rssItemDao().markReadByChannel(channelId)
            changed.update { it + 1 }
        }
        override suspend fun markItemRead(itemId: Long) {
            allowWrite.await()
            check(!failWrites) { "injected write failure" }
            database.rssItemDao().markRead(itemId)
            changed.update { it + 1 }
        }
        fun releaseConfirmation() { holdConfirmation = false; changed.update { it + 1 } }
        fun reopen() {
            database.close()
            database = Room.databaseBuilder(context, WatchRssDatabase::class.java, name).build()
        }
        fun close() { database.close(); context.deleteDatabase(name) }
        private fun RssItemEntity.model() = RssItem(id, channelId, title, description, content, originalContent,
            if (rssMode) "https://example.invalid/article/$id" else if (txtMode) link?.replace("import-epub", "import-txt-novel") else link, pubDate, imageUrl, audioUrl, videoUrl, summary, previewImageUrl, isRead, isLiked,
            readingProgress, fetchedAt)
    }
}
