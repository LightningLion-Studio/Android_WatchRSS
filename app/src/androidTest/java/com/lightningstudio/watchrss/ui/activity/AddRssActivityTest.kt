package com.lightningstudio.watchrss.ui.activity

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lightningstudio.watchrss.AddRssActivity
import com.lightningstudio.watchrss.data.settings.CURRENT_OOBE_VERSION
import com.lightningstudio.watchrss.testutil.FakeRssRepository
import com.lightningstudio.watchrss.testutil.TestAppContainer
import com.lightningstudio.watchrss.testutil.TestAppContainerRule
import com.lightningstudio.watchrss.testutil.createTestSettingsRepository
import com.lightningstudio.watchrss.ui.testing.AddRssTestTags
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AddRssActivityTest {
    private val containerRule = TestAppContainerRule { context ->
        val settingsRepository = createTestSettingsRepository(context, "add-rss-activity")
        runBlocking {
            settingsRepository.setOobeSeenVersion(CURRENT_OOBE_VERSION)
            settingsRepository.setPhoneConnectionEnabled(true)
        }
        TestAppContainer(
            context = context,
            rssRepository = FakeRssRepository(),
            settingsRepository = settingsRepository
        )
    }

    private val composeRule = createAndroidComposeRule<AddRssActivity>()

    @get:Rule
    val ruleChain: RuleChain = RuleChain.outerRule(containerRule).around(composeRule)

    @Test
    fun addRssActivity_rendersInputAndRemoteEntry() {
        composeRule.onNodeWithTag(AddRssTestTags.ROOT).assertExists()
        composeRule.onNodeWithTag(AddRssTestTags.URL_INPUT).assertExists()
        composeRule.onNodeWithTag(AddRssTestTags.REMOTE_INPUT_BUTTON).assertExists()
    }
}
