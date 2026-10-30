package com.lightningstudio.watchrss.ui.screen.rss

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class DetailHeaderActionsTouchTargetTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun circularReaderAction_has48dpTargetAndRemainsClickable() {
        var clickCount = 0
        composeRule.setContent {
            MaterialTheme {
                CircleIconButton(
                    icon = Icons.Default.Settings,
                    contentDescription = "阅读设置",
                    tint = Color.White,
                    containerColor = Color.Black,
                    borderColor = Color.Transparent,
                    size = 32.dp,
                    padding = 6.dp,
                    onClick = { clickCount++ }
                )
            }
        }

        composeRule.onNodeWithContentDescription("阅读设置")
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)
            .performClick()

        composeRule.runOnIdle { assertEquals(1, clickCount) }
    }
}
