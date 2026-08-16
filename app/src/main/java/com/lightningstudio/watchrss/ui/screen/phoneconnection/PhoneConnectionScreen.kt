package com.lightningstudio.watchrss.ui.screen.phoneconnection

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import com.lightningstudio.watchrss.R
import com.lightningstudio.watchrss.ui.components.DownloadPhoneAppButton
import com.lightningstudio.watchrss.ui.components.WatchSurface
import com.lightningstudio.watchrss.ui.input.InstallDigitalCrownScrollHandler
import com.lightningstudio.watchrss.ui.testing.PhoneConnectionTestTags
import com.lightningstudio.watchrss.ui.theme.watchDimensionResource

@Composable
fun PhoneConnectionScreen() {
    val safePadding = watchDimensionResource(R.dimen.watch_safe_padding)
    val sectionSpacing = com.lightningstudio.watchrss.ui.theme.WatchDimens.hey_content_horizontal_distance
    val trailingSpacerHeight = com.lightningstudio.watchrss.ui.theme.WatchDimens.hey_multiple_item_height
    val scrollState = rememberScrollState()

    InstallDigitalCrownScrollHandler(scrollState)

    WatchSurface(pureBlack = true) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(safePadding)
                .testTag(PhoneConnectionTestTags.ROOT),
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "连接手机",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(sectionSpacing))

            Text(
                text = "请在与手表蓝牙配对了的手机上下载并打开腕上RSS手机端后操作",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(sectionSpacing))

            DownloadPhoneAppButton(operation = "手机连接同步")

            Spacer(modifier = Modifier.height(trailingSpacerHeight))
        }
    }
}
