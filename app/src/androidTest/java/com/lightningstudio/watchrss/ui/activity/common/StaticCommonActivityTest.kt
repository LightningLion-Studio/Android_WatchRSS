package com.lightningstudio.watchrss.ui.activity.common

import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lightningstudio.watchrss.AboutActivity
import com.lightningstudio.watchrss.BeianActivity
import com.lightningstudio.watchrss.CollaboratorsActivity
import com.lightningstudio.watchrss.ContactDeveloperActivity
import com.lightningstudio.watchrss.JoinGroupActivity
import com.lightningstudio.watchrss.LogUploadPrivacyActivity
import com.lightningstudio.watchrss.ProjectInfoActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StaticCommonActivityTest {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    @Test
    fun aboutActivity_rendersTitle() {
        launchAndAssert(AboutActivity::class.java) {
            composeRule.waitForIdle()
            composeRule.onNodeWithText("关于").assertExists()
            composeRule.onNodeWithText("项目自介").assertExists()
        }
    }

    @Test
    fun contactDeveloperActivity_rendersActions() {
        launchAndAssert(ContactDeveloperActivity::class.java) {
            composeRule.waitForIdle()
            composeRule.onNodeWithText("联系开发者").assertExists()
            composeRule.onNodeWithText("上传日志").assertExists()
        }
    }

    @Test
    fun collaboratorsActivity_rendersCollaborators() {
        launchAndAssert(CollaboratorsActivity::class.java) {
            composeRule.waitForIdle()
            composeRule.onNodeWithText("协作者名单").assertExists()
            composeRule.onNodeWithText("Nicolas").assertExists()
        }
    }

    @Test
    fun projectInfoActivity_rendersProjectQr() {
        launchAndAssert(ProjectInfoActivity::class.java) {
            composeRule.waitForIdle()
            composeRule.onNodeWithText("LightningLion-Studio / WatchRSS").assertExists()
            composeRule.onNodeWithContentDescription("GitHub QR Code").assertExists()
        }
    }

    @Test
    fun beianActivity_rendersBeianQr() {
        launchAndAssert(BeianActivity.createIntent(ApplicationProvider.getApplicationContext())) {
            composeRule.waitForIdle()
            composeRule.onNodeWithText("手机扫码查看").assertExists()
            composeRule.onNodeWithContentDescription("备案查询二维码").assertExists()
        }
    }

    @Test
    fun joinGroupActivity_rendersGroupInfo() {
        launchAndAssert(JoinGroupActivity::class.java) {
            composeRule.waitForIdle()
            composeRule.onNodeWithText("加群").assertExists()
            composeRule.onNodeWithText("QQ群号 1083518433").assertExists()
        }
    }

    @Test
    fun logUploadPrivacyActivity_rendersUploadEntry() {
        launchAndAssert(LogUploadPrivacyActivity::class.java) {
            composeRule.waitForIdle()
            composeRule.onNodeWithText("隐私及防诈说明").assertExists()
            composeRule.onNodeWithText("开始上传").assertExists()
        }
    }

    private fun <A : android.app.Activity> launchAndAssert(
        activityClass: Class<A>,
        assertions: () -> Unit
    ) {
        ActivityScenario.launch(activityClass).use {
            assertions()
        }
    }

    private fun launchAndAssert(intent: android.content.Intent, assertions: () -> Unit) {
        ActivityScenario.launch<android.app.Activity>(intent).use {
            assertions()
        }
    }
}
