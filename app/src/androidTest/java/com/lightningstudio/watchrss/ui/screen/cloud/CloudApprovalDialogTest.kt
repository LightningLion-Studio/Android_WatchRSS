package com.lightningstudio.watchrss.ui.screen.cloud

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lightningstudio.watchrss.testutil.setWatchContent
import com.lightningstudio.watchrss.ui.testing.CloudSyncTestTags
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CloudApprovalDialogTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun eightDigitCodeEnablesConfirmationOnRoundScreen() {
        var confirmations = 0
        composeRule.setWatchContent {
            var code by remember { mutableStateOf("") }
            CloudApprovalDialog(false, true, false, false, code, "请输入旧设备显示的8位配对码。",
                { code = it }, { confirmations++ }, {}, {})
        }
        composeRule.onNodeWithTag(CloudSyncTestTags.ACTION).assertIsNotEnabled()
        composeRule.onNodeWithTag("cloud-approval-code").performScrollTo().performTextInput("00123456")
        composeRule.onNodeWithTag(CloudSyncTestTags.ACTION).performScrollTo().performClick()
        composeRule.runOnIdle { assertEquals(1, confirmations) }
    }
}
