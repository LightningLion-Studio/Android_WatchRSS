package com.lightningstudio.watchrss.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import com.lightningstudio.watchrss.ui.theme.watchColorResource
import com.lightningstudio.watchrss.ui.theme.watchDimensionResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.lightningstudio.watchrss.R
import com.lightningstudio.watchrss.ui.components.WatchSurface
import com.lightningstudio.watchrss.ui.input.InstallDigitalCrownScrollHandler
import com.lightningstudio.watchrss.ui.testing.ProfileTestTags

@Composable
fun ProfileScreen(
    showAccountEntry: Boolean = true,
    showPhoneConnectionEntry: Boolean = true,
    onAccountClick: () -> Unit = {},
    onFavoritesClick: () -> Unit,
    onWatchLaterClick: () -> Unit,
    onNotesClick: () -> Unit,
    onPhoneConnectionClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onAboutClick: () -> Unit,
    onContactDeveloperClick: () -> Unit,
    onBeianClick: () -> Unit
) {
    val safePadding = watchDimensionResource(R.dimen.watch_safe_padding)
    val sectionSpacing = com.lightningstudio.watchrss.ui.theme.WatchDimens.hey_content_horizontal_distance
    val entrySpacing = com.lightningstudio.watchrss.ui.theme.WatchDimens.hey_distance_8dp
    val pillHeight = com.lightningstudio.watchrss.ui.theme.WatchDimens.hey_multiple_item_height
    val pillRadius = com.lightningstudio.watchrss.ui.theme.WatchDimens.hey_button_default_radius
    val pillColor = watchColorResource(R.color.watch_pill_background)
    val pillHorizontalPadding = com.lightningstudio.watchrss.ui.theme.WatchDimens.hey_distance_10dp
    val pillVerticalPadding = com.lightningstudio.watchrss.ui.theme.WatchDimens.hey_distance_8dp
    val iconSize = com.lightningstudio.watchrss.ui.theme.WatchDimens.hey_listitem_lefticon_height_width
    val iconSpacing = com.lightningstudio.watchrss.ui.theme.WatchDimens.hey_content_horizontal_distance
    val scrollState = rememberScrollState()

    InstallDigitalCrownScrollHandler(scrollState)

    WatchSurface(pureBlack = true) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(safePadding)
                .testTag(ProfileTestTags.ROOT),
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

            if (showAccountEntry) {
                ProfileEntry(
                    title = "账号",
                    icon = Icons.Outlined.Person,
                    onClick = onAccountClick,
                    testTag = ProfileTestTags.ACCOUNT_ENTRY,
                    pillHeight = pillHeight,
                    pillRadius = pillRadius,
                    pillColor = pillColor,
                    pillHorizontalPadding = pillHorizontalPadding,
                    pillVerticalPadding = pillVerticalPadding,
                    iconSize = iconSize,
                    iconSpacing = iconSpacing
                )

                Spacer(modifier = Modifier.height(entrySpacing))
            }

            ProfileEntry(
                title = "我的收藏",
                icon = Icons.Filled.Star,
                onClick = onFavoritesClick,
                testTag = ProfileTestTags.FAVORITES_ENTRY,
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
                icon = Icons.Filled.PlayCircle,
                onClick = onWatchLaterClick,
                testTag = ProfileTestTags.WATCH_LATER_ENTRY,
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
                title = "备忘录",
                icon = Icons.Outlined.EditNote,
                onClick = onNotesClick,
                testTag = "profile_notes_entry",
                pillHeight = pillHeight,
                pillRadius = pillRadius,
                pillColor = pillColor,
                pillHorizontalPadding = pillHorizontalPadding,
                pillVerticalPadding = pillVerticalPadding,
                iconSize = iconSize,
                iconSpacing = iconSpacing
            )

            if (showPhoneConnectionEntry) {
                Spacer(modifier = Modifier.height(entrySpacing))

                ProfileEntry(
                    title = "连接手机",
                    icon = Icons.Outlined.PhoneAndroid,
                    onClick = onPhoneConnectionClick,
                    testTag = ProfileTestTags.PHONE_CONNECTION_ENTRY,
                    pillHeight = pillHeight,
                    pillRadius = pillRadius,
                    pillColor = pillColor,
                    pillHorizontalPadding = pillHorizontalPadding,
                    pillVerticalPadding = pillVerticalPadding,
                    iconSize = iconSize,
                    iconSpacing = iconSpacing
                )
            }

            Spacer(modifier = Modifier.height(entrySpacing))

            ProfileEntry(
                title = "设置",
                icon = Icons.Outlined.Settings,
                onClick = onSettingsClick,
                testTag = ProfileTestTags.SETTINGS_ENTRY,
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
                icon = Icons.Outlined.Info,
                onClick = onAboutClick,
                testTag = ProfileTestTags.ABOUT_ENTRY,
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
                icon = Icons.Outlined.Person,
                onClick = onContactDeveloperClick,
                testTag = ProfileTestTags.CONTACT_DEVELOPER_ENTRY,
                pillHeight = pillHeight,
                pillRadius = pillRadius,
                pillColor = pillColor,
                pillHorizontalPadding = pillHorizontalPadding,
                pillVerticalPadding = pillVerticalPadding,
                iconSize = iconSize,
                iconSpacing = iconSpacing
            )

            Spacer(modifier = Modifier.height(pillHeight))

            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "浙ICP备2024111886号-5A",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .testTag(ProfileTestTags.BEIAN_ENTRY)
                        .clickable(onClick = onBeianClick)
                )
            }
        }
    }
}

@Composable
private fun ProfileEntry(
    title: String,
    icon: ImageVector,
    onClick: () -> Unit,
    testTag: String,
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
            .testTag(testTag)
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
            imageVector = icon,
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
