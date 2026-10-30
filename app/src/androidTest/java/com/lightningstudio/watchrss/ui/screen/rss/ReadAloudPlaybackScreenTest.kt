package com.lightningstudio.watchrss.ui.screen.rss

import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lightningstudio.watchrss.data.tts.ReadAloudPhase
import com.lightningstudio.watchrss.data.tts.ReadAloudUiState
import com.lightningstudio.watchrss.testutil.setWatchContent
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReadAloudPlaybackScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun playbackActions_haveAtLeast48DpTouchTargets() {
        var openArticleCount = 0
        composeRule.setWatchContent {
            ReadAloudPlaybackScreen(
                state = ReadAloudUiState(
                    visible = true,
                    phase = ReadAloudPhase.READY,
                    currentTitle = "测试文章",
                    isPlaying = true
                ),
                onTogglePlayPause = {},
                onPrevious = {},
                onNext = {},
                onStop = {},
                onToggleAutoAdvance = {},
                onDecreaseSpeechRate = {},
                onIncreaseSpeechRate = {},
                onOpenCurrentArticle = { openArticleCount++ }
            )
        }

        listOf(
            "上一篇",
            "暂停朗读",
            "下一篇",
            "停止朗读",
            "自动下一篇已开启",
            "减慢朗读速度",
            "加快朗读速度",
            "查看当前文章"
        ).forEach { description ->
            composeRule.onNodeWithContentDescription(description)
                .assertWidthIsAtLeast(48.dp)
                .assertHeightIsAtLeast(48.dp)
        }

        composeRule.onNodeWithContentDescription("查看当前文章").performClick()
        composeRule.runOnIdle { assertEquals(1, openArticleCount) }
    }
}
