package com.lightningstudio.watchrss.ui.screen.bili

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.SemanticsMatcher
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lightningstudio.watchrss.testutil.setWatchContent
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BiliSettingsScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun dangerActionsAreButtonsAndInvokeTheirCallbacks() {
        var logoutCount = 0
        var deleteCount = 0
        composeRule.setWatchContent {
            BiliSettingsScreen(
                isLoggedIn = true,
                showOriginalContent = false,
                originalContentEnabled = false,
                onToggleOriginalContent = {},
                showContinuePlaybackInBackground = false,
                continuePlaybackInBackgroundEnabled = false,
                onToggleContinuePlaybackInBackground = {},
                deleteEnabled = true,
                onDelete = { deleteCount++ },
                onLogout = { logoutCount++ }
            )
        }

        composeRule.onNodeWithText("退出登录")
            .assertHasClickAction()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .assertIsEnabled()
            .performClick()
        composeRule.onNodeWithText("删除频道")
            .assertHasClickAction()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .assertIsEnabled()
            .performClick()

        composeRule.runOnIdle {
            assertEquals(1, logoutCount)
            assertEquals(1, deleteCount)
        }
    }
}
