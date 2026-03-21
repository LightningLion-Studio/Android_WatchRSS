package com.lightningstudio.watchrss.ui.screen.phoneconnection

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lightningstudio.watchrss.phoneconnection.PhoneConnectionMode
import com.lightningstudio.watchrss.testutil.setWatchContent
import com.lightningstudio.watchrss.ui.testing.PhoneConnectionTestTags
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PhoneConnectionScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun phoneConnectionScreen_rendersModes() {
        composeRule.setWatchContent {
            PhoneConnectionScreen(
                preferredAbilityLabel = "RSS订阅输入",
                onModeClick = {}
            )
        }

        composeRule.onNodeWithTag(PhoneConnectionTestTags.ROOT).assertExists()
        composeRule.onNodeWithTag(PhoneConnectionTestTags.PURE_SOUND_ENTRY).assertExists()
        composeRule.onNodeWithTag(PhoneConnectionTestTags.SOUND_GUIDED_WIFI_ENTRY).performScrollTo().assertExists()
        composeRule.onNodeWithTag(PhoneConnectionTestTags.MANUAL_WIFI_ENTRY).performScrollTo().assertExists()
    }

    @Test
    fun phoneConnectionScreen_modeClicksInvokeCallback() {
        val clickedModes = mutableListOf<PhoneConnectionMode>()

        composeRule.setWatchContent {
            PhoneConnectionScreen(
                preferredAbilityLabel = null,
                onModeClick = { clickedModes += it }
            )
        }

        composeRule.onNodeWithTag(PhoneConnectionTestTags.PURE_SOUND_ENTRY).performClick()
        composeRule.onNodeWithTag(PhoneConnectionTestTags.SOUND_GUIDED_WIFI_ENTRY).performScrollTo().performClick()
        composeRule.onNodeWithTag(PhoneConnectionTestTags.MANUAL_WIFI_ENTRY).performScrollTo().performClick()

        composeRule.runOnIdle {
            assertEquals(
                listOf(
                    PhoneConnectionMode.PURE_SOUND,
                    PhoneConnectionMode.SOUND_GUIDED_WIFI,
                    PhoneConnectionMode.MANUAL_WIFI
                ),
                clickedModes
            )
        }
    }
}
