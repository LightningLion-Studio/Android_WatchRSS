package com.lightningstudio.watchrss.ui.screen.phoneconnection

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lightningstudio.watchrss.testutil.setWatchContent
import com.lightningstudio.watchrss.ui.testing.PhoneConnectionTestTags
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PhoneConnectionScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun phoneConnectionScreen_rendersPhoneSideOperationPromptWithoutModes() {
        composeRule.setWatchContent {
            PhoneConnectionScreen()
        }

        composeRule.onNodeWithTag(PhoneConnectionTestTags.ROOT).assertExists()
        composeRule.onNodeWithText("请在与手表蓝牙配对了的手机上下载并打开腕上RSS手机端后操作").assertExists()
        composeRule.onAllNodesWithTag(PhoneConnectionTestTags.BLUETOOTH_ENTRY).assertCountEquals(0)
        composeRule.onAllNodesWithTag(PhoneConnectionTestTags.SOUND_GUIDED_WIFI_ENTRY).assertCountEquals(0)
        composeRule.onAllNodesWithTag(PhoneConnectionTestTags.PURE_SOUND_ENTRY).assertCountEquals(0)
        composeRule.onAllNodesWithTag(PhoneConnectionTestTags.MANUAL_WIFI_ENTRY).assertCountEquals(0)
    }
}
