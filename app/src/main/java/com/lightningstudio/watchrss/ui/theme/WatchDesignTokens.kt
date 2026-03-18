package com.lightningstudio.watchrss.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.min
import kotlin.math.max

data class WatchSpacingTokens(
    val distance2: Dp,
    val distance4: Dp,
    val distance6: Dp,
    val distance8: Dp,
    val distance10: Dp,
    val distance12: Dp,
    val distance20: Dp,
    val contentHorizontal: Dp,
    val contentHorizontalWide: Dp
)

data class WatchShapeTokens(
    val buttonDefaultRadius: Dp,
    val cardNormalRadius: Dp
)

data class WatchSizeTokens(
    val listItemLeftIcon: Dp,
    val multipleItemHeight: Dp
)

data class WatchLayoutTokens(
    val safePadding: Dp,
    val safeVerticalPadding: Dp,
    val actionButtonWidth: Dp,
    val actionButtonHeight: Dp,
    val swipeActionButtonWidth: Dp,
    val detailPageHorizontalPadding: Dp,
    val detailBlockSpacing: Dp,
    val detailTitleSafePadding: Dp,
    val detailTitleFirstLineMaxWidth: Dp,
    val detailTitleSecondLineMaxWidth: Dp
)

data class WatchTokens(
    val spacing: WatchSpacingTokens,
    val shapes: WatchShapeTokens,
    val sizes: WatchSizeTokens,
    val layout: WatchLayoutTokens
)

private val BaseWatchTokens = WatchTokens(
    spacing = WatchSpacingTokens(
        distance2 = 2.dp,
        distance4 = 4.dp,
        distance6 = 6.dp,
        distance8 = 8.dp,
        distance10 = 10.dp,
        distance12 = 12.dp,
        distance20 = 20.dp,
        contentHorizontal = 9.dp,
        contentHorizontalWide = 14.dp
    ),
    shapes = WatchShapeTokens(
        buttonDefaultRadius = 47.dp,
        cardNormalRadius = 16.dp
    ),
    sizes = WatchSizeTokens(
        listItemLeftIcon = 30.dp,
        multipleItemHeight = 52.dp
    ),
    layout = WatchLayoutTokens(
        safePadding = 18.dp,
        safeVerticalPadding = 8.dp,
        actionButtonWidth = 160.dp,
        actionButtonHeight = 48.dp,
        swipeActionButtonWidth = 72.dp,
        detailPageHorizontalPadding = 14.dp,
        detailBlockSpacing = 6.dp,
        detailTitleSafePadding = 0.dp,
        detailTitleFirstLineMaxWidth = 150.dp,
        detailTitleSecondLineMaxWidth = 190.dp
    )
)

private val RoundWatchTokens = WatchTokens(
    spacing = BaseWatchTokens.spacing,
    shapes = BaseWatchTokens.shapes,
    sizes = BaseWatchTokens.sizes,
    layout = BaseWatchTokens.layout.copy(
        safeVerticalPadding = 18.dp
    )
)

@Composable
fun rememberWatchTokens(): WatchTokens {
    val isRound = LocalConfiguration.current.isScreenRound
    return watchTokensFor(isRound)
}

@Composable
fun rememberIsRoundWatch(): Boolean = LocalConfiguration.current.isScreenRound

internal fun watchTokensFor(isRound: Boolean): WatchTokens {
    return if (isRound) RoundWatchTokens else BaseWatchTokens
}

object WatchDimens {
    val hey_button_default_radius: Dp
        @Composable get() = rememberWatchTokens().shapes.buttonDefaultRadius

    val hey_card_normal_bg_radius: Dp
        @Composable get() = rememberWatchTokens().shapes.cardNormalRadius

    val hey_content_horizontal_distance: Dp
        @Composable get() = rememberWatchTokens().spacing.contentHorizontal

    val hey_content_horizontal_distance_6_0: Dp
        @Composable get() = rememberWatchTokens().spacing.contentHorizontalWide

    val hey_distance_2dp: Dp
        @Composable get() = rememberWatchTokens().spacing.distance2

    val hey_distance_4dp: Dp
        @Composable get() = rememberWatchTokens().spacing.distance4

    val hey_distance_6dp: Dp
        @Composable get() = rememberWatchTokens().spacing.distance6

    val hey_distance_8dp: Dp
        @Composable get() = rememberWatchTokens().spacing.distance8

