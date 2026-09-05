package com.lightningstudio.watchrss.ui.components

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp


object AppTransparencyStore {
    private const val PREFERENCES_NAME = "watchrss_transparency"
    private const val INITIAL_APP_DISCLOSURE_KEY = "initial_app_disclosure_v1"

    fun isInitialAppDisclosureAcknowledged(context: Context): Boolean = context
        .applicationContext
        .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        .getBoolean(INITIAL_APP_DISCLOSURE_KEY, false)

    fun acknowledgeInitialAppDisclosure(context: Context) {
        context.applicationContext
            .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(INITIAL_APP_DISCLOSURE_KEY, true)
            .apply()
    }
}

@Composable
fun InitialAppTransparencyDialog(
    onAcknowledge: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { },
        title = { Text("使用前说明") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("本 App 由第三方开发者及社区用户贡献，与哔哩哔哩无合作关系，哔哩哔哩是上海宽娱数码科技有限公司的商标。")
                Text("本 App 不是哔哩哔哩的替代品，建议能使用官方客户端时尽量使用官方客户端。")
                Text("本 App 均使用来源于网络的公开信息进行开发。")
                Text("本 App 中和 B 站相关的功能完全免费。")
                Text("本 App 中所呈现的 B 站内容来自哔哩哔哩官方。")
            }
        },
        confirmButton = {
            TextButton(onClick = onAcknowledge) { Text("我已了解") }
        }
    )
}

/**
 * Persistent relationship label for connected third-party platforms.
 * Keep this visible after the entry confirmation has been dismissed.
 */
@Composable
fun ThirdPartyPlatformNotice(
    platform: String,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = if (compact) 6.dp else 9.dp),
            verticalArrangement = Arrangement.spacedBy(if (compact) 2.dp else 4.dp)
        ) {
            Text(
                text = "腕上RSS提供的第三方平台功能",
                style = if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
            Text(
                text = "这是腕上RSS连接$platform的精选功能，不是$platform官方客户端。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
            if (!compact) {
                Text(
                    text = "平台账号、平台会员和内容规则由$platform独立管理；当前支持范围不因¥6手机版设备授权而改变。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
fun ThirdPartyPlatformScopeCard(
    platform: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.46f),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text("当前支持范围", fontWeight = FontWeight.SemiBold)
            Text(
                "已提供：精选频道浏览、搜索、视频播放、收藏与稍后再看。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "平台账号相关操作受账号权限、接口和内容规则影响；当前未提供的官方客户端能力，不等于付费后解锁。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "只有明确标注‘需要手机版设备授权’的功能才与¥6有关；其余平台功能与这笔付款无关。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun WatchRssAccessBoundaryCard(
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.46f),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text("手机版设备授权包", fontWeight = FontWeight.SemiBold)
            Text(
                "¥6主要用于小说、备忘录和手机与手表协同，不是哔哩哔哩或抖音会员，也不会提升播放速度或清晰度。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "WatchRSS云会员与平台会员分别由WatchRSS和对应平台管理。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}


@Composable
fun ThirdPartyPlatformConfirmationDialog(
    platform: String,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { },
        title = { Text("使用前说明") },
        text = {
            Text(
                "本 App 与$platform及其关联公司或子公司不存在任何关联，也未获其授权、维护、赞助或认可。\n\n" +
                    "这是独立且非官方的软件，使用风险由您自行承担。平台账号、内容和平台规则由$platform独立管理。"
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("知道了，继续") }
        }
    )
}
