package com.lightningstudio.watchrss.ui.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

    @Test
    fun formatWatchScreenTitleBreaksLinesOnlyOnRoundScreens() {
        val title = "腕上RSS · 哔哩哔哩频道"
        val measure: (String) -> Float = { it.length.toFloat() }

        val round = formatWatchScreenTitle(
            title = title,
            isRound = true,
            availableWidthPx = 20f,
            firstLimitPx = 6f,
            secondLimitPx = 8f,
            protectedSuffix = "ⓘ",
            measureText = measure
        )
        val square = formatWatchScreenTitle(
            title = title,
            isRound = false,
            availableWidthPx = 20f,
            firstLimitPx = 20f,
            secondLimitPx = 20f,
            protectedSuffix = "ⓘ",
            measureText = measure
        )

        round.lines().forEachIndexed { index, line ->
            assertTrue(line.length <= if (index == 0) 6 else 8)
        }
        assertTrue(round.lines().size > 1)
        assertTrue(round.endsWith("ⓘ"))
        assertEquals("$title\u00A0ⓘ", square)
    }

    @Test
    fun roundScreenLineWidthUsesChordAtFartherLineEdge() {
        // 466 px round screen; a line box from y = 36 to 88 near the top edge.
        val top = roundScreenLineWidthPx(36f, 52f, 233f, 233f, 233f, 233f, marginPx = 4f)
        val middle = roundScreenLineWidthPx(207f, 52f, 233f, 233f, 233f, 233f, marginPx = 0f)
        val offCentre = roundScreenLineWidthPx(36f, 52f, 243f, 233f, 233f, 233f, marginPx = 4f)

        assertEquals(2f * (kotlin.math.sqrt(233f * 233f - 197f * 197f) - 4f), top, 0.01f)
        assertTrue(middle > 460f)
        assertEquals(top - 20f, offCentre, 0.01f)
        assertEquals(0f, roundScreenLineWidthPx(-10f, 52f, 233f, 233f, 233f, 233f, 0f), 0f)
    }
}
