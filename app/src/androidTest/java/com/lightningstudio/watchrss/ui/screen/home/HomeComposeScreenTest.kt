package com.lightningstudio.watchrss.ui.screen.home

import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lightningstudio.watchrss.data.rss.RssChannel
import com.lightningstudio.watchrss.testutil.setWatchContent
import com.lightningstudio.watchrss.ui.testing.HomeTestTags
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeComposeScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun emptyHome_rendersCoreEntries() {
        composeRule.setWatchContent {
            HomeComposeScreen(
                channels = emptyList(),
                openSwipeId = null,
                onOpenSwipe = {},
                onCloseSwipe = {},
                draggingSwipeId = null,
                onDragStart = {},
                onDragEnd = {},
                onProfileClick = {},
                onRecommendClick = {},
                onChannelClick = {},
                onChannelLongClick = {},
                onAddRssClick = {},
                onMoveTopClick = {},
                onMarkReadClick = {},
                onBeianClick = {}
            )
        }

        composeRule.onNodeWithTag(HomeTestTags.PROFILE_ENTRY).assertExists()
        composeRule.onNodeWithTag(HomeTestTags.EMPTY_ENTRY).assertExists()
        composeRule.onNodeWithTag(HomeTestTags.CHANNEL_LIST)
            .performScrollToNode(hasTestTag(HomeTestTags.RECOMMEND_ENTRY))
        composeRule.onNodeWithTag(HomeTestTags.RECOMMEND_ENTRY).assertExists()
        composeRule.onNodeWithTag(HomeTestTags.CHANNEL_LIST)
            .performScrollToNode(hasTestTag(HomeTestTags.ADD_ENTRY))
        composeRule.onNodeWithTag(HomeTestTags.ADD_ENTRY).assertExists()
        composeRule.onNodeWithTag(HomeTestTags.CHANNEL_LIST)
            .performScrollToNode(hasTestTag(HomeTestTags.BEIAN_ENTRY))
        composeRule.onNodeWithTag(HomeTestTags.BEIAN_ENTRY).assertExists()
    }

    @Test
    fun fixedEntries_clickCallbacksAreInvoked() {
        var profileClicks = 0
        var recommendClicks = 0
        var addClicks = 0
        var beianClicks = 0

        composeRule.setWatchContent {
            HomeComposeScreen(
                channels = emptyList(),
                openSwipeId = null,
                onOpenSwipe = {},
                onCloseSwipe = {},
                draggingSwipeId = null,
                onDragStart = {},
                onDragEnd = {},
                onProfileClick = { profileClicks++ },
                onRecommendClick = { recommendClicks++ },
                onChannelClick = {},
                onChannelLongClick = {},
                onAddRssClick = { addClicks++ },
                onMoveTopClick = {},
                onMarkReadClick = {},
                onBeianClick = { beianClicks++ }
            )
        }

        composeRule.onNodeWithTag(HomeTestTags.PROFILE_ENTRY).performClick()
        composeRule.onNodeWithTag(HomeTestTags.CHANNEL_LIST)
            .performScrollToNode(hasTestTag(HomeTestTags.RECOMMEND_ENTRY))
        composeRule.onNodeWithTag(HomeTestTags.RECOMMEND_ENTRY).performClick()
        composeRule.onNodeWithTag(HomeTestTags.CHANNEL_LIST)
            .performScrollToNode(hasTestTag(HomeTestTags.ADD_ENTRY))
        composeRule.onNodeWithTag(HomeTestTags.ADD_ENTRY).performClick()
        composeRule.onNodeWithTag(HomeTestTags.CHANNEL_LIST)
            .performScrollToNode(hasTestTag(HomeTestTags.BEIAN_ENTRY))
        composeRule.onNodeWithTag(HomeTestTags.BEIAN_ENTRY).performClick()

        composeRule.runOnIdle {
            assertEquals(1, profileClicks)
            assertEquals(1, recommendClicks)
            assertEquals(1, addClicks)
            assertEquals(1, beianClicks)
        }
    }

    @Test
    fun channelCard_clickInvokesChannelCallback() {
        var clickedChannelId = -1L

        composeRule.setWatchContent {
            HomeComposeScreen(
                channels = listOf(sampleChannel(id = 42L)),
                openSwipeId = null,
                onOpenSwipe = {},
                onCloseSwipe = {},
                draggingSwipeId = null,
                onDragStart = {},
                onDragEnd = {},
                onProfileClick = {},
                onRecommendClick = {},
                onChannelClick = { clickedChannelId = it.id },
                onChannelLongClick = {},
                onAddRssClick = {},
                onMoveTopClick = {},
                onMarkReadClick = {},
                onBeianClick = {}
            )
        }

        composeRule.onNodeWithTag(HomeTestTags.channelCard(42L)).assertExists().performClick()

        composeRule.runOnIdle {
            assertEquals(42L, clickedChannelId)
        }
    }

    private fun sampleChannel(id: Long): RssChannel {
        return RssChannel(
            id = id,
            url = "https://example.com/feed.xml",
            title = "示例频道",
            description = "示例简介",
            imageUrl = null,
            lastFetchedAt = 1_710_000_000_000,
            sortOrder = 0,
            isPinned = false,
            useOriginalContent = false,
            unreadCount = 3
        )
    }
}
