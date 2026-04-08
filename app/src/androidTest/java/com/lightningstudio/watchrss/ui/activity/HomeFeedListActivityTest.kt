package com.lightningstudio.watchrss.ui.activity

import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lightningstudio.watchrss.AddRssActivity
import com.lightningstudio.watchrss.FeedActivity
import com.lightningstudio.watchrss.HomeFeedListActivity
import com.lightningstudio.watchrss.data.rss.RssChannel
import com.lightningstudio.watchrss.data.settings.CURRENT_OOBE_VERSION
import com.lightningstudio.watchrss.testutil.FakeRssRepository
import com.lightningstudio.watchrss.testutil.TestAppContainer
import com.lightningstudio.watchrss.testutil.TestAppContainerRule
import com.lightningstudio.watchrss.testutil.createTestSettingsRepository
import com.lightningstudio.watchrss.testutil.currentResumedActivity
import com.lightningstudio.watchrss.testutil.waitUntil
import com.lightningstudio.watchrss.ui.testing.HomeTestTags
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeFeedListActivityTest {
    private val sampleChannels = listOf(
        RssChannel(
            id = 42L,
            url = "https://example.com/feed.xml",
            title = "示例频道",
            description = "用于 UI 自动化回归",
            imageUrl = null,
            lastFetchedAt = 1_710_000_000_000L,
            sortOrder = 0,
            isPinned = false,
            useOriginalContent = false,
            unreadCount = 3
        )
    )

    private val containerRule = TestAppContainerRule { context ->
        val settingsRepository = createTestSettingsRepository(context, "home-feed-list-activity")
        runBlocking {
            settingsRepository.setOobeSeenVersion(CURRENT_OOBE_VERSION)
            settingsRepository.setPhoneConnectionEnabled(true)
        }
        TestAppContainer(
            context = context,
            rssRepository = FakeRssRepository(initialChannels = sampleChannels),
            settingsRepository = settingsRepository
        )
    }

    private val composeRule = createAndroidComposeRule<HomeFeedListActivity>()

    @get:Rule
    val ruleChain: RuleChain = RuleChain.outerRule(containerRule).around(composeRule)

    @Test
    fun homeFeedListActivity_rendersHomeAndNavigatesToAddRss() {
        composeRule.onNodeWithTag(HomeTestTags.ROOT).assertExists()
        composeRule.onNodeWithTag(HomeTestTags.channelCard(42L)).assertExists()
        composeRule.onNodeWithTag(HomeTestTags.CHANNEL_LIST)
            .performScrollToNode(hasTestTag(HomeTestTags.ADD_ENTRY))
        composeRule.onNodeWithTag(HomeTestTags.ADD_ENTRY).performClick()

        waitUntil(timeoutMillis = 5_000) {
            currentResumedActivity()?.javaClass == AddRssActivity::class.java
        }
    }

    @Test
    fun homeFeedListActivity_clickingChannelOpensFeedActivityWithSelectedChannel() {
        composeRule.onNodeWithTag(HomeTestTags.channelCard(42L)).assertExists().performClick()

        waitUntil(timeoutMillis = 5_000) {
            currentResumedActivity()?.javaClass == FeedActivity::class.java
        }

        val feedActivity = currentResumedActivity() as? FeedActivity
        assertEquals(42L, feedActivity?.intent?.getLongExtra(FeedActivity.EXTRA_CHANNEL_ID, -1L))
    }
}
