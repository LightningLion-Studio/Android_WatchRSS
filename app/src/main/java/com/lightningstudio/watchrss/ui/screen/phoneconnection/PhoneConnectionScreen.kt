package com.lightningstudio.watchrss.ui.screen.phoneconnection

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lightningstudio.watchrss.MiniProgramPairingActivity
import com.lightningstudio.watchrss.R
import com.lightningstudio.watchrss.ui.components.DownloadPhoneAppButton
import com.lightningstudio.watchrss.ui.components.WatchSurface
import com.lightningstudio.watchrss.ui.input.InstallDigitalCrownScrollHandler
import com.lightningstudio.watchrss.ui.testing.PhoneConnectionTestTags
import com.lightningstudio.watchrss.ui.theme.WatchDimens
import com.lightningstudio.watchrss.ui.theme.watchDimensionResource

/** Steps of the phone-App route; the watch side listens on its own while WatchRSS is open. */
internal val PHONE_APP_STEPS = listOf(
    "手机与手表已蓝牙配对",
    "在手机打开腕上RSS手机端",
    "在手机端发起同步，手表保持亮屏"
)

/**
 * Landing page: one primary route (WeChat scan, over the internet
 * inside) and the phone-App route folded behind a disclosure, so its download code is never
 * on screen next to a pairing code. Nothing starts until the user picks a route.
 */
@Composable
fun PhoneConnectionScreen(
    onPairMiniProgram: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val safePadding = watchDimensionResource(R.dimen.watch_safe_padding)
    val scrollState = rememberScrollState()
    var appExpanded by rememberSaveable { mutableStateOf(false) }
    InstallDigitalCrownScrollHandler(scrollState)

    WatchSurface(pureBlack = true) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = safePadding)
                .padding(top = 30.dp)
                .testTag(PhoneConnectionTestTags.ROOT),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            PageTitle("连接手机")
            Spacer(Modifier.height(12.dp))
            ConnectionOption(
                title = "微信扫码连接",
                subtitle = "用腕上RSS小程序扫一扫",
                primary = true,
                testTag = PhoneConnectionTestTags.MINI_PROGRAM_ENTRY,
                onClick = onPairMiniProgram ?: {
                    context.startActivity(MiniProgramPairingActivity.createIntent(context))
                }
            )
            Spacer(Modifier.height(8.dp))
            ConnectionOption(
                title = "手机App连接",
                subtitle = if (appExpanded) "按以下步骤操作" else "用腕上RSS手机端同步",
                trailing = if (appExpanded) "收起" else "展开",
                testTag = PhoneConnectionTestTags.PHONE_APP_ENTRY,
                onClick = { appExpanded = !appExpanded }
            )
            if (appExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp, vertical = 12.dp)
                        .testTag(PhoneConnectionTestTags.PHONE_APP_STEPS),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PHONE_APP_STEPS.forEachIndexed { index, step ->
                        Row {
                            Text(
                                text = "${index + 1}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.width(16.dp)
                            )
                            Text(
                                text = step,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
                DownloadPhoneAppButton(
                    operation = "手机连接同步",
                    prompt = "还没装？下载手机端App",
                    testTag = PhoneConnectionTestTags.DOWNLOAD_ENTRY
                )
            }
            Spacer(Modifier.height(WatchDimens.hey_multiple_item_height))
        }
    }
}
