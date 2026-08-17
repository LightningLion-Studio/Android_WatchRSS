package com.lightningstudio.watchrss.ui.components

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lightningstudio.watchrss.testutil.setWatchContent
import com.lightningstudio.watchrss.ui.testing.DownloadPhoneAppTestTags
import com.lightningstudio.watchrss.ui.testing.PhoneSyncActionsTestTags
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PhoneSyncActionsTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun startSyncInvokesCallbackWithoutOpeningDownloadDialog() {
        var syncRequests = 0
        composeRule.setWatchContent {
            PhoneSyncActions(
                operation = "同步收藏",
                onStartSync = { syncRequests += 1 }
            )
        }

        composeRule.onNodeWithTag(PhoneSyncActionsTestTags.START_SYNC_BUTTON).performClick()

        composeRule.runOnIdle { assertEquals(1, syncRequests) }
        composeRule.onNodeWithTag(DownloadPhoneAppTestTags.DIALOG).assertDoesNotExist()
    }

    @Test
    fun downloadLinkOpensDialogWithoutStartingSync() {
        var syncRequests = 0
        composeRule.setWatchContent {
            PhoneSyncActions(
                operation = "同步B站观看记录",
                onStartSync = { syncRequests += 1 }
            )
        }

        composeRule.onNodeWithText(phoneAppDownloadPrompt("同步B站观看记录")).performClick()

        composeRule.runOnIdle { assertEquals(0, syncRequests) }
        composeRule.onNodeWithTag(DownloadPhoneAppTestTags.DIALOG).assertExists()
    }
}
