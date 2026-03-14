package com.lightningstudio.watchrss.ui.activity

import android.content.Context
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lightningstudio.watchrss.BiliChannelInfoActivity
import com.lightningstudio.watchrss.BiliCommentActivity
import com.lightningstudio.watchrss.BiliDetailActivity
import com.lightningstudio.watchrss.BiliEntryActivity
import com.lightningstudio.watchrss.BiliListActivity
import com.lightningstudio.watchrss.BiliLoginActivity
import com.lightningstudio.watchrss.BiliPlayerActivity
import com.lightningstudio.watchrss.BiliReplyDetailActivity
import com.lightningstudio.watchrss.BiliSearchActivity
import com.lightningstudio.watchrss.BiliSettingsActivity
import com.lightningstudio.watchrss.data.settings.CURRENT_OOBE_VERSION
import com.lightningstudio.watchrss.sdk.bili.BiliResult
import com.lightningstudio.watchrss.sdk.bili.WebQrCode
import com.lightningstudio.watchrss.sdk.bili.QrPollResult
import com.lightningstudio.watchrss.sdk.bili.QrPollStatus
import com.lightningstudio.watchrss.testutil.FakeBiliRepository
import com.lightningstudio.watchrss.testutil.FakeRssRepository
import com.lightningstudio.watchrss.testutil.TestAppContainer
import com.lightningstudio.watchrss.testutil.TestAppContainerRule
import com.lightningstudio.watchrss.testutil.createTestSettingsRepository
import com.lightningstudio.watchrss.testutil.sampleBiliBuiltinChannel
import com.lightningstudio.watchrss.testutil.sampleBiliCommentPage
import com.lightningstudio.watchrss.testutil.sampleBiliFavoriteFolders
import com.lightningstudio.watchrss.testutil.sampleBiliFavoritePage
import com.lightningstudio.watchrss.testutil.sampleBiliHistoryPage
import com.lightningstudio.watchrss.testutil.sampleBiliHotSearch
import com.lightningstudio.watchrss.testutil.sampleBiliItem
import com.lightningstudio.watchrss.testutil.sampleBiliReplyPage
import com.lightningstudio.watchrss.testutil.sampleBiliSearchResponse
import com.lightningstudio.watchrss.testutil.sampleBiliToViewPage
import com.lightningstudio.watchrss.testutil.sampleBiliVideoDetail
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BiliActivitySmokeTest {
    private val sampleItem = sampleBiliItem()
    private val fakeBiliRepository = FakeBiliRepository(
        initialLoggedIn = true,
        initialFeedItems = listOf(sampleItem),
        initialFeedCache = listOf(sampleItem),
        initialFavoriteFolders = sampleBiliFavoriteFolders(),
        initialSearchHistory = listOf("测试关键词"),
        initialPlayHeaders = mapOf("Referer" to "https://www.bilibili.com")
    ).apply {
        webQrCode = WebQrCode(qrKey = "fake-qr-key", url = "https://example.com/qr")
        webQrPollResult = QrPollResult(
            status = QrPollStatus.PENDING,
            rawCode = 0,
            cookies = emptyMap()
        )
        feedResult = BiliResult(0, data = com.lightningstudio.watchrss.testutil.sampleBiliFeedPage(listOf(sampleItem)))
        favoriteFoldersResult = BiliResult(0, data = sampleBiliFavoriteFolders())
        toViewResult = BiliResult(0, data = sampleBiliToViewPage(sampleItem))
        historyResult = BiliResult(0, data = sampleBiliHistoryPage(sampleItem))
        hotSearchResult = BiliResult(0, data = sampleBiliHotSearch())
        setSearchResult("测试关键词", 1, BiliResult(0, data = sampleBiliSearchResponse(sampleItem)))
        setComments(sampleItem.aid ?: 0L, 0L, BiliResult(0, data = sampleBiliCommentPage()))
        setReplies(sampleItem.aid ?: 0L, 1L, 1, BiliResult(0, data = sampleBiliReplyPage()))
        setVideoDetail(sampleItem.aid, sampleItem.bvid, sampleBiliVideoDetail(sampleItem))
        setFavoriteItems(1L, 1, BiliResult(0, data = sampleBiliFavoritePage(sampleItem)))
    }
    private val fakeRssRepository = FakeRssRepository(
        initialChannels = listOf(sampleBiliBuiltinChannel())
    )

    private val containerRule = TestAppContainerRule { context ->
        val settingsRepository = createTestSettingsRepository(context, "bili-smoke")
        runBlocking {
            settingsRepository.setOobeSeenVersion(CURRENT_OOBE_VERSION)
            settingsRepository.setShareUseSystem(false)
            settingsRepository.setReadingThemeDark(true)
            settingsRepository.setReadingFontSizeSp(14)
        }
        TestAppContainer(
            context = context,
            rssRepository = fakeRssRepository,
            settingsRepository = settingsRepository,
            biliRepositoryOverride = fakeBiliRepository
        )
    }

    @get:Rule
    val ruleChain: RuleChain = RuleChain.outerRule(containerRule)

    @Test
    fun biliEntryActivity_launches() {
        launchAndAssertResumed(BiliEntryActivity::class.java)
    }

    @Test
    fun biliLoginActivity_launches() {
        launchAndAssertResumed(BiliLoginActivity.createIntent(context()))
    }

    @Test
    fun biliChannelInfoActivity_launches() {
        launchAndAssertResumed(BiliChannelInfoActivity.createIntent(context()))
    }

    @Test
    fun biliListActivity_launches() {
        launchAndAssertResumed(BiliListActivity.createIntent(context(), com.lightningstudio.watchrss.ui.viewmodel.BiliListType.FAVORITE))
    }

    @Test
    fun biliSearchActivity_launches() {
        launchAndAssertResumed(BiliSearchActivity.createIntent(context()))
    }

    @Test
    fun biliCommentActivity_launches() {
        launchAndAssertResumed(BiliCommentActivity.createIntent(context(), sampleItem.aid ?: 0L, sampleItem.owner?.mid ?: 0L))
    }

    @Test
    fun biliReplyDetailActivity_launches() {
        launchAndAssertResumed(BiliReplyDetailActivity.createIntent(context(), sampleItem.aid ?: 0L, 1L, sampleItem.owner?.mid ?: 0L))
    }

    @Test
    fun biliDetailActivity_launches() {
        launchAndAssertResumed(BiliDetailActivity.createIntent(context(), sampleItem.aid, sampleItem.bvid, sampleItem.cid))
    }

    @Test
    fun biliPlayerActivity_launches() {
        launchAndAssertResumed(
            BiliPlayerActivity.createIntent(
                context = context(),
                aid = sampleItem.aid,
                bvid = sampleItem.bvid,
                cid = sampleItem.cid,
                title = sampleItem.title,
                owner = sampleItem.owner?.name
            )
        )
    }

    @Test
    fun biliSettingsActivity_launches() {
        launchAndAssertResumed(BiliSettingsActivity.createIntent(context()))
    }

    private fun context(): Context = ApplicationProvider.getApplicationContext()

    private fun launchAndAssertResumed(activityClass: Class<*>) {
        ActivityScenario.launch<android.app.Activity>(android.content.Intent(context(), activityClass)).use { scenario ->
            assertEquals(Lifecycle.State.RESUMED, scenario.state)
            scenario.onActivity { activity ->
                assertFalse(activity.isFinishing)
                assertFalse(activity.isDestroyed)
            }
        }
    }

    private fun launchAndAssertResumed(intent: android.content.Intent) {
        ActivityScenario.launch<android.app.Activity>(intent).use { scenario ->
            assertEquals(Lifecycle.State.RESUMED, scenario.state)
            scenario.onActivity { activity ->
                assertFalse(activity.isFinishing)
                assertFalse(activity.isDestroyed)
            }
        }
    }
}
