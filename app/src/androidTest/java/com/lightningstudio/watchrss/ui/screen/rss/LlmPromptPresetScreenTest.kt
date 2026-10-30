package com.lightningstudio.watchrss.ui.screen.rss

import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lightningstudio.watchrss.testutil.setWatchContent
import com.lightningstudio.watchrss.ui.viewmodel.LlmPromptPresets
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LlmPromptPresetScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun presetsExposeSingleSelectionAndInvokeSelectedPreset() {
        val current = LlmPromptPresets.all.first()
        val target = LlmPromptPresets.all[1]
        val selected = mutableListOf<Int>()

        composeRule.setWatchContent {
            LlmPromptPresetScreen(
                currentPreset = current.index,
                onSelectPreset = selected::add
            )
        }

        composeRule.onNodeWithText(current.label)
            .assertIsSelected()
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)
        composeRule.onNodeWithText(target.label)
            .assertHeightIsAtLeast(48.dp)
            .performClick()

        composeRule.runOnIdle { assertEquals(listOf(target.index), selected) }
    }
}
