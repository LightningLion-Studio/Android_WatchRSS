package com.lightningstudio.watchrss.ui.activity

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lightningstudio.watchrss.ProfileActivity
import com.lightningstudio.watchrss.ui.testing.ProfileTestTags
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProfileActivityTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ProfileActivity>()

    @Test
    fun profileActivity_rendersKeyEntries() {
        composeRule.onNodeWithTag(ProfileTestTags.ROOT).assertExists()
        composeRule.onNodeWithTag(ProfileTestTags.FAVORITES_ENTRY).assertExists()
        composeRule.onNodeWithTag(ProfileTestTags.SETTINGS_ENTRY).assertExists()
    }
}
