package com.lightningstudio.watchrss.ui.screen.rss

import androidx.annotation.DrawableRes
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.testTag
import com.lightningstudio.watchrss.ui.theme.watchColorResource
import com.lightningstudio.watchrss.ui.theme.watchDimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import com.lightningstudio.watchrss.BuildConfig
import com.lightningstudio.watchrss.R
import com.lightningstudio.watchrss.data.settings.CACHE_LIMIT_OPTIONS_MB
import com.lightningstudio.watchrss.ui.components.WatchSwitch
import com.lightningstudio.watchrss.ui.components.WatchSurface
import com.lightningstudio.watchrss.ui.input.InstallRotaryScrollHandler
import com.lightningstudio.watchrss.ui.settings.MainSettingsCatalog
import com.lightningstudio.watchrss.ui.testing.SettingsTestTags
import kotlinx.coroutines.flow.StateFlow

private enum class SettingsPage {
    Main,
    Advanced
}

@Composable
fun SettingsScreen(
    cacheLimitMb: StateFlow<Long>,
    cacheUsageMb: StateFlow<Long>,
    readingThemeDark: StateFlow<Boolean>,
    shareUseSystem: StateFlow<Boolean>,
    readingFontSizeSp: StateFlow<Int>,
    phoneConnectionEnabled: StateFlow<Boolean>,
    mediaVolumeGuardEnabled: StateFlow<Boolean>,
    showPerformanceTools: Boolean,
    onSelectCacheLimit: (Long) -> Unit,
    onToggleReadingTheme: () -> Unit,
    onToggleShareMode: () -> Unit,
    onSelectFontSize: (Int) -> Unit,
    onTogglePhoneConnection: () -> Unit,
    onToggleMediaVolumeGuard: () -> Unit,
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
    val mediaVolumeGuard by mediaVolumeGuardEnabled.collectAsState()
    var currentPage by rememberSaveable { mutableStateOf(SettingsPage.Main) }

    BackHandler(enabled = currentPage == SettingsPage.Advanced) {
        currentPage = SettingsPage.Main
    }

    when (currentPage) {
        SettingsPage.Main -> MainSettingsPage(
            readingThemeDark = themeDark,
            readingFontSizeSp = fontSizeSp,
            phoneConnectionEnabled = phoneConnection,
            mediaVolumeGuardEnabled = mediaVolumeGuard,
            showPerformanceTools = showPerformanceTools,
            onToggleReadingTheme = onToggleReadingTheme,
            onSelectFontSize = onSelectFontSize,
            onTogglePhoneConnection = onTogglePhoneConnection,
            onToggleMediaVolumeGuard = onToggleMediaVolumeGuard,
            onOpenAdvanced = { currentPage = SettingsPage.Advanced },
            onOpenOobe = onOpenOobe,
            onOpenPerfLargeList = onOpenPerfLargeList,
            onOpenPerfLargeArticle = onOpenPerfLargeArticle,
            onBeianClick = onBeianClick
        )
        SettingsPage.Advanced -> AdvancedSettingsPage(
            cacheLimit = cacheLimit,
            cacheUsage = usage,
            shareUseSystem = useSystemShare,
            onSelectCacheLimit = onSelectCacheLimit,
            onToggleShareMode = onToggleShareMode
        )
    }
}