    val hey_distance_10dp: Dp
        @Composable get() = rememberWatchTokens().spacing.distance10

    val hey_distance_12dp: Dp
        @Composable get() = rememberWatchTokens().spacing.distance12

    val hey_distance_20dp: Dp
        @Composable get() = rememberWatchTokens().spacing.distance20

    val hey_listitem_lefticon_height_width: Dp
        @Composable get() = rememberWatchTokens().sizes.listItemLeftIcon

    val hey_multiple_item_height: Dp
        @Composable get() = rememberWatchTokens().sizes.multipleItemHeight

    val watch_safe_padding: Dp
        @Composable get() = rememberWatchTokens().layout.safePadding

    val watch_safe_vertical_padding: Dp
        @Composable get() = rememberWatchTokens().layout.safeVerticalPadding

    val watch_action_button_width: Dp
        @Composable get() = rememberWatchTokens().layout.actionButtonWidth

    val watch_action_button_height: Dp
        @Composable get() = rememberWatchTokens().layout.actionButtonHeight

    val watch_swipe_action_button_width: Dp
        @Composable get() = rememberWatchTokens().layout.swipeActionButtonWidth

    val detail_page_horizontal_padding: Dp
        @Composable get() = rememberWatchTokens().layout.detailPageHorizontalPadding

    val detail_block_spacing: Dp
        @Composable get() = rememberWatchTokens().layout.detailBlockSpacing

    val detail_title_safe_padding: Dp
        @Composable get() = rememberWatchTokens().layout.detailTitleSafePadding

    val detail_title_first_line_max_width: Dp
        @Composable get() = rememberWatchTokens().layout.detailTitleFirstLineMaxWidth

    val detail_title_second_line_max_width: Dp
        @Composable get() = rememberWatchTokens().layout.detailTitleSecondLineMaxWidth
}

@Composable
fun watchActionButtonWidthFor(
    availableWidth: Dp,
    reservedHorizontalSpace: Dp = 0.dp
): Dp {
    val preferredWidth = WatchDimens.watch_action_button_width
    val roundInset = if (rememberIsRoundWatch()) {
        WatchDimens.watch_safe_padding / 2
    } else {
        0.dp
    }
    return (availableWidth - reservedHorizontalSpace - roundInset * 2)
        .coerceAtLeast(0.dp)
        .coerceAtMost(preferredWidth)
}

@Composable
fun watchSwipeActionButtonWidthFor(
    availableWidth: Dp,
    actionCount: Int = 2,
    actionPadding: Dp = WatchDimens.hey_distance_4dp
): Dp {
    if (actionCount <= 0) return 0.dp
    val preferredWidth = WatchDimens.watch_swipe_action_button_width
    val totalPadding = actionPadding * (actionCount + 1)
    return ((availableWidth - totalPadding).coerceAtLeast(0.dp) / actionCount)
        .coerceAtMost(preferredWidth)
}

@Composable
fun watchQrSizeFor(
    availableWidth: Dp,
    availableHeight: Dp,
    preferredSize: Dp
): Dp {
    val roundInset = if (rememberIsRoundWatch()) {
        WatchDimens.watch_safe_padding / 2
    } else {
        0.dp
    }
    val safeWidth = (availableWidth - roundInset * 2).coerceAtLeast(0.dp)
    val safeHeight = (availableHeight - roundInset * 2).coerceAtLeast(0.dp)
    return safeWidth
        .coerceAtMost(safeHeight)
        .coerceAtMost(preferredSize)
}

@Composable
fun rememberWatchTitleLineLimitsPx(availableWidthPx: Float): Pair<Float, Float> {
    val density = LocalDensity.current
    return rememberWatchTitleLineLimitsPx(availableWidthPx, density)
}

fun rememberWatchTitleLineLimitsPx(
    availableWidthPx: Float,
    density: Density
): Pair<Float, Float> {
    val firstBasePx = with(density) { BaseWatchTokens.layout.detailTitleFirstLineMaxWidth.toPx() }
    val secondBasePx = with(density) { BaseWatchTokens.layout.detailTitleSecondLineMaxWidth.toPx() }
    val firstLimit = min(firstBasePx, availableWidthPx * 0.82f).coerceAtMost(availableWidthPx)
    val secondLimit = min(secondBasePx, availableWidthPx * 0.94f).coerceAtMost(availableWidthPx)
    return firstLimit to secondLimit
}
