package com.lightningstudio.watchrss.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

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

data class WatchTokens(
    val spacing: WatchSpacingTokens,
    val shapes: WatchShapeTokens,
    val sizes: WatchSizeTokens
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
    )
)

private val RoundWatchTokens = WatchTokens(
    spacing = BaseWatchTokens.spacing.copy(
        contentHorizontal = 12.dp,
        contentHorizontalWide = 28.dp
    ),
    shapes = BaseWatchTokens.shapes.copy(
        buttonDefaultRadius = 60.dp,
        cardNormalRadius = 30.dp
    ),
    sizes = BaseWatchTokens.sizes.copy(
        listItemLeftIcon = 30.dp
    )
)

@Composable
fun rememberWatchTokens(): WatchTokens {
    val isRound = LocalConfiguration.current.isScreenRound
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
}
