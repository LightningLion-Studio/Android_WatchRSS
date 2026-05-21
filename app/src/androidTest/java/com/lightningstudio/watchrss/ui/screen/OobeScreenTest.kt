package com.lightningstudio.watchrss.ui.screen

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lightningstudio.watchrss.testutil.setWatchContent
import com.lightningstudio.watchrss.ui.testing.OobeTestTags
import com.lightningstudio.watchrss.ui.viewmodel.OobeUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OobeScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun firstPage_nextButtonAdvancesToAgreementPage() {
        var targetPage = -1

        composeRule.setWatchContent {
            OobeScreen(
                uiState = OobeUiState(introPage = 0),
                onSetIntroPage = { targetPage = it },
                onContinueFromIntro = {},
                onOpenUserAgreement = {},
                onOpenPrivacy = {}
            )
        }

        composeRule.onNodeWithTag(OobeTestTags.NEXT_BUTTON).assertExists().performClick()

        composeRule.runOnIdle {
            assertEquals(1, targetPage)
        }
    }

    @Test
    fun agreementPage_withoutAgreementShowsError() {
        composeRule.setWatchContent {
            OobeScreen(
                uiState = OobeUiState(introPage = 1),
                onSetIntroPage = {},
                onContinueFromIntro = {},
                onOpenUserAgreement = {},
                onOpenPrivacy = {}
            )
        }

        composeRule.onNodeWithTag(OobeTestTags.ERROR_TEXT).assertDoesNotExist()
        composeRule.onNodeWithTag(OobeTestTags.NEXT_BUTTON).performClick()
        composeRule.onNodeWithTag(OobeTestTags.ERROR_TEXT).assertExists()
    }

    @Test
    fun agreementPage_agreeThenContinueAdvancesToCustomPage() {
        var targetPage = -1

        composeRule.setWatchContent {
            OobeScreen(
                uiState = OobeUiState(introPage = 1),
                onSetIntroPage = { targetPage = it },
                onContinueFromIntro = {},
                onOpenUserAgreement = {},
                onOpenPrivacy = {}
            )
        }

        composeRule.onNodeWithTag(OobeTestTags.AGREEMENT_CHECKBOX).performClick()
        composeRule.onNodeWithTag(OobeTestTags.NEXT_BUTTON).performClick()

        composeRule.runOnIdle {
            assertEquals(2, targetPage)
        }
    }

    @Test
    fun customPage_showsStandardSettingsOverview() {
        composeRule.setWatchContent {
            OobeScreen(
                uiState = OobeUiState(introPage = 2),
                onSetIntroPage = {},
                onContinueFromIntro = {},
                onOpenUserAgreement = {},
                onOpenPrivacy = {}
            )
        }

        composeRule.onNodeWithTag(OobeTestTags.CUSTOM_PAGE).assertExists()
        composeRule.onNodeWithText("自定义").assertExists()
        composeRule.onNodeWithText("阅读主题").assertExists()
        composeRule.onNodeWithText("字体大小").assertExists()
        composeRule.onNodeWithText("使用滚轮调节音量").assertExists()
        composeRule.onNodeWithText("音量调节防干扰").assertExists()
        composeRule.onNodeWithText("静音开播").assertExists()
        composeRule.onNodeWithTag(OobeTestTags.CUSTOM_THEME_TOGGLE, useUnmergedTree = true).assertExists()
        composeRule.onNodeWithTag(OobeTestTags.CUSTOM_FONT_VALUE, useUnmergedTree = true).assertExists()
        composeRule.onNodeWithTag(OobeTestTags.CUSTOM_MEDIA_VOLUME_CONTROL_SWITCH, useUnmergedTree = true).assertExists()
        composeRule.onNodeWithTag(OobeTestTags.CUSTOM_MEDIA_GUARD_SWITCH, useUnmergedTree = true)
            .assertExists()
            .assertIsOff()
        composeRule.onNodeWithTag(OobeTestTags.CUSTOM_PLAYBACK_START_VOLUME_VALUE, useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("无限制").assertExists()
    }

    @Test
    fun customPage_hidesVolumeGuardWhenWheelVolumeIsOff() {
        composeRule.setWatchContent {
            OobeScreen(
                uiState = OobeUiState(introPage = 2),
                onSetIntroPage = {},
                onContinueFromIntro = {},
                onOpenUserAgreement = {},
                onOpenPrivacy = {}
            )
        }

        composeRule.onNodeWithTag(OobeTestTags.CUSTOM_MEDIA_VOLUME_CONTROL_SWITCH, useUnmergedTree = true)
            .performScrollTo()
            .performClick()

        composeRule.onNodeWithText("音量调节防干扰").assertDoesNotExist()
        composeRule.onNodeWithTag(OobeTestTags.CUSTOM_MEDIA_GUARD_SWITCH, useUnmergedTree = true)
            .assertDoesNotExist()
        composeRule.onNodeWithText("静音开播").assertExists()
    }
}
