package com.lightningstudio.watchrss.ui.screen.common

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lightningstudio.watchrss.testutil.setWatchContent
import com.lightningstudio.watchrss.ui.screen.AboutScreen
import com.lightningstudio.watchrss.ui.screen.ActionDialogScreen
import com.lightningstudio.watchrss.ui.screen.ActionItem
import com.lightningstudio.watchrss.ui.screen.BeianScreen
import com.lightningstudio.watchrss.ui.screen.CollaboratorsScreen
import com.lightningstudio.watchrss.ui.screen.ContactDeveloperScreen
import com.lightningstudio.watchrss.ui.screen.JoinGroupScreen
import com.lightningstudio.watchrss.ui.screen.LogUploadPrivacyScreen
import com.lightningstudio.watchrss.ui.screen.ProjectInfoScreen
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StaticCommonScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun aboutScreen_rendersEntries_and_invokesCallbacks() {
        val clicks = mutableListOf<String>()

        composeRule.setWatchContent {
            AboutScreen(
                onIntroClick = { clicks += "intro" },
                onPrivacyClick = { clicks += "privacy" },
                onTermsClick = { clicks += "terms" },
                onLicensesClick = { clicks += "licenses" },
                onCollaboratorsClick = { clicks += "collaborators" }
            )
        }

        composeRule.onNodeWithText("关于").assertExists()
        composeRule.onNodeWithText("项目自介").performClick()
        composeRule.onNodeWithText("隐私政策").performClick()

        composeRule.runOnIdle {
            assertEquals(listOf("intro", "privacy"), clicks)
        }
    }

    @Test
    fun contactDeveloperScreen_rendersActions_and_invokesCallbacks() {
        val clicks = mutableListOf<String>()

        composeRule.setWatchContent {
            ContactDeveloperScreen(
                onJoinGroupClick = { clicks += "join" },
                onUploadLogClick = { clicks += "upload" }
            )
        }

        composeRule.onNodeWithText("联系开发者").assertExists()
        composeRule.onNodeWithText("加群").performClick()
        composeRule.onNodeWithText("上传日志").performClick()

        composeRule.runOnIdle {
            assertEquals(listOf("join", "upload"), clicks)
        }
    }

    @Test
    fun collaboratorsScreen_rendersCoreContent() {
        composeRule.setWatchContent {
            CollaboratorsScreen(circleMaskEnabled = false)
        }

        composeRule.onNodeWithText("协作者名单").assertExists()
        composeRule.onNodeWithText("闪电狮").assertExists()
        composeRule.onNodeWithText("Nicolas").assertExists()
    }

    @Test
    fun projectInfoScreen_rendersProjectQr() {
        composeRule.setWatchContent {
            ProjectInfoScreen()
        }

        composeRule.onNodeWithText("LightningLion-Studio / WatchRSS").assertExists()
        composeRule.onNodeWithContentDescription("GitHub QR Code").assertExists()
    }

    @Test
    fun beianScreen_rendersBeianQr() {
        composeRule.setWatchContent {
            BeianScreen()
        }

        composeRule.onNodeWithText("手机扫码查看").assertExists()
        composeRule.onNodeWithContentDescription("备案查询二维码").assertExists()
    }

    @Test
    fun joinGroupScreen_rendersGroupInfo() {
        composeRule.setWatchContent {
            JoinGroupScreen(
                qrCodeUrl = "https://qm.qq.com/q/cJNTQuxfoW",
                groupNumber = "1083518433"
            )
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithText("加群").assertExists()
        composeRule.onNodeWithText("QQ群号 1083518433").assertExists()
    }

    @Test
    fun logUploadPrivacyScreen_rendersUploadEntry() {
        composeRule.setWatchContent {
            LogUploadPrivacyScreen(onStartUploadClick = {})
        }

        composeRule.onNodeWithText("隐私及防诈说明").assertExists()
        composeRule.onNodeWithText("开始上传").assertExists()
    }

    @Test
    fun actionDialogScreen_rendersItems_and_clicksEnabledAction() {
        var confirmClicks = 0

        composeRule.setWatchContent {
            ActionDialogScreen(
                items = listOf(
                    ActionItem(label = "确认", onClick = { confirmClicks += 1 }),
                    ActionItem(label = "删除", enabled = false, onClick = { confirmClicks += 10 }),
                    ActionItem(label = "取消", onClick = {})
                )
            )
        }

        composeRule.onNodeWithText("确认").performClick()
        composeRule.onNodeWithText("删除").assertExists()
        composeRule.onNodeWithText("取消").assertExists()

        composeRule.runOnIdle {
            assertEquals(1, confirmClicks)
        }
    }
}
