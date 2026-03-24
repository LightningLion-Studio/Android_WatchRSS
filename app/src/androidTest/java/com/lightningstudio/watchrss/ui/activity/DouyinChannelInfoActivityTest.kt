package com.lightningstudio.watchrss.ui.activity

import android.app.Activity
import android.os.SystemClock
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.lightningstudio.watchrss.DouyinChannelInfoActivity
import com.lightningstudio.watchrss.DouyinSettingsActivity
import com.lightningstudio.watchrss.MainActivity
import com.lightningstudio.watchrss.data.douyin.DouyinResult
import com.lightningstudio.watchrss.data.settings.CURRENT_OOBE_VERSION
import com.lightningstudio.watchrss.testutil.FakeDouyinRepository
import com.lightningstudio.watchrss.testutil.FakeRssRepository
import com.lightningstudio.watchrss.testutil.TestAppContainer
import com.lightningstudio.watchrss.testutil.TestAppContainerRule
import com.lightningstudio.watchrss.testutil.createTestSettingsRepository
import com.lightningstudio.watchrss.testutil.sampleDouyinBuiltinChannel
import com.lightningstudio.watchrss.testutil.sampleDouyinFeedPage
import com.lightningstudio.watchrss.testutil.sampleDouyinVideo
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DouyinChannelInfoActivityTest {
    private val sampleVideo = sampleDouyinVideo()
    private val fakeDouyinRepository = FakeDouyinRepository(
        initialLoggedIn = true,
        initialFeedPage = sampleDouyinFeedPage(listOf(sampleVideo))
    ).apply {
        setFeedPage(null, DouyinResult(0, data = sampleDouyinFeedPage(listOf(sampleVideo))))
    }

    private val containerRule = TestAppContainerRule { context ->
        val settingsRepository = createTestSettingsRepository(context, "douyin-channel-info")
        runBlocking {
            settingsRepository.setOobeSeenVersion(CURRENT_OOBE_VERSION)
            settingsRepository.setShareUseSystem(false)
            settingsRepository.setReadingThemeDark(true)
            settingsRepository.setReadingFontSizeSp(14)
        }
        TestAppContainer(
            context = context,
            rssRepository = FakeRssRepository(initialChannels = listOf(sampleDouyinBuiltinChannel())),
            settingsRepository = settingsRepository,
            douyinRepositoryOverride = fakeDouyinRepository
        )
    }

    private val composeRule = createAndroidComposeRule<DouyinChannelInfoActivity>()

    @get:Rule
    val ruleChain: RuleChain = RuleChain.outerRule(containerRule).around(composeRule)

    @Test
    fun logoutFromSettings_returnsToMainActivity() {
        composeRule.onNodeWithText("设置", useUnmergedTree = true)
            .assertExists()
            .performClick()

        waitForResumedActivity(DouyinSettingsActivity::class.java)

        SystemClock.sleep(700)

        composeRule.onNodeWithText("退出登录", useUnmergedTree = true)
            .assertExists()
            .performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            fakeDouyinRepository.logoutCount == 1 &&
                currentResumedActivity()?.javaClass == MainActivity::class.java
        }
    }

    private fun waitForResumedActivity(expectedClass: Class<out Activity>) {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            currentResumedActivity()?.javaClass == expectedClass
        }
    }

    private fun currentResumedActivity(): Activity? {
        var resumedActivity: Activity? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            resumedActivity = ActivityLifecycleMonitorRegistry.getInstance()
                .getActivitiesInStage(Stage.RESUMED)
                .firstOrNull()
        }
        return resumedActivity
    }
}
