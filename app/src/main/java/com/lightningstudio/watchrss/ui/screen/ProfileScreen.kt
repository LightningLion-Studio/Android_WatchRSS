package com.lightningstudio.watchrss.ui.screen

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.lightningstudio.watchrss.R
import com.lightningstudio.watchrss.ui.components.WatchSurface

@Composable
fun ProfileScreen(
    onFavoritesClick: () -> Unit,
    onWatchLaterClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onAboutClick: () -> Unit,
    onContactDeveloperClick: () -> Unit
) {
    val safePadding = dimensionResource(R.dimen.watch_safe_padding)
    val sectionSpacing = com.lightningstudio.watchrss.ui.theme.WatchDimens.hey_content_horizontal_distance
    val entrySpacing = com.lightningstudio.watchrss.ui.theme.WatchDimens.hey_distance_8dp
    val pillHeight = com.lightningstudio.watchrss.ui.theme.WatchDimens.hey_multiple_item_height
    val pillRadius = com.lightningstudio.watchrss.ui.theme.WatchDimens.hey_button_default_radius
    val pillColor = colorResource(R.color.watch_pill_background)
    val pillHorizontalPadding = com.lightningstudio.watchrss.ui.theme.WatchDimens.hey_distance_10dp
    val pillVerticalPadding = com.lightningstudio.watchrss.ui.theme.WatchDimens.hey_distance_8dp
    val iconSize = com.lightningstudio.watchrss.ui.theme.WatchDimens.hey_listitem_lefticon_height_width
    val iconSpacing = com.lightningstudio.watchrss.ui.theme.WatchDimens.hey_content_horizontal_distance

    WatchSurface(pureBlack = true) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(safePadding),
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "我的",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(sectionSpacing))

            ProfileEntry(
                title = "我的收藏",
                iconRes = R.drawable.ic_action_favorite,
                onClick = onFavoritesClick,
                pillHeight = pillHeight,
                pillRadius = pillRadius,
                pillColor = pillColor,
                pillHorizontalPadding = pillHorizontalPadding,
                pillVerticalPadding = pillVerticalPadding,
                iconSize = iconSize,
                iconSpacing = iconSpacing
            )

            Spacer(modifier = Modifier.height(entrySpacing))

            ProfileEntry(
                title = "稍后再看",
                iconRes = R.drawable.ic_play_circle,
                onClick = onWatchLaterClick,
                pillHeight = pillHeight,
                pillRadius = pillRadius,
                pillColor = pillColor,
                pillHorizontalPadding = pillHorizontalPadding,
                pillVerticalPadding = pillVerticalPadding,
                iconSize = iconSize,
                iconSpacing = iconSpacing
            )

            Spacer(modifier = Modifier.height(entrySpacing))

            ProfileEntry(
                title = "设置",
                iconRes = R.drawable.ic_settings,
                onClick = onSettingsClick,
                pillHeight = pillHeight,
                pillRadius = pillRadius,
                pillColor = pillColor,
                pillHorizontalPadding = pillHorizontalPadding,
                pillVerticalPadding = pillVerticalPadding,
                iconSize = iconSize,
                iconSpacing = iconSpacing
            )

            Spacer(modifier = Modifier.height(entrySpacing))

            ProfileEntry(
                title = "关于",
                iconRes = R.drawable.ic_action_share,
                onClick = onAboutClick,
                pillHeight = pillHeight,
                pillRadius = pillRadius,
                pillColor = pillColor,
                pillHorizontalPadding = pillHorizontalPadding,
                pillVerticalPadding = pillVerticalPadding,
                iconSize = iconSize,
                iconSpacing = iconSpacing
            )

            Spacer(modifier = Modifier.height(entrySpacing))

            ProfileEntry(
                title = "联系开发者",
                iconRes = R.drawable.ic_person,
                onClick = onContactDeveloperClick,
                pillHeight = pillHeight,
                pillRadius = pillRadius,
                pillColor = pillColor,
                pillHorizontalPadding = pillHorizontalPadding,
                pillVerticalPadding = pillVerticalPadding,
                iconSize = iconSize,
                iconSpacing = iconSpacing
            )

            Spacer(modifier = Modifier.height(pillHeight))
        }
    }
}

@Composable
private fun ProfileEntry(
    title: String,
    @DrawableRes iconRes: Int,
    onClick: () -> Unit,
    pillHeight: androidx.compose.ui.unit.Dp,
    pillRadius: androidx.compose.ui.unit.Dp,
    pillColor: androidx.compose.ui.graphics.Color,
    pillHorizontalPadding: androidx.compose.ui.unit.Dp,
    pillVerticalPadding: androidx.compose.ui.unit.Dp,
    iconSize: androidx.compose.ui.unit.Dp,
    iconSpacing: androidx.compose.ui.unit.Dp
) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(pillHeight)
            .clip(RoundedCornerShape(pillRadius))
            .background(pillColor)
            .clickable(onClick = onClick)
            .padding(
                start = pillHorizontalPadding,
                end = pillHorizontalPadding,
                top = pillVerticalPadding,
                bottom = pillVerticalPadding
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(iconSize)
        )
        Spacer(modifier = Modifier.width(iconSpacing))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
