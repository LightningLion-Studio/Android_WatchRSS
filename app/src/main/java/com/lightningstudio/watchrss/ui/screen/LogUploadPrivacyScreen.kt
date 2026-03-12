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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lightningstudio.watchrss.R
import com.lightningstudio.watchrss.ui.components.WatchSurface
import com.lightningstudio.watchrss.ui.theme.ActionButtonTextStyle

@Composable
fun LogUploadPrivacyScreen(
    onStartUploadClick: () -> Unit
) {
    val safePadding = dimensionResource(R.dimen.watch_safe_padding)
    val sectionSpacing = 12.dp
    val buttonHeight = dimensionResource(R.dimen.hey_button_height)
    val buttonRadius = dimensionResource(R.dimen.hey_button_default_radius)
    val buttonHorizontalPadding = dimensionResource(R.dimen.hey_button_mergin_horizontal)
    val buttonVerticalPadding = dimensionResource(R.dimen.hey_button_padding_vertical)
    val buttonColor = MaterialTheme.colorScheme.surfaceVariant

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
                text = "隐私及防诈说明",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(sectionSpacing))

            Text(
                text = "只应该在开发者要求时上传日志，以避免泄露个人信息。\n\n" +
                        "日志平时只存储在您的设备本地，仅当您明确点击\u201c开始上传\u201d后才会上传。\n\n" +
                        "开发者只会将日志用于性能提升和Bug修复，不会用于广告及追踪。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Start
            )

            Spacer(modifier = Modifier.height(sectionSpacing))

            Box(
                modifier = Modifier
                    .height(buttonHeight)
                    .clip(RoundedCornerShape(buttonRadius))
                    .background(buttonColor)
                    .clickable(onClick = onStartUploadClick)
                    .padding(horizontal = buttonHorizontalPadding, vertical = buttonVerticalPadding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "开始上传",
                    style = ActionButtonTextStyle,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(sectionSpacing))
        }
    }
}
