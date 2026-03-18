package com.lightningstudio.watchrss.ui.util

import org.junit.Assert.assertEquals
import org.junit.Test

class WatchTitleFormattingTest {

    @Test
    fun normalizeWatchTitleWhitespaceRemovesBrokenSpacesInCjkTitles() {
        val raw = "王兴兴放狠话： GJ 全世界都在学 20年 后依然经典 首曝春晚机器人背后门道"

        assertEquals(
            "王兴兴放狠话：GJ全世界都在学20年后依然经典首曝春晚机器人背后门道",
            normalizeWatchTitleWhitespace(raw)
        )
    }

    @Test
    fun normalizeWatchTitleWhitespaceKeepsSpacesForNonCjkTitles() {
        val raw = "OpenAI releases GPT 5.4 today"

        assertEquals(raw, normalizeWatchTitleWhitespace(raw))
    }

    @Test
    fun formatWatchTitleForWidthLimitsKeepsTwoContentCharsBeforeTrailingHint() {
        val formatted = formatWatchTitleForWidthLimitsWithMeasurer(
            title = "中新网即时新闻ⓘ",
            availableWidthPx = 6f,
            firstLimitPx = 6f,
            secondLimitPx = 3f,
            protectedSuffix = "ⓘ",
            minPrefixCharsBeforeSuffixOnLastLine = 2,
            measureText = { it.length.toFloat() }
        )

        assertEquals("中新网即时\n新闻ⓘ", formatted)
    }
}
