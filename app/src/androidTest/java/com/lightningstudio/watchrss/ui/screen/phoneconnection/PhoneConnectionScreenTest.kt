package com.lightningstudio.watchrss.ui.screen.phoneconnection

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
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
    fun landing_offersScanFirstAndFoldsPhoneAppDownloadAway() {
        var pairRequests = 0
        composeRule.setWatchContent {
            PhoneConnectionScreen(onPairMiniProgram = { pairRequests++ })
        }

        composeRule.onNodeWithTag(PhoneConnectionTestTags.ROOT).assertExists()
        composeRule.onAllNodesWithTag(PhoneConnectionTestTags.DOWNLOAD_ENTRY).assertCountEquals(0)
        composeRule.onAllNodesWithTag(PhoneConnectionTestTags.BLUETOOTH_ENTRY).assertCountEquals(0)
        assertEquals(0, pairRequests)

        composeRule.onNodeWithTag(PhoneConnectionTestTags.PHONE_APP_ENTRY).performClick()
        composeRule.onNodeWithTag(PhoneConnectionTestTags.PHONE_APP_STEPS).assertExists()
        composeRule.onNodeWithTag(PhoneConnectionTestTags.DOWNLOAD_ENTRY).assertExists()

        composeRule.onNodeWithTag(PhoneConnectionTestTags.MINI_PROGRAM_ENTRY).performClick()
        assertEquals(1, pairRequests)
    }
}
