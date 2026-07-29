package com.lightningstudio.watchrss.ui.reader

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lightningstudio.watchrss.ui.theme.WatchDimens

object ReaderPageLayout {
    val horizontalPadding: Dp
        @Composable get() = WatchDimens.detail_page_horizontal_padding

    val topSafePadding: Dp
        @Composable get() = WatchDimens.watch_safe_padding

    val titleGap: Dp
        @Composable get() = WatchDimens.hey_distance_4dp

    val titleTopPadding: Dp
        @Composable get() = topSafePadding + titleGap

    val titleHorizontalPadding: Dp
        @Composable get() = WatchDimens.detail_title_safe_padding

    val blockSpacing: Dp
        @Composable get() = WatchDimens.detail_block_spacing

    fun bottomPadding(hasFloatingAction: Boolean): Dp =
        if (hasFloatingAction) 56.dp else 15.dp
}
