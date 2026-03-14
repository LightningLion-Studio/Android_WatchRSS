package com.lightningstudio.watchrss.ui.screen

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lightningstudio.watchrss.testutil.setWatchContent
import com.lightningstudio.watchrss.ui.testing.ProfileTestTags
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProfileScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun profileScreen_rendersAllEntries() {
        composeRule.setWatchContent {
            ProfileScreen(
                onFavoritesClick = {},
                onWatchLaterClick = {},
                onSettingsClick = {},
                onAboutClick = {},
                onContactDeveloperClick = {}
            )
        }

        composeRule.onNodeWithTag(ProfileTestTags.FAVORITES_ENTRY).assertExists()
        composeRule.onNodeWithTag(ProfileTestTags.WATCH_LATER_ENTRY).assertExists()
        composeRule.onNodeWithTag(ProfileTestTags.SETTINGS_ENTRY).assertExists()
        composeRule.onNodeWithTag(ProfileTestTags.ABOUT_ENTRY).performScrollTo().assertExists()
        composeRule.onNodeWithTag(ProfileTestTags.CONTACT_DEVELOPER_ENTRY).performScrollTo().assertExists()
    }

    @Test
    fun profileScreen_clickCallbacksAreInvoked() {
        var favoritesClicks = 0
        var watchLaterClicks = 0
        var settingsClicks = 0
        var aboutClicks = 0
        var contactClicks = 0

        composeRule.setWatchContent {
            ProfileScreen(
                onFavoritesClick = { favoritesClicks++ },
                onWatchLaterClick = { watchLaterClicks++ },
                onSettingsClick = { settingsClicks++ },
                onAboutClick = { aboutClicks++ },
                onContactDeveloperClick = { contactClicks++ }
            )
        }

        composeRule.onNodeWithTag(ProfileTestTags.FAVORITES_ENTRY).performClick()
        composeRule.onNodeWithTag(ProfileTestTags.WATCH_LATER_ENTRY).performClick()
        composeRule.onNodeWithTag(ProfileTestTags.SETTINGS_ENTRY).performClick()
        composeRule.onNodeWithTag(ProfileTestTags.ABOUT_ENTRY).performScrollTo().performClick()
        composeRule.onNodeWithTag(ProfileTestTags.CONTACT_DEVELOPER_ENTRY).performScrollTo().performClick()

        composeRule.runOnIdle {
            assertEquals(1, favoritesClicks)
            assertEquals(1, watchLaterClicks)
            assertEquals(1, settingsClicks)
            assertEquals(1, aboutClicks)
            assertEquals(1, contactClicks)
        }
    }
}
