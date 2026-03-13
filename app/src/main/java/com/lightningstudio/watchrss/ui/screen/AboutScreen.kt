package com.lightningstudio.watchrss.ui.screen

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.lightningstudio.watchrss.R
import com.lightningstudio.watchrss.ui.components.WatchSurface

@Composable
fun AboutScreen(
    onIntroClick: () -> Unit,
    onPrivacyClick: () -> Unit,
    onTermsClick: () -> Unit,
    onLicensesClick: () -> Unit,
    onCollaboratorsClick: () -> Unit
) {
    val safePadding = dimensionResource(R.dimen.watch_safe_padding)
    val sectionSpacing = com.lightningstudio.watchrss.ui.theme.WatchDimens.hey_content_horizontal_distance
    val entrySpacing = com.lightningstudio.watchrss.ui.theme.WatchDimens.hey_distance_8dp
    val summarySpacing = com.lightningstudio.watchrss.ui.theme.WatchDimens.hey_distance_4dp
    val pillHeight = com.lightningstudio.watchrss.ui.theme.WatchDimens.hey_multiple_item_height
    val pillStartPadding = com.lightningstudio.watchrss.ui.theme.WatchDimens.hey_content_horizontal_distance_6_0
    val pillEndPadding = com.lightningstudio.watchrss.ui.theme.WatchDimens.hey_distance_10dp
    val pillVerticalPadding = com.lightningstudio.watchrss.ui.theme.WatchDimens.hey_distance_8dp
    val pillRadius = com.lightningstudio.watchrss.ui.theme.WatchDimens.hey_button_default_radius
    val pillColor = colorResource(R.color.watch_pill_background)

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
                text = "关于",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(sectionSpacing))

            AboutEntry(
                title = "项目自介",
                summary = "腕上 RSS 聚合阅读",
                onClick = onIntroClick,
                pillHeight = pillHeight,
                pillRadius = pillRadius,
                pillColor = pillColor,
                pillStartPadding = pillStartPadding,
                pillEndPadding = pillEndPadding,
                pillVerticalPadding = pillVerticalPadding,
                summarySpacing = summarySpacing
            )

            Spacer(modifier = Modifier.height(entrySpacing))

            AboutEntry(
                title = "隐私政策",
                summary = "个人信息与日志说明",
                onClick = onPrivacyClick,
                pillHeight = pillHeight,
                pillRadius = pillRadius,
                pillColor = pillColor,
                pillStartPadding = pillStartPadding,
                pillEndPadding = pillEndPadding,
                pillVerticalPadding = pillVerticalPadding,
                summarySpacing = summarySpacing
            )

            Spacer(modifier = Modifier.height(entrySpacing))

            AboutEntry(
                title = "用户协议",
                summary = "使用条款",
                onClick = onTermsClick,
                pillHeight = pillHeight,
                pillRadius = pillRadius,
                pillColor = pillColor,
                pillStartPadding = pillStartPadding,
                pillEndPadding = pillEndPadding,
                pillVerticalPadding = pillVerticalPadding,
                summarySpacing = summarySpacing
            )

            Spacer(modifier = Modifier.height(entrySpacing))

            AboutEntry(
                title = "开源许可与清单",
                summary = "第三方库信息",
                onClick = onLicensesClick,
                pillHeight = pillHeight,
                pillRadius = pillRadius,
                pillColor = pillColor,
                pillStartPadding = pillStartPadding,
                pillEndPadding = pillEndPadding,
                pillVerticalPadding = pillVerticalPadding,
                summarySpacing = summarySpacing
            )

            Spacer(modifier = Modifier.height(entrySpacing))

            AboutEntry(
                title = "贡献者",
                summary = "协作者名单",
                onClick = onCollaboratorsClick,
                pillHeight = pillHeight,
                pillRadius = pillRadius,
                pillColor = pillColor,
                pillStartPadding = pillStartPadding,
                pillEndPadding = pillEndPadding,
                pillVerticalPadding = pillVerticalPadding,
                summarySpacing = summarySpacing
            )

            Spacer(modifier = Modifier.height(pillHeight))
        }
    }
}

@Composable
private fun AboutEntry(
    title: String,
    summary: String,
    onClick: () -> Unit,
    pillHeight: androidx.compose.ui.unit.Dp,
    pillRadius: androidx.compose.ui.unit.Dp,
    pillColor: androidx.compose.ui.graphics.Color,
    pillStartPadding: androidx.compose.ui.unit.Dp,
    pillEndPadding: androidx.compose.ui.unit.Dp,
    pillVerticalPadding: androidx.compose.ui.unit.Dp,
    summarySpacing: androidx.compose.ui.unit.Dp
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(pillHeight)
                .clip(RoundedCornerShape(pillRadius))
                .background(pillColor)
                .padding(
                    start = pillStartPadding,
                    end = pillEndPadding,
                    top = pillVerticalPadding,
                    bottom = pillVerticalPadding
                )
                .clickable(onClick = onClick),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            text = summary,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = pillStartPadding, top = summarySpacing)
        )
    }
}
