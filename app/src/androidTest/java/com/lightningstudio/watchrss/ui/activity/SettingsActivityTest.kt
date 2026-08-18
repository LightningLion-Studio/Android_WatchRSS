package com.lightningstudio.watchrss.ui.activity

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lightningstudio.watchrss.AdvancedSettingsActivity
import com.lightningstudio.watchrss.SettingsActivity
import com.lightningstudio.watchrss.data.settings.CURRENT_OOBE_VERSION
import com.lightningstudio.watchrss.testutil.FakeRssRepository
import com.lightningstudio.watchrss.testutil.TestAppContainer
import com.lightningstudio.watchrss.testutil.TestAppContainerRule
import com.lightningstudio.watchrss.testutil.createTestSettingsRepository
import com.lightningstudio.watchrss.testutil.currentResumedActivity
import com.lightningstudio.watchrss.ui.testing.SettingsTestTags
import com.lightningstudio.watchrss.ui.util.isSystemShareSettingSupported
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsActivityTest {
    private val showSystemShareSetting = isSystemShareSettingSupported(
        ApplicationProvider.getApplicationContext()
    )

    private val containerRule = TestAppContainerRule { context ->
        val settingsRepository = createTestSettingsRepository(context, "settings-activity")
        runBlocking {
            settingsRepository.setOobeSeenVersion(CURRENT_OOBE_VERSION)
            settingsRepository.setCacheLimitBytes(1024L * 1024L * 1024L)
            settingsRepository.setReadingThemeDark(true)
            settingsRepository.setReadingFontSizeSp(14)
            settingsRepository.setShareUseSystem(false)
            settingsRepository.setPhoneConnectionEnabled(true)
        }
        TestAppContainer(
            context = context,
            rssRepository = FakeRssRepository(initialCacheUsageBytes = 256L * 1024L * 1024L),
            settingsRepository = settingsRepository
        )
    }

    private val composeRule = createAndroidComposeRule<SettingsActivity>()

    @get:Rule
    val ruleChain: RuleChain = RuleChain.outerRule(containerRule).around(composeRule)

    @Test
    fun settingsActivity_rendersInjectedState() {
        composeRule.onNodeWithTag(SettingsTestTags.ROOT).assertExists()
        composeRule.onAllNodesWithText("阅读主题").assertCountEquals(0)
        composeRule.onNodeWithTag(SettingsTestTags.MEDIA_VOLUME_CONTROL_SWITCH, useUnmergedTree = true).performScrollTo().assertExists()
        composeRule.onNodeWithTag(SettingsTestTags.MEDIA_VOLUME_GUARD_SWITCH, useUnmergedTree = true).performScrollTo().assertExists()
        composeRule.onNodeWithText("自动滚动").performScrollTo().assertExists()
        composeRule.onNodeWithTag(SettingsTestTags.ADVANCED_ENTRY, useUnmergedTree = true).assertExists()
        composeRule.onNodeWithTag(SettingsTestTags.OPEN_OOBE_ENTRY, useUnmergedTree = true).performScrollTo().assertExists()
        composeRule.onNodeWithTag(SettingsTestTags.ADVANCED_ENTRY, useUnmergedTree = true).performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            currentResumedActivity()?.javaClass == AdvancedSettingsActivity::class.java
        }
        if (showSystemShareSetting) {
            composeRule.onNodeWithTag(SettingsTestTags.SHARE_SWITCH, useUnmergedTree = true).assertExists()
        }
        composeRule.onNodeWithTag(SettingsTestTags.CACHE_VALUE, useUnmergedTree = true).assertExists()
    }
}