@Composable
private fun MainSettingsPage(
    readingThemeDark: Boolean,
    readingFontSizeSp: Int,
    phoneConnectionEnabled: Boolean,
    mediaVolumeGuardEnabled: Boolean,
    showPerformanceTools: Boolean,
    onToggleReadingTheme: () -> Unit,
    onSelectFontSize: (Int) -> Unit,
    onTogglePhoneConnection: () -> Unit,
    onToggleMediaVolumeGuard: () -> Unit,
    onOpenAdvanced: () -> Unit,
    onOpenOobe: () -> Unit,
    onOpenPerfLargeList: () -> Unit,
    onOpenPerfLargeArticle: () -> Unit,
    onBeianClick: () -> Unit
) {
    val fontOptions = remember { (12..32 step 2).toList() }
    val lowerFont = fontOptions.lastOrNull { it < readingFontSizeSp }
    val higherFont = fontOptions.firstOrNull { it > readingFontSizeSp }
    val safePadding = watchDimensionResource(R.dimen.watch_safe_padding)
    val sectionSpacing = com.lightningstudio.watchrss.ui.theme.WatchDimens.hey_content_horizontal_distance
    val entrySpacing = com.lightningstudio.watchrss.ui.theme.WatchDimens.hey_distance_8dp
    val valueSpacing = com.lightningstudio.watchrss.ui.theme.WatchDimens.hey_distance_4dp
    val stepperSpacing = com.lightningstudio.watchrss.ui.theme.WatchDimens.hey_distance_6dp
    val stepperValueWidth = watchDimensionResource(R.dimen.watch_action_button_height)
    val valueIndent = com.lightningstudio.watchrss.ui.theme.WatchDimens.hey_distance_10dp
    val pillHeight = com.lightningstudio.watchrss.ui.theme.WatchDimens.hey_multiple_item_height
    val scrollState = rememberScrollState()
    val readingThemeInfo = remember { MainSettingsCatalog.readingTheme }
    val fontSizeInfo = remember { MainSettingsCatalog.fontSize }
    val mediaVolumeGuardInfo = remember { MainSettingsCatalog.mediaVolumeGuard }

    InstallRotaryScrollHandler(scrollState)

    WatchSurface(pureBlack = true) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(safePadding)
                .testTag(SettingsTestTags.ROOT)
        ) {
            SettingsHeader(title = "设置")

            Spacer(modifier = Modifier.height(sectionSpacing))

            SettingsPillRow(label = readingThemeInfo.title, endPaddingMultiplier = 1.5f) {
                ReadingThemeToggle(
                    isDark = readingThemeDark,
                    modifier = Modifier.testTag(SettingsTestTags.THEME_SWITCH),
                    onToggle = onToggleReadingTheme
                )
            }
            Text(
                text = if (readingThemeDark) "深色" else "浅色",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = valueIndent, top = valueSpacing)
            )
            Text(
                text = readingThemeInfo.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = valueIndent, top = valueSpacing)
            )

            Spacer(modifier = Modifier.height(entrySpacing))

            SettingsPillRow(label = fontSizeInfo.title) {
                RoundIconButtonIcon(
                    iconRes = R.drawable.ic_action_minus,
                    contentDescription = "减小字体",
                    enabled = lowerFont != null,
                    testTag = SettingsTestTags.FONT_DECREASE_BUTTON,
                    onClick = { lowerFont?.let(onSelectFontSize) }
                )
                Spacer(modifier = Modifier.width(stepperSpacing))
                StepperValue(
                    text = "${readingFontSizeSp}sp",
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
                text = fontSizeInfo.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = valueIndent, top = valueSpacing)
            )

            Spacer(modifier = Modifier.height(entrySpacing))

            SettingsPillRow(label = mediaVolumeGuardInfo.title, endPaddingMultiplier = 1.5f) {
                WatchSwitch(
                    checked = mediaVolumeGuardEnabled,
                    modifier = Modifier.testTag(SettingsTestTags.MEDIA_VOLUME_GUARD_SWITCH),
                    onCheckedChange = { onToggleMediaVolumeGuard() }
                )
            }
            Text(
                text = if (mediaVolumeGuardEnabled) {
                    "播放前若音量超过15%，会先压到约7%"
                } else {
                    "关闭后播放时不再自动压低音量"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = valueIndent, top = valueSpacing)
            )
            Text(
                text = mediaVolumeGuardInfo.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = valueIndent, top = valueSpacing)
            )
            Text(
                text = "滚轮连续上调时会先停在约16%，停一下再滚可继续升高",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = valueIndent, top = valueSpacing)
            )

            Spacer(modifier = Modifier.height(entrySpacing))

            SettingsPillRow(
                label = "高级",
                leadingIconRes = R.drawable.ic_settings,
                testTag = SettingsTestTags.ADVANCED_ENTRY,
                onClick = onOpenAdvanced
            )
            Text(
                text = "管理分享方式和缓存上限",
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
                        checked = phoneConnectionEnabled,
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
private fun AdvancedSettingsPage(
    cacheLimit: Long,
    cacheUsage: Long,
    shareUseSystem: Boolean,
    onSelectCacheLimit: (Long) -> Unit,
    onToggleShareMode: () -> Unit
) {
    val cacheOptions = remember { CACHE_LIMIT_OPTIONS_MB }
    val lowerCache = cacheOptions.lastOrNull { it < cacheLimit }
    val higherCache = cacheOptions.firstOrNull { it > cacheLimit }
    val safePadding = watchDimensionResource(R.dimen.watch_safe_padding)
    val sectionSpacing = com.lightningstudio.watchrss.ui.theme.WatchDimens.hey_content_horizontal_distance
    val entrySpacing = com.lightningstudio.watchrss.ui.theme.WatchDimens.hey_distance_8dp
    val valueSpacing = com.lightningstudio.watchrss.ui.theme.WatchDimens.hey_distance_4dp
    val stepperSpacing = com.lightningstudio.watchrss.ui.theme.WatchDimens.hey_distance_6dp
    val stepperValueWidth = watchDimensionResource(R.dimen.watch_action_button_height)
    val valueIndent = com.lightningstudio.watchrss.ui.theme.WatchDimens.hey_distance_10dp
    val pillHeight = com.lightningstudio.watchrss.ui.theme.WatchDimens.hey_multiple_item_height
    val scrollState = rememberScrollState()

    InstallRotaryScrollHandler(scrollState)

    WatchSurface(pureBlack = true) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(safePadding)
                .testTag(SettingsTestTags.ROOT)
        ) {
            SettingsHeader(title = "高级")

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
                text = "当前已用 ${formatCacheSize(cacheUsage)}",
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

            SettingsPillRow(label = "使用系统分享方式", endPaddingMultiplier = 1.5f) {
                WatchSwitch(
                    checked = shareUseSystem,
                    modifier = Modifier.testTag(SettingsTestTags.SHARE_SWITCH),
                    onCheckedChange = { onToggleShareMode() }
                )
            }
            Text(
                text = if (shareUseSystem) "当前：系统分享" else "当前：二维码分享",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = valueIndent, top = valueSpacing)
            )
            Text(
                text = "开启后直接调用系统分享面板；关闭后显示二维码供扫码分享",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = valueIndent, top = valueSpacing)
            )
            Text(
                text = "便于配合其他第三方应用使用",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = valueIndent, top = valueSpacing)
            )

            Spacer(modifier = Modifier.height(pillHeight))
        }
    }
}

