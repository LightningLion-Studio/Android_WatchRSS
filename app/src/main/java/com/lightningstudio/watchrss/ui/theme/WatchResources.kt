package com.lightningstudio.watchrss.ui.theme

import androidx.annotation.ColorRes
import androidx.annotation.DimenRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import com.lightningstudio.watchrss.R

@Composable
fun watchDimensionResource(@DimenRes id: Int): Dp {
    return when (id) {
        R.dimen.watch_safe_padding -> WatchDimens.watch_safe_padding
        R.dimen.watch_action_button_width -> WatchDimens.watch_action_button_width
        R.dimen.watch_action_button_height -> WatchDimens.watch_action_button_height
        R.dimen.watch_swipe_action_button_width -> WatchDimens.watch_swipe_action_button_width
        R.dimen.detail_page_horizontal_padding -> WatchDimens.detail_page_horizontal_padding
        R.dimen.detail_block_spacing -> WatchDimens.detail_block_spacing
        R.dimen.detail_title_safe_padding -> WatchDimens.detail_title_safe_padding
        R.dimen.detail_title_first_line_max_width -> WatchDimens.detail_title_first_line_max_width
        R.dimen.detail_title_second_line_max_width -> WatchDimens.detail_title_second_line_max_width
        R.dimen.hey_button_default_radius -> WatchDimens.hey_button_default_radius
        R.dimen.hey_card_normal_bg_radius -> WatchDimens.hey_card_normal_bg_radius
        R.dimen.hey_content_horizontal_distance -> WatchDimens.hey_content_horizontal_distance
        R.dimen.hey_content_horizontal_distance_6_0 -> WatchDimens.hey_content_horizontal_distance_6_0
        else -> dimensionResource(id)
    }
}

@Composable
fun watchColorResource(@ColorRes id: Int): Color = colorResource(id)

@Composable
fun watchTextUnitResource(@DimenRes id: Int): TextUnit {
    val density = LocalDensity.current
    return with(density) { watchDimensionResource(id).toSp() }
}
