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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.SettingsInputAntenna
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import com.lightningstudio.watchrss.R
import com.lightningstudio.watchrss.phoneconnection.PhoneConnectionMode
import com.lightningstudio.watchrss.ui.components.WatchSurface
import com.lightningstudio.watchrss.ui.input.InstallDigitalCrownScrollHandler
import com.lightningstudio.watchrss.ui.settings.WatchSettingsPillRow
import com.lightningstudio.watchrss.ui.testing.PhoneConnectionTestTags
import com.lightningstudio.watchrss.ui.theme.watchDimensionResource

@Composable
fun PhoneConnectionScreen(
    preferredAbilityLabel: String?,
    onModeClick: (PhoneConnectionMode) -> Unit
) {
    val safePadding = watchDimensionResource(R.dimen.watch_safe_padding)
    val sectionSpacing = com.lightningstudio.watchrss.ui.theme.WatchDimens.hey_content_horizontal_distance
    val entrySpacing = com.lightningstudio.watchrss.ui.theme.WatchDimens.hey_distance_8dp
    val valueSpacing = com.lightningstudio.watchrss.ui.theme.WatchDimens.hey_distance_4dp
    val valueIndent = com.lightningstudio.watchrss.ui.theme.WatchDimens.hey_distance_10dp
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

            preferredAbilityLabel?.takeIf { it.isNotBlank() }?.let { label ->
                Spacer(modifier = Modifier.height(com.lightningstudio.watchrss.ui.theme.WatchDimens.hey_distance_4dp))
                Text(
                    text = "当前操作：$label",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(sectionSpacing))

            ConnectionModeEntry(
                title = "纯声波",
                description = "无需同一 WiFi，适合少量数据或慢速同步",
                icon = Icons.Outlined.GraphicEq,
                testTag = PhoneConnectionTestTags.PURE_SOUND_ENTRY,
                onClick = { onModeClick(PhoneConnectionMode.PURE_SOUND) },
                descriptionIndent = valueIndent,
                descriptionTopPadding = valueSpacing
            )

            Spacer(modifier = Modifier.height(entrySpacing))

            ConnectionModeEntry(
                title = "声波引导WiFi连接",
                description = "用声波把会话信息发给手表，再自动建立局域网",
                icon = Icons.Outlined.SettingsInputAntenna,
                testTag = PhoneConnectionTestTags.SOUND_GUIDED_WIFI_ENTRY,
                onClick = { onModeClick(PhoneConnectionMode.SOUND_GUIDED_WIFI) },
                descriptionIndent = valueIndent,
                descriptionTopPadding = valueSpacing
            )

            Spacer(modifier = Modifier.height(entrySpacing))

            ConnectionModeEntry(
                title = "手动WiFi连接",
                description = "先让手机与手表在同一局域网，再扫码连接",
                icon = Icons.Outlined.QrCodeScanner,
                testTag = PhoneConnectionTestTags.MANUAL_WIFI_ENTRY,
                onClick = { onModeClick(PhoneConnectionMode.MANUAL_WIFI) },
                descriptionIndent = valueIndent,
                descriptionTopPadding = valueSpacing
            )

            Spacer(modifier = Modifier.height(trailingSpacerHeight))
        }
    }
}

@Composable
private fun ConnectionModeEntry(
    title: String,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    testTag: String,
    onClick: () -> Unit,
    descriptionIndent: androidx.compose.ui.unit.Dp,
    descriptionTopPadding: androidx.compose.ui.unit.Dp
) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        WatchSettingsPillRow(
            label = title,
            leadingIcon = icon,
            testTag = testTag,
            onClick = onClick
        )
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = descriptionIndent, top = descriptionTopPadding)
        )
    }
}
