package com.lightningstudio.watchrss.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Density
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchThemeTokensTest {

    @Test
    fun typographyDefinesAllUsedSlotsWithProjectFontFamily() {
        val projectFontFamily = Typography.bodyLarge.fontFamily

        val usedSlots = listOf(
            Typography.titleLarge,
            Typography.titleMedium,
            Typography.titleSmall,
            Typography.labelLarge,
            Typography.labelMedium,
            Typography.labelSmall
        )

        assertTrue(usedSlots.all { it.fontFamily == projectFontFamily })
        assertTrue(usedSlots.all { it.fontSize.value > 0f && it.lineHeight.value > 0f })
    }

    @Test
    fun colorSchemesOwnSemanticSlotsUsedByUi() {
        assertEquals(WatchDanger, WatchDarkColorScheme.error)
        assertEquals(WatchPinnedSurface, WatchDarkColorScheme.tertiary)
        assertEquals(WatchTextPrimary, WatchDarkColorScheme.onTertiary)

        assertEquals(Color(0xFFB3261E), WatchLightColorScheme.error)
        assertEquals(WatchPinnedSurface, WatchLightColorScheme.tertiary)
        assertEquals(Color.White, WatchLightColorScheme.onTertiary)
    }

    @Test
    fun roundAndSquareTokensShareVisualDefaults() {
        val square = watchTokensFor(isRound = false)
        val round = watchTokensFor(isRound = true)

        assertEquals(square.spacing, round.spacing)
        assertEquals(square.shapes, round.shapes)
        assertEquals(square.sizes, round.sizes)
        assertEquals(square.layout.safePadding, round.layout.safePadding)
        assertEquals(square.layout.actionButtonWidth, round.layout.actionButtonWidth)
        assertEquals(square.layout.actionButtonHeight, round.layout.actionButtonHeight)
        assertEquals(square.layout.swipeActionButtonWidth, round.layout.swipeActionButtonWidth)
        assertEquals(square.layout.detailPageHorizontalPadding, round.layout.detailPageHorizontalPadding)
        assertTrue(round.layout.safeVerticalPadding >= square.layout.safeVerticalPadding)
    }

    @Test
    fun titleLineLimitsShrinkOnNarrowWidths() {
        val density = Density(1f)
        val (first, second) = rememberWatchTitleLineLimitsPx(100f, density)

        assertEquals(82f, first, 0.001f)
        assertEquals(94f, second, 0.001f)
    }

    @Test
    fun titleLineLimitsRespectConfiguredCapsOnWideWidths() {
        val density = Density(1f)
        val (first, second) = rememberWatchTitleLineLimitsPx(300f, density)

        assertEquals(150f, first, 0.001f)
        assertEquals(190f, second, 0.001f)
    }
}
