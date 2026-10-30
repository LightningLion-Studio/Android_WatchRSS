package com.lightningstudio.watchrss.ui.screen.rss

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SavedItemsScreenStyleTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun undoAction_has48dpTargetAndRemainsClickable() {
        var undoCount = 0
        composeRule.setContent {
            MaterialTheme {
                SavedItemsScreen(
                    title = "收藏",
                    hint = "已保存文章",
                    emptyMessage = "暂无收藏",
                    hasLoadedItems = true,
                    items = emptyList(),
                    undoVisible = true,
                    onUndoClick = { undoCount++ },
                    onItemClick = {},
                    onItemRemove = {},
                    onItemsReordered = {},
                    syncOperationLabel = "同步收藏"
                )
            }
        }

        composeRule.onNodeWithContentDescription("撤回按钮")
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)
            .performClick()

        composeRule.runOnIdle { assertEquals(1, undoCount) }
    }
}