@Composable
private fun ReadingThemeToggle(
    isDark: Boolean,
    modifier: Modifier = Modifier,
    onToggle: () -> Unit
) {
    val outerSize = com.lightningstudio.watchrss.ui.theme.WatchDimens.hey_distance_20dp
    val innerInset = com.lightningstudio.watchrss.ui.theme.WatchDimens.hey_distance_2dp
    val innerSize = outerSize - (innerInset * 2f)
    val orange = watchColorResource(R.color.brand_orange)
    val fillColor = if (isDark) androidx.compose.ui.graphics.Color.Black else androidx.compose.ui.graphics.Color.White
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val borderColor = if (isPressed) lerp(orange, androidx.compose.ui.graphics.Color.White, 0.12f) else orange

    Box(
        modifier = modifier
            .size(outerSize)
            .semantics {
                contentDescription = "阅读主题"
                stateDescription = if (isDark) "深色" else "浅色"
            }
            .clip(CircleShape)
            .background(borderColor)
            .toggleable(
                value = isDark,
                role = Role.Switch,
                interactionSource = interactionSource,
                indication = null,
                onValueChange = { onToggle() }
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(innerSize)
                .clip(CircleShape)
                .background(fillColor)
        )
    }
}

@Composable
private fun SettingsHeader(title: String) {
    val padding = com.lightningstudio.watchrss.ui.theme.WatchDimens.hey_distance_4dp

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(padding),
        horizontalArrangement = Arrangement.Center
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun SettingsPillRow(
    label: String,
    @DrawableRes leadingIconRes: Int? = null,
    testTag: String? = null,
    onClick: (() -> Unit)? = null,
    endPaddingMultiplier: Float = 1f,
    content: @Composable RowScope.() -> Unit = {}
) {
    val pillColor = watchColorResource(R.color.watch_pill_background)
    val pillRadius = com.lightningstudio.watchrss.ui.theme.WatchDimens.hey_button_default_radius
    val pillHeight = com.lightningstudio.watchrss.ui.theme.WatchDimens.hey_multiple_item_height
    val startPadding = com.lightningstudio.watchrss.ui.theme.WatchDimens.hey_distance_10dp
    val endPadding = com.lightningstudio.watchrss.ui.theme.WatchDimens.hey_distance_10dp * endPaddingMultiplier
    val verticalPadding = com.lightningstudio.watchrss.ui.theme.WatchDimens.hey_distance_8dp
    val iconSize = watchDimensionResource(R.dimen.hey_listitem_lefticon_height_width)
    val iconSpacing = com.lightningstudio.watchrss.ui.theme.WatchDimens.hey_distance_8dp

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
        if (leadingIconRes != null) {
            Icon(
                painter = painterResource(id = leadingIconRes),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(iconSize)
            )
            Spacer(modifier = Modifier.width(iconSpacing))
        }
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
    val baseColor = watchColorResource(R.color.watch_pill_background)
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
    val baseColor = watchColorResource(R.color.watch_pill_background)
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
