package com.lightningstudio.watchrss.ui.screen.rss

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import com.lightningstudio.watchrss.BuildConfig
import com.lightningstudio.watchrss.R
import com.lightningstudio.watchrss.data.settings.CACHE_LIMIT_OPTIONS_MB
import com.lightningstudio.watchrss.ui.components.WatchSwitch
import com.lightningstudio.watchrss.ui.components.WatchSurface
import com.lightningstudio.watchrss.ui.testing.SettingsTestTags
import kotlinx.coroutines.flow.StateFlow

@Composable
fun SettingsScreen(
    cacheLimitMb: StateFlow<Long>,
    cacheUsageMb: StateFlow<Long>,
    readingThemeDark: StateFlow<Boolean>,
    shareUseSystem: StateFlow<Boolean>,
    readingFontSizeSp: StateFlow<Int>,
    phoneConnectionEnabled: StateFlow<Boolean>,
    showPerformanceTools: Boolean,
    onSelectCacheLimit: (Long) -> Unit,
    onToggleReadingTheme: () -> Unit,
    onToggleShareMode: () -> Unit,
    onSelectFontSize: (Int) -> Unit,
    onTogglePhoneConnection: () -> Unit,
    onOpenOobe: () -> Unit,
    onOpenPerfLargeList: () -> Unit,
    onOpenPerfLargeArticle: () -> Unit,
    onBeianClick: () -> Unit
) {
    val cacheLimit by cacheLimitMb.collectAsState()
    val usage by cacheUsageMb.collectAsState()
    val themeDark by readingThemeDark.collectAsState()
    val useSystemShare by shareUseSystem.collectAsState()
    val fontSizeSp by readingFontSizeSp.collectAsState()
    val phoneConnection by phoneConnectionEnabled.collectAsState()

    val cacheOptions = remember { CACHE_LIMIT_OPTIONS_MB }
    val fontOptions = remember { (12..32 step 2).toList() }

    val lowerCache = cacheOptions.lastOrNull { it < cacheLimit }
    val higherCache = cacheOptions.firstOrNull { it > cacheLimit }
    val lowerFont = fontOptions.lastOrNull { it < fontSizeSp }
    val higherFont = fontOptions.firstOrNull { it > fontSizeSp }

    val safePadding = dimensionResource(R.dimen.watch_safe_padding)
    val sectionSpacing = com.lightningstudio.watchrss.ui.theme.WatchDimens.hey_content_horizontal_distance
    val entrySpacing = com.lightningstudio.watchrss.ui.theme.WatchDimens.hey_distance_8dp
    val valueSpacing = com.lightningstudio.watchrss.ui.theme.WatchDimens.hey_distance_4dp
    val stepperSpacing = com.lightningstudio.watchrss.ui.theme.WatchDimens.hey_distance_6dp
    val stepperValueWidth = dimensionResource(R.dimen.watch_action_button_height)
    val valueIndent = com.lightningstudio.watchrss.ui.theme.WatchDimens.hey_content_horizontal_distance_6_0
    val pillHeight = com.lightningstudio.watchrss.ui.theme.WatchDimens.hey_multiple_item_height

    WatchSurface(pureBlack = true) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(safePadding)
                .testTag(SettingsTestTags.ROOT)
        ) {
            SettingsHeader()

            Spacer(modifier = Modifier.height(sectionSpacing))

            SettingsPillRow(label = "缓存上限") {
                RoundIconButtonIcon(
                    iconRes = R.drawable.ic_action_minus,
                    contentDescription = "减少缓存上限",
                    enabled = lowerCache != null,
                    testTag = SettingsTestTags.CACHE_DECREASE_BUTTON,
                    onClick = { lowerCache?.let(onSelectCacheLimit) }
                )
                Spacer(modifier = Modifier.width(stepperSpacing))
                StepperValue(
                    text = formatCacheSize(cacheLimit),
                    width = stepperValueWidth,
                    testTag = SettingsTestTags.CACHE_VALUE
                )
                Spacer(modifier = Modifier.width(stepperSpacing))
                RoundIconButtonIcon(
                    iconRes = R.drawable.ic_action_plus,
                    contentDescription = "增加缓存上限",
                    enabled = higherCache != null,
                    testTag = SettingsTestTags.CACHE_INCREASE_BUTTON,
                    onClick = { higherCache?.let(onSelectCacheLimit) }
                )
            }
            Text(
                text = "当前已用 ${formatCacheSize(usage)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = valueIndent, top = valueSpacing)
            )
            Text(
                text = "包含图片、预览和离线媒体；离线媒体不会自动清理",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = valueIndent, top = valueSpacing)
            )

            Spacer(modifier = Modifier.height(entrySpacing))

            SettingsPillRow(label = "阅读主题", endPaddingMultiplier = 1.5f) {
                WatchSwitch(
                    checked = themeDark,
                    modifier = Modifier.testTag(SettingsTestTags.THEME_SWITCH),
                    onCheckedChange = { onToggleReadingTheme() }
                )
            }
            Text(
                text = if (themeDark) "深色" else "浅色",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = valueIndent, top = valueSpacing)
            )

            Spacer(modifier = Modifier.height(entrySpacing))

            SettingsPillRow(label = "分享方式", endPaddingMultiplier = 1.5f) {
                WatchSwitch(
                    checked = useSystemShare,
                    modifier = Modifier.testTag(SettingsTestTags.SHARE_SWITCH),
                    onCheckedChange = { onToggleShareMode() }
                )
            }
            Text(
                text = if (useSystemShare) "系统分享" else "二维码",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = valueIndent, top = valueSpacing)
            )

            Spacer(modifier = Modifier.height(entrySpacing))

            SettingsPillRow(label = "字体大小") {
                RoundIconButtonIcon(
                    iconRes = R.drawable.ic_action_minus,
                    contentDescription = "减小字体",
                    enabled = lowerFont != null,
                    testTag = SettingsTestTags.FONT_DECREASE_BUTTON,
                    onClick = { lowerFont?.let(onSelectFontSize) }
                )
                Spacer(modifier = Modifier.width(stepperSpacing))
                StepperValue(
                    text = "${fontSizeSp}sp",
                    width = stepperValueWidth,
                    testTag = SettingsTestTags.FONT_VALUE
                )
                Spacer(modifier = Modifier.width(stepperSpacing))
                RoundIconButtonIcon(
                    iconRes = R.drawable.ic_action_plus,
                    contentDescription = "增大字体",
                    enabled = higherFont != null,
                    testTag = SettingsTestTags.FONT_INCREASE_BUTTON,
                    onClick = { higherFont?.let(onSelectFontSize) }
                )
            }
            Text(
                text = "正文阅读字号",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = valueIndent, top = valueSpacing)
            )

            Spacer(modifier = Modifier.height(entrySpacing))

            if (BuildConfig.DEBUG) {
                SettingsPillRow(
                    label = "新手引导",
                    testTag = SettingsTestTags.OPEN_OOBE_ENTRY,
                    onClick = onOpenOobe
                )
                Text(
                    text = "重新查看圆屏首启流程",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = valueIndent, top = valueSpacing)
                )

                Spacer(modifier = Modifier.height(entrySpacing))

                Text(
                    text = "开发者选项",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(start = valueIndent)
                )

                Spacer(modifier = Modifier.height(entrySpacing))

                SettingsPillRow(label = "手机互联", endPaddingMultiplier = 1.5f) {
                    WatchSwitch(
                        checked = phoneConnection,
                        modifier = Modifier.testTag(SettingsTestTags.PHONE_CONNECTION_SWITCH),
                        onCheckedChange = { onTogglePhoneConnection() }
                    )
                }
                Text(
                    text = "会在添加RSS页面和收藏及稍后再看页面显示有关手机互联的按钮",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = valueIndent, top = valueSpacing)
                )

                if (showPerformanceTools) {
                    Spacer(modifier = Modifier.height(entrySpacing))
                    Text(
                        text = "性能测试",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(start = valueIndent)
                    )
                    Spacer(modifier = Modifier.height(entrySpacing))
                    SettingsPillRow(label = "超大列表") {
                        RoundIconButton(
                            text = "进入",
                            enabled = true,
                            onClick = onOpenPerfLargeList
                        )
                    }
                    Spacer(modifier = Modifier.height(entrySpacing))
                    SettingsPillRow(label = "超大文章") {
                        RoundIconButton(
                            text = "进入",
                            enabled = true,
                            onClick = onOpenPerfLargeArticle
                        )
                    }
                }

                Spacer(modifier = Modifier.height(entrySpacing))
            }

            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "浙ICP备2024111886号-5A",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .testTag(SettingsTestTags.BEIAN_ENTRY)
                        .clickable(onClick = onBeianClick)
                )
            }

            Spacer(modifier = Modifier.height(pillHeight))
        }
    }
}

