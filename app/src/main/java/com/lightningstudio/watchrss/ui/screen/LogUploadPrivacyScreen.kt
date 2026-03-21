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
import com.lightningstudio.watchrss.ui.theme.watchDimensionResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lightningstudio.watchrss.R
import com.lightningstudio.watchrss.ui.components.WatchSurface
import com.lightningstudio.watchrss.ui.input.InstallRotaryScrollHandler
import com.lightningstudio.watchrss.ui.theme.ActionButtonTextStyle

@Composable
fun LogUploadPrivacyScreen(
    onStartUploadClick: () -> Unit
) {
    val safePadding = watchDimensionResource(R.dimen.watch_safe_padding)
    val topPadding = safePadding + watchDimensionResource(R.dimen.hey_distance_2dp)
    val sectionSpacing = 12.dp
    val buttonHeight = watchDimensionResource(R.dimen.hey_button_height)
    val buttonRadius = watchDimensionResource(R.dimen.hey_button_default_radius)
    val buttonHorizontalPadding = watchDimensionResource(R.dimen.hey_button_mergin_horizontal)
    val buttonVerticalPadding = watchDimensionResource(R.dimen.hey_button_padding_vertical)
    val buttonColor = MaterialTheme.colorScheme.surfaceVariant
    val scrollState = rememberScrollState()

    InstallRotaryScrollHandler(scrollState)

    WatchSurface(pureBlack = true) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(
                    start = safePadding,
                    top = topPadding,
                    end = safePadding,
                    bottom = safePadding
                ),
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
                text = "只应在开发者要求协助排障时上传日志，以降低信息暴露风险。\n\n" +
                        "日志默认仅保存在您的设备本地。点击“开始上传”后，应用会跳转并自动开始上传加密日志。\n\n" +
                        "上传日志仅用于排查问题，不用于广告或个性化推荐。根据当前说明，已上传日志会临时存储在美国俄勒冈州服务器，原则上保存24小时；如需删除已上传日志，请通过QQ群1083518433联系并提供取件码等必要信息。",
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
