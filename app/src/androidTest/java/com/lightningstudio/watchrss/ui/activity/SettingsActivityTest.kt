package com.lightningstudio.watchrss.ui.activity

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lightningstudio.watchrss.SettingsActivity
import com.lightningstudio.watchrss.data.settings.CURRENT_OOBE_VERSION
import com.lightningstudio.watchrss.testutil.FakeRssRepository
import com.lightningstudio.watchrss.testutil.TestAppContainer
import com.lightningstudio.watchrss.testutil.TestAppContainerRule
import com.lightningstudio.watchrss.testutil.createTestSettingsRepository
import com.lightningstudio.watchrss.ui.testing.SettingsTestTags
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsActivityTest {
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
        composeRule.onNodeWithTag(SettingsTestTags.THEME_SWITCH, useUnmergedTree = true).assertExists()
        composeRule.onNodeWithTag(SettingsTestTags.SHARE_SWITCH, useUnmergedTree = true).assertExists()
        composeRule.onNodeWithTag(SettingsTestTags.OPEN_OOBE_ENTRY, useUnmergedTree = true).performScrollTo().assertExists()
    }
}
