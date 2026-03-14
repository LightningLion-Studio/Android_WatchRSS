package com.lightningstudio.watchrss.testutil

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import com.lightningstudio.watchrss.ui.theme.WatchRSSTheme

fun ComposeContentTestRule.setWatchContent(content: @Composable () -> Unit) {
    setContent {
        WatchRSSTheme {
            content()
        }
    }
}
