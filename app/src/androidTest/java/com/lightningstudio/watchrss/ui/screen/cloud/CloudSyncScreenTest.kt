package com.lightningstudio.watchrss.ui.screen.cloud

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lightningstudio.watchrss.testutil.setWatchContent
import com.lightningstudio.watchrss.ui.testing.CloudSyncTestTags
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CloudSyncScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun syncingActionIgnoresRepeatedTaps() {
        val actions = mutableListOf<CloudSyncAction>()
        composeRule.setWatchContent {
            CloudSyncScreen(
                state = CloudSyncUiState(
                    title = "正在同步",
                    detail = "正在连接云空间",
                    tone = CloudSyncTone.BUSY,
                    button = CloudSyncButton("同步中…", CloudSyncAction.SYNC, enabled = false)
                ),
                onAction = { actions += it }
            )
        }

        composeRule.onNodeWithTag(CloudSyncTestTags.ACTION).assertIsNotEnabled().performClick().performClick()
        composeRule.runOnIdle { assertEquals(emptyList<CloudSyncAction>(), actions) }
    }

    @Test
    fun primaryActionReportsItsKind() {
        val actions = mutableListOf<CloudSyncAction>()
        composeRule.setWatchContent {
            CloudSyncScreen(
                state = CloudSyncUiState(
                    title = "未登录账号",
                    detail = "",
                    tone = CloudSyncTone.MUTED,
                    button = CloudSyncButton("连接手机", CloudSyncAction.OPEN_PHONE_CONNECTION)
                ),
                onAction = { actions += it }
            )
        }

        composeRule.onNodeWithTag(CloudSyncTestTags.ACTION).performClick()
        composeRule.runOnIdle { assertEquals(listOf(CloudSyncAction.OPEN_PHONE_CONNECTION), actions) }
    }
}
