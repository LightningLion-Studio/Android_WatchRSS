package com.lightningstudio.watchrss.ui.activity

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.lightningstudio.watchrss.AppResumeStateStore
import com.lightningstudio.watchrss.DetailActivity
import com.lightningstudio.watchrss.FeedActivity
import com.lightningstudio.watchrss.data.novel.NovelReadingHistory
import com.lightningstudio.watchrss.data.rss.*
import com.lightningstudio.watchrss.testutil.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.RuleChain
import java.io.File
import java.util.UUID

/** Production activities + production chapter preferences; repository contains synthetic books only. */
class NovelContentsResumeTest {
    private val compose = createEmptyComposeRule()
    private val fixture = Library()
    private val container = TestAppContainerRule { context ->
        TestAppContainer(context, fixture, createTestSettingsRepository(context, "novel-catalog-test"))
    }
    @get:Rule val rules: RuleChain = RuleChain.outerRule(container).around(compose)
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private var scenario: ActivityScenario<FeedActivity>? = null
    private var previousResume: Intent? = null
    private lateinit var history: NovelReadingHistory

    @Before fun preserveUserResumeState() {
        previousResume = AppResumeStateStore.load(context)
        history = NovelReadingHistory(context)
    }

    @After fun cleanupSyntheticState() {
        currentResumedActivity()?.takeIf { it is DetailActivity }?.let { activity ->
            InstrumentationRegistry.getInstrumentation().runOnMainSync { activity.finish() }
        }
        scenario?.close()
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        fixture.books.values.forEach { history.removeBook(it) }
        previousResume?.let { AppResumeStateStore.save(context, it) } ?: AppResumeStateStore.clear(context)
    }

    @Test fun twoLongBooksRememberTheirOwnChapter_afterBackReopenAndRecreate() {
        readAndReturn(7, 310)
        scenario!!.close()
        scenario = null
        readAndReturn(8, 240)
        scenario!!.close()
        scenario = null

        open(7)
        visible("novel-chapter-7310")
        compose.onNodeWithText("上次阅读").assertIsDisplayed()
        scenario!!.recreate()
        visible("novel-chapter-7310")
        capture("01-epub-chapter-310.png")
        scenario!!.close()
        scenario = null
        open(8)
        visible("novel-chapter-8240")
        compose.onNodeWithText("上次阅读").assertIsDisplayed()
        capture("02-txt-chapter-240.png")
        val reopenedHistory = NovelReadingHistory(context)
        assertEquals(fixture.url(7, 310), reopenedHistory.lastChapter(fixture.books.getValue(7)))
        assertEquals(fixture.url(8, 240), reopenedHistory.lastChapter(fixture.books.getValue(8)))
        assertTrue("Chapter metadata must not be capped at the RSS limit", Int.MAX_VALUE in fixture.requestedLimits)
    }

    @Test fun deletedLastChapterUsesNearestFollowingChapter_withoutChangingTheSavedRecord() {
        runBlocking { assertTrue(history.recordChapter(fixture.url(7, 310))) }
        fixture.removeChapter(7, 310)
        open(7)
        visible("novel-chapter-7311")
        compose.onNodeWithText("上次位置附近").assertIsDisplayed()
        assertEquals(fixture.url(7, 310), history.lastChapter(fixture.books.getValue(7)))
    }

    @Test fun firstOpenStartsAtFirstChapterInNumericOrder() {
        open(7)
        visible("novel-chapter-7001")
        compose.onNodeWithText("共 350 章").assertIsDisplayed()
        assertNull(history.lastChapter(fixture.books.getValue(7)))
        compose.onNodeWithText("上次阅读").assertDoesNotExist()
    }

    private fun readAndReturn(book: Int, number: Int) {
        open(book)
        compose.waitUntil(5_000) { compose.onAllNodesWithText("共 350 章").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("novel-contents-list").performScrollToIndex(number)
        compose.onNodeWithTag("novel-chapter-${book * 1000 + number}").assertIsDisplayed().performClick()
        waitUntil(5_000) { currentResumedActivity() is DetailActivity }
        waitUntil(5_000) { history.lastChapter(fixture.books.getValue(book)) == fixture.url(book, number) }
        val detail = currentResumedActivity()!!
        InstrumentationRegistry.getInstrumentation().runOnMainSync { detail.finish() }
        waitUntil(5_000) { currentResumedActivity() is FeedActivity }
        visible("novel-chapter-${book * 1000 + number}")
    }

    private fun open(book: Int) {
        scenario = ActivityScenario.launch(Intent(context, FeedActivity::class.java)
            .putExtra(FeedActivity.EXTRA_CHANNEL_ID, book.toLong()))
    }

    private fun visible(tag: String) {
        compose.waitUntil(8_000) {
            runCatching { compose.onNodeWithTag(tag).assertIsDisplayed(); true }.getOrDefault(false)
        }
    }

    private fun capture(name: String) {
        val output = File(context.getExternalFilesDir(null), "req001").apply { mkdirs() }
        File(output, name).outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    private class Library : RssRepository by FakeRssRepository() {
        private val suffix = UUID.randomUUID().toString()
        val books = mapOf(7 to "${ImportedContentIds.EPUB_SOURCE_ROOT_URL}/test-$suffix-epub",
            8 to "${ImportedContentIds.TXT_NOVEL_SOURCE_ROOT_URL}/test-$suffix-txt")
        val requestedLimits = mutableListOf<Int>()
        private val all = MutableStateFlow(books.keys.flatMap { book ->
            (350 downTo 1).map { number ->
                RssItem((book * 1000 + number).toLong(), book.toLong(), "第${number}章 合成测试", null,
                    "<p>第${number}章的合成正文。</p>", null, url(book, number), null,
                    null, null, null, "测试目录", null, false, false, 0f, number.toLong())
            }
        })
        fun url(book: Int, number: Int) = "${books.getValue(book)}/chapter/${number.toString().padStart(4, '0')}-content"
        fun removeChapter(book: Int, number: Int) { all.update { values -> values.filterNot { it.id == (book * 1000 + number).toLong() } } }
        override fun observeChannel(channelId: Long) = all.map { values ->
            books[channelId.toInt()]?.let { url -> RssChannel(channelId, url,
                if (channelId == 7L) "EPUB测试书" else "TXT测试书", null, null, null, channelId, false, false,
                values.count { it.channelId == channelId && !it.isRead }) }
        }
        override fun observeItemsPaged(channelId: Long, limit: Int): Flow<List<RssItem>> {
            requestedLimits += limit
            return all.map { values -> values.filter { it.channelId == channelId }.take(limit) }
        }
        override fun observeItem(itemId: Long) = all.map { values -> values.find { it.id == itemId } }
        override fun observeItemCount(channelId: Long) = all.map { values -> values.count { it.channelId == channelId } }
        override suspend fun markItemRead(itemId: Long) { all.update { values -> values.map { if (it.id == itemId) it.copy(isRead = true) else it } } }
    }
}
