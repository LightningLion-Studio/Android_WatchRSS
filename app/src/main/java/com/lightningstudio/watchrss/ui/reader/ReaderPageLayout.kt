package com.lightningstudio.watchrss.ui.reader

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lightningstudio.watchrss.ui.theme.WatchDimens

object ReaderPageLayout {
    val horizontalPadding: Dp
        @Composable get() = WatchDimens.detail_page_horizontal_padding

    val titleTopPadding: Dp
        @Composable get() = WatchDimens.watch_safe_padding + WatchDimens.hey_distance_4dp

    val blockSpacing: Dp
        @Composable get() = WatchDimens.detail_block_spacing

    fun bottomPadding(hasFloatingAction: Boolean): Dp =
        if (hasFloatingAction) 56.dp else 15.dp
}
