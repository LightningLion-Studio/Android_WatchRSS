package com.lightningstudio.watchrss.ui.screen.rss

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lightningstudio.watchrss.testutil.setWatchContent
import com.lightningstudio.watchrss.ui.viewmodel.LlmSummaryUiState
import com.lightningstudio.watchrss.ui.viewmodel.SummaryStatus
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LlmSummaryScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun errorStateShowsMessageAndRetryRemainsAccessible() {
        var retryCount = 0
        composeRule.setWatchContent {
            LlmSummaryContent(
                state = LlmSummaryUiState(status = SummaryStatus.Error("网络暂时不可用")),
                showTokenUsage = false,
                onRetry = { retryCount++ }
            )
        }

        composeRule.onNodeWithText("生成失败").assertExists()
        composeRule.onNodeWithText("网络暂时不可用").assertExists()
        composeRule.onNodeWithContentDescription("重试生成总结按钮")
            .assertHasClickAction()
            .performClick()

        composeRule.runOnIdle { assertEquals(1, retryCount) }
    }
}