@Composable
private fun SettingsHeader() {
    val padding = com.lightningstudio.watchrss.ui.theme.WatchDimens.hey_distance_4dp

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(padding),
        horizontalArrangement = Arrangement.Center
    ) {
        Text(
            text = "设置",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun SettingsPillRow(
    label: String,
    testTag: String? = null,
    onClick: (() -> Unit)? = null,
    endPaddingMultiplier: Float = 1f,
    content: @Composable RowScope.() -> Unit = {}
) {
    val pillColor = colorResource(R.color.watch_pill_background)
    val pillRadius = com.lightningstudio.watchrss.ui.theme.WatchDimens.hey_button_default_radius
    val pillHeight = com.lightningstudio.watchrss.ui.theme.WatchDimens.hey_multiple_item_height
    val startPadding = com.lightningstudio.watchrss.ui.theme.WatchDimens.hey_content_horizontal_distance_6_0
    val endPadding = com.lightningstudio.watchrss.ui.theme.WatchDimens.hey_distance_10dp * endPaddingMultiplier
    val verticalPadding = com.lightningstudio.watchrss.ui.theme.WatchDimens.hey_distance_8dp

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(pillHeight)
            .clip(RoundedCornerShape(pillRadius))
            .background(pillColor)
            .then(testTag?.let(Modifier::testTag) ?: Modifier)
            .clickable(enabled = onClick != null, onClick = { onClick?.invoke() })
            .padding(
                start = startPadding,
                end = endPadding,
                top = verticalPadding,
                bottom = verticalPadding
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        content()
    }
}

@Composable
private fun StepperValue(
    text: String,
    width: androidx.compose.ui.unit.Dp,
    testTag: String? = null
) {
    Box(
        modifier = Modifier
            .width(width)
            .then(testTag?.let(Modifier::testTag) ?: Modifier),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun RoundIconButton(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val size = com.lightningstudio.watchrss.ui.theme.WatchDimens.hey_distance_20dp
    val baseColor = colorResource(R.color.watch_pill_background)
    val idleColor = lerp(baseColor, androidx.compose.ui.graphics.Color.White, 0.12f)
    val pressedColor = lerp(baseColor, androidx.compose.ui.graphics.Color.Black, 0.18f)
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val backgroundColor = if (isPressed && enabled) pressedColor else idleColor

    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(backgroundColor)
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun RoundIconButtonIcon(
    iconRes: Int,
    contentDescription: String,
    enabled: Boolean,
    testTag: String? = null,
    onClick: () -> Unit
) {
    val size = com.lightningstudio.watchrss.ui.theme.WatchDimens.hey_distance_20dp
    val iconSize = size * 0.6f
    val baseColor = colorResource(R.color.watch_pill_background)
    val idleColor = lerp(baseColor, androidx.compose.ui.graphics.Color.White, 0.12f)
    val pressedColor = lerp(baseColor, androidx.compose.ui.graphics.Color.Black, 0.18f)
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val backgroundColor = if (isPressed && enabled) pressedColor else idleColor

    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(backgroundColor)
            .then(testTag?.let(Modifier::testTag) ?: Modifier)
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.45f),
            modifier = Modifier.size(iconSize)
        )
    }
}

private fun formatCacheSize(valueMb: Long): String {
    if (valueMb < 1024L) return "${valueMb}M"
    val tenths = (valueMb * 10L) / 1024L
    val whole = tenths / 10L
    val fraction = tenths % 10L
    return if (fraction == 0L) {
        "${whole}G"
    } else {
        "${whole}.${fraction}G"
    }
}
