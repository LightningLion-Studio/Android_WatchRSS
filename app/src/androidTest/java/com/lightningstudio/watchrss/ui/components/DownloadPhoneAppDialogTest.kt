package com.lightningstudio.watchrss.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.espresso.Espresso.pressBack
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lightningstudio.watchrss.testutil.setWatchContent
import com.lightningstudio.watchrss.ui.testing.DownloadPhoneAppTestTags
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DownloadPhoneAppDialogTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun dialogEscapesConstrainedParent_andBackDismissesIt() {
        var dismissals = 0

        composeRule.setWatchContent {
            Box(Modifier.size(72.dp)) {
                DownloadPhoneAppDialog(
                    operation = "同步收藏",
                    onDismiss = { dismissals++ }
                )
            }
        }

        composeRule.onNodeWithTag(DownloadPhoneAppTestTags.DIALOG)
            .assertIsDisplayed()
            .assertWidthIsAtLeast(200.dp)

        pressBack()

        composeRule.runOnIdle { assertEquals(1, dismissals) }
    }

    @Test
    fun scrimConsumesTouches_withoutClickingUnderlyingContentOrDismissing() {
        var underlyingClicks = 0
        var dismissals = 0

        composeRule.setWatchContent {
            Box(
                Modifier
                    .fillMaxSize()
                    .clickable { underlyingClicks++ }
                    .testTag("underlying_content")
            )
            DownloadPhoneAppDialog(
                operation = "同步收藏",
                onDismiss = { dismissals++ }
            )
        }

        composeRule.onNodeWithTag(DownloadPhoneAppTestTags.SCRIM)
            .performTouchInput { click(Offset(1f, 1f)) }

        composeRule.runOnIdle {
            assertEquals(0, underlyingClicks)
            assertEquals(0, dismissals)
        }
    }
}
