package com.lightningstudio.watchrss.ui.components

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.lightningstudio.watchrss.ui.theme.WatchRSSTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class PlatformDisclosureButtonTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun initialDisclosure_invokesOnlyAcknowledgementCallback() {
        var acknowledgements = 0
        compose.setContent { WatchRSSTheme { InitialAppTransparencyDialog { acknowledgements++ } } }
        compose.onNodeWithText("我已了解").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(1, acknowledgements) }
    }

    @Test fun platformDisclosure_invokesOnlyConfirmationCallback() {
        var confirmations = 0
        compose.setContent { WatchRSSTheme { ThirdPartyPlatformConfirmationDialog("测试平台") { confirmations++ } } }
        compose.onNodeWithText("知道了，继续").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(1, confirmations) }
    }
}
