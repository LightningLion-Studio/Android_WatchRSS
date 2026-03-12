package com.lightningstudio.watchrss.ui.screen

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.unit.dp
import com.lightningstudio.watchrss.R
import com.lightningstudio.watchrss.ui.components.WatchSurface

@Composable
fun ContactDeveloperScreen(
    onJoinGroupClick: () -> Unit,
    onUploadLogClick: () -> Unit
) {
    val safePadding = dimensionResource(R.dimen.watch_safe_padding)
    val sectionSpacing = 12.dp
    val entrySpacing = 8.dp
    val pillHeight = 48.dp
    val pillRadius = 24.dp
    val pillColor = colorResource(R.color.watch_pill_background)
    val pillHorizontalPadding = 10.dp
    val pillVerticalPadding = 8.dp
    val iconSize = 20.dp
    val iconSpacing = 12.dp

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
                text = "联系开发者",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(sectionSpacing))

            ContactEntry(
                title = "加群",
                iconRes = R.drawable.ic_action_share,
                onClick = onJoinGroupClick,
                pillHeight = pillHeight,
                pillRadius = pillRadius,
                pillColor = pillColor,
                pillHorizontalPadding = pillHorizontalPadding,
                pillVerticalPadding = pillVerticalPadding,
                iconSize = iconSize,
                iconSpacing = iconSpacing
            )

            Spacer(modifier = Modifier.height(entrySpacing))

            ContactEntry(
                title = "上传日志",
                iconRes = R.drawable.ic_settings,
                onClick = onUploadLogClick,
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
private fun ContactEntry(
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
    Row(
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
