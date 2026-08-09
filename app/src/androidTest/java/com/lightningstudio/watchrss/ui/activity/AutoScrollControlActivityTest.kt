package com.lightningstudio.watchrss.ui.activity

import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lightningstudio.watchrss.AutoScrollControlActivity
import com.lightningstudio.watchrss.testutil.TestAppContainer
import com.lightningstudio.watchrss.testutil.TestAppContainerRule
import com.lightningstudio.watchrss.testutil.FakeRssRepository
import com.lightningstudio.watchrss.testutil.createTestSettingsRepository
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AutoScrollControlActivityTest {
    private val containerRule = TestAppContainerRule { context ->
        TestAppContainer(
            context = context,
            rssRepository = FakeRssRepository(),
            settingsRepository = createTestSettingsRepository(context, "auto-scroll-control-activity")
        )
    }
    private val composeRule = createEmptyComposeRule()

    @get:Rule
    val ruleChain: RuleChain = RuleChain.outerRule(containerRule).around(composeRule)

    @Test
    fun settingsEntry_rendersDetailedParametersAndReaderHint() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        ActivityScenario.launch<AutoScrollControlActivity>(
            AutoScrollControlActivity.createSettingsIntent(context)
        ).use {
            composeRule.onNodeWithText("自动滚动控制").assertExists()
            composeRule.onNodeWithText("打开文章自动开始").assertExists()
            composeRule.onNodeWithText("速度").performScrollTo().assertExists()
            composeRule.onNodeWithText("你也可以在阅读器中双击进入自动滚动控制")
                .performScrollTo()
                .assertExists()
        }
    }

    @Test
    fun readerEntry_togglesCurrentArticlePlayback() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        ActivityScenario.launch<AutoScrollControlActivity>(
            AutoScrollControlActivity.createReaderIntent(context, isPlaying = true)
        ).use {
            composeRule.onNodeWithText("正在自动滚动").assertExists()
            composeRule.onNodeWithContentDescription("暂停自动滚动").performClick()
            composeRule.onNodeWithText("自动滚动已暂停").assertExists()
            composeRule.onNodeWithContentDescription("启动自动滚动").assertExists()
        }
    }
}
