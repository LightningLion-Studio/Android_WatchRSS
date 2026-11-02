package com.lightningstudio.watchrss.ui.screen.rss

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lightningstudio.watchrss.data.db.LlmTokenUsageByProviderPojo
import com.lightningstudio.watchrss.data.db.LlmTokenUsageEntity
import com.lightningstudio.watchrss.testutil.setWatchContent
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LlmTokenUsageScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun providerSummaryCardIsNotPresentedAsClickable() {
        composeRule.setWatchContent {
            ProviderSummaryChip(
                LlmTokenUsageByProviderPojo(
                    provider = "测试供应商",
                    model = "测试模型",
                    calls = 2,
                    totalTokens = 30
                )
            )
        }

        composeRule.onNodeWithText("测试供应商").assertHasNoClickAction()
    }

    @Test
    fun historyRecordCardIsNotPresentedAsClickable() {
        composeRule.setWatchContent {
            RecordChip(
                LlmTokenUsageEntity(
                    provider = "测试供应商",
                    model = "测试模型",
                    requestId = "request-1",
                    promptTokens = 10,
                    completionTokens = 20,
                    totalTokens = 30,
                    reasoningTokens = null,
                    cachedPromptTokens = null,
                    inputTokens = null,
                    outputTokens = null,
                    promptTokenCount = null,
                    candidatesTokenCount = null,
                    totalTokenCount = null
                )
            )
        }

        composeRule.onNodeWithText("测试供应商 · 测试模型").assertHasNoClickAction()
    }

    @Test
    fun clearActionUsesButtonSemanticsAndMinimumTouchHeight() {
        var clicks = 0
        composeRule.setWatchContent {
            ActionChip(
                icon = Icons.Outlined.Delete,
                label = "清空记录",
                onClick = { clicks++ }
            )
        }

        composeRule.onNodeWithText("清空记录")
            .assertHeightIsAtLeast(48.dp)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .performClick()
        composeRule.runOnIdle { assertEquals(1, clicks) }
    }
}
