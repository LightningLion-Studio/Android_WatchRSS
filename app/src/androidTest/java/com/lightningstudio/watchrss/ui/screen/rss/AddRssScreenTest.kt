package com.lightningstudio.watchrss.ui.screen.rss

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lightningstudio.watchrss.data.rss.RssChannel
import com.lightningstudio.watchrss.data.rss.RssChannelPreview
import com.lightningstudio.watchrss.testutil.setWatchContent
import com.lightningstudio.watchrss.ui.testing.AddRssTestTags
import com.lightningstudio.watchrss.ui.viewmodel.AddRssStep
import com.lightningstudio.watchrss.ui.viewmodel.AddRssUiState
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AddRssScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun inputStep_acceptsUrlAndInvokesSubmit() {
        val state = MutableStateFlow(AddRssUiState())
        var submitClicks = 0

        composeRule.setWatchContent {
            AddRssScreen(
                uiState = state,
                showRemoteInputButton = true,
                onUrlChange = { state.value = state.value.copy(url = it) },
                onSubmit = { submitClicks++ },
                onConfirm = {},
                onBack = {},
                onBackToInput = {},
                onOpenExisting = {},
                onChannelAdded = { _, _ -> },
                onConsumed = {},
                onClearError = {},
                onRemoteInput = {}
            )
        }

        composeRule.onNodeWithTag(AddRssTestTags.URL_INPUT).performTextInput("https://example.com/feed.xml")
        composeRule.onNodeWithTag(AddRssTestTags.REMOTE_INPUT_BUTTON).assertExists()
        composeRule.onNodeWithTag(AddRssTestTags.SUBMIT_BUTTON).performClick()

        composeRule.runOnIdle {
            assertEquals("https://example.com/feed.xml", state.value.url)
            assertEquals(1, submitClicks)
        }
    }

    @Test
    fun previewStep_showsConfirmAndBackActions() {
        val state = MutableStateFlow(
            AddRssUiState(
                url = "https://example.com/feed.xml",
                step = AddRssStep.PREVIEW,
                preview = samplePreview()
            )
        )
        var confirmClicks = 0
        var backClicks = 0

        composeRule.setWatchContent {
            AddRssScreen(
                uiState = state,
                showRemoteInputButton = false,
                onUrlChange = {},
                onSubmit = {},
                onConfirm = { confirmClicks++ },
                onBack = {},
                onBackToInput = { backClicks++ },
                onOpenExisting = {},
                onChannelAdded = { _, _ -> },
                onConsumed = {},
                onClearError = {},
                onRemoteInput = {}
            )
        }

        composeRule.onNodeWithTag(AddRssTestTags.PREVIEW_PANEL).assertExists()
        composeRule.onNodeWithTag(AddRssTestTags.CONFIRM_BUTTON).performClick()
        composeRule.onNodeWithTag(AddRssTestTags.BACK_TO_INPUT_BUTTON).performClick()

        composeRule.runOnIdle {
            assertEquals(1, confirmClicks)
            assertEquals(1, backClicks)
        }
    }

    @Test
    fun existingStep_opensExistingChannel() {
        val state = MutableStateFlow(
            AddRssUiState(
                url = "https://example.com/feed.xml",
                step = AddRssStep.EXISTING,
                existingChannel = sampleChannel(7L)
            )
        )
        var openedChannelId = -1L

        composeRule.setWatchContent {
            AddRssScreen(
                uiState = state,
                showRemoteInputButton = false,
                onUrlChange = {},
                onSubmit = {},
                onConfirm = {},
                onBack = {},
                onBackToInput = {},
                onOpenExisting = { openedChannelId = it.id },
                onChannelAdded = { _, _ -> },
                onConsumed = {},
                onClearError = {},
                onRemoteInput = {}
            )
        }

        composeRule.onNodeWithTag(AddRssTestTags.EXISTING_PANEL).assertExists()
        composeRule.onNodeWithTag(AddRssTestTags.OPEN_EXISTING_BUTTON).performClick()

        composeRule.runOnIdle {
            assertEquals(7L, openedChannelId)
        }
    }

    @Test
    fun qrCodeStep_showsQrPanelAndBackAction() {
        val state = MutableStateFlow(
            AddRssUiState(
                step = AddRssStep.QR_CODE,
                serverAddress = "192.168.1.3:8899"
            )
        )
        var backClicks = 0

        composeRule.setWatchContent {
            AddRssScreen(
                uiState = state,
                showRemoteInputButton = false,
                onUrlChange = {},
                onSubmit = {},
                onConfirm = {},
                onBack = {},
                onBackToInput = { backClicks++ },
                onOpenExisting = {},
                onChannelAdded = { _, _ -> },
                onConsumed = {},
                onClearError = {},
                onRemoteInput = {}
            )
        }

        composeRule.onNodeWithTag(AddRssTestTags.QR_PANEL).assertExists()
        composeRule.onNodeWithTag(AddRssTestTags.QR_IMAGE).assertExists()
        composeRule.onNodeWithTag(AddRssTestTags.BACK_TO_INPUT_BUTTON).performScrollTo().performClick()

        composeRule.runOnIdle {
            assertEquals(1, backClicks)
        }
    }

    private fun samplePreview(): RssChannelPreview {
        return RssChannelPreview(
            url = "https://example.com/feed.xml",
            title = "测试预览",
            description = "测试简介",
            imageUrl = null,
            siteUrl = "https://example.com",
            items = emptyList(),
            isBuiltin = false
        )
    }

    private fun sampleChannel(id: Long): RssChannel {
        return RssChannel(
            id = id,
            url = "https://example.com/feed.xml",
            title = "已存在频道",
            description = "已存在",
            imageUrl = null,
            lastFetchedAt = null,
            sortOrder = 0,
            isPinned = false,
            useOriginalContent = false,
            unreadCount = 0
        )
    }
}
