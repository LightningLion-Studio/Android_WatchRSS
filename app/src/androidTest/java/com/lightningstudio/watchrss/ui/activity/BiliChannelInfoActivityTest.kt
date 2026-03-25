package com.lightningstudio.watchrss.ui.activity

import android.os.SystemClock
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.espresso.Espresso.pressBack
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lightningstudio.watchrss.BiliChannelInfoActivity
import com.lightningstudio.watchrss.BiliSearchActivity
import com.lightningstudio.watchrss.BiliSettingsActivity
import com.lightningstudio.watchrss.data.settings.CURRENT_OOBE_VERSION
import com.lightningstudio.watchrss.sdk.bili.BiliResult
import com.lightningstudio.watchrss.testutil.FakeBiliRepository
import com.lightningstudio.watchrss.testutil.FakeRssRepository
import com.lightningstudio.watchrss.testutil.TestAppContainer
import com.lightningstudio.watchrss.testutil.TestAppContainerRule
import com.lightningstudio.watchrss.testutil.currentResumedActivity
import com.lightningstudio.watchrss.testutil.createTestSettingsRepository
import com.lightningstudio.watchrss.testutil.sampleBiliBuiltinChannel
import com.lightningstudio.watchrss.testutil.sampleBiliItem
import com.lightningstudio.watchrss.testutil.sampleBiliSearchResponse
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BiliChannelInfoActivityTest {
    private val sampleItem = sampleBiliItem()
    private val fakeBiliRepository = FakeBiliRepository(
        initialLoggedIn = true,
        initialFeedItems = listOf(sampleItem),
        initialFeedCache = listOf(sampleItem),
        initialSearchHistory = listOf("测试关键词")
    ).apply {
        setSearchResult("测试关键词", 1, BiliResult(0, data = sampleBiliSearchResponse(sampleItem)))
    }

    private val containerRule = TestAppContainerRule { context ->
        val settingsRepository = createTestSettingsRepository(context, "bili-channel-info")
        runBlocking {
            settingsRepository.setOobeSeenVersion(CURRENT_OOBE_VERSION)
            settingsRepository.setShareUseSystem(false)
            settingsRepository.setReadingThemeDark(true)
            settingsRepository.setReadingFontSizeSp(14)
        }
        TestAppContainer(
            context = context,
            rssRepository = FakeRssRepository(initialChannels = listOf(sampleBiliBuiltinChannel())),
            settingsRepository = settingsRepository,
            biliRepositoryOverride = fakeBiliRepository
        )
    }

    private val composeRule = createAndroidComposeRule<BiliChannelInfoActivity>()

    @get:Rule
    val ruleChain: RuleChain = RuleChain.outerRule(containerRule).around(composeRule)

    @Test
    fun returningFromSearch_doesNotReopenSearchWhenOpeningSettings() {
        composeRule.onNodeWithText("搜索", useUnmergedTree = true)
            .assertExists()
            .performClick()

        waitForResumedActivity(BiliSearchActivity::class.java)

        SystemClock.sleep(700)

        composeRule.onNodeWithText("测试关键词", useUnmergedTree = true)
            .assertExists()
            .performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("测试 B 站视频", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }

        pressBack()
        waitForResumedActivity(BiliChannelInfoActivity::class.java)

        composeRule.onNodeWithText("设置", useUnmergedTree = true)
            .assertExists()
            .performClick()

        waitForResumedActivity(BiliSettingsActivity::class.java)
    }

    @Test
    fun logoutFromSettings_finishesChannelInfoActivity() {
        composeRule.onNodeWithText("设置", useUnmergedTree = true)
            .assertExists()
            .performClick()

        waitForResumedActivity(BiliSettingsActivity::class.java)

        composeRule.onNodeWithText("退出登录", useUnmergedTree = true)
            .assertExists()
            .performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            fakeBiliRepository.logoutCount == 1 &&
                (composeRule.activity.isFinishing || composeRule.activity.isDestroyed)
        }
    }

    private fun waitForResumedActivity(expectedClass: Class<out android.app.Activity>) {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            currentResumedActivity()?.javaClass == expectedClass
        }
    }
}
