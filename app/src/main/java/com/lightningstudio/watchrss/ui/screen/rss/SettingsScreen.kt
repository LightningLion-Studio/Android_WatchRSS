package com.lightningstudio.watchrss.ui.screen.rss

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.LocalIndication
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Settings
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import com.lightningstudio.watchrss.ui.theme.watchColorResource
import com.lightningstudio.watchrss.ui.theme.watchDimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.annotation.DrawableRes
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import com.lightningstudio.watchrss.BuildConfig
import com.lightningstudio.watchrss.R
import com.lightningstudio.watchrss.data.settings.CACHE_LIMIT_OPTIONS_MB
import com.lightningstudio.watchrss.data.settings.formatMediaPlaybackStartVolumeLimitPercent
import com.lightningstudio.watchrss.data.settings.nextMediaPlaybackStartVolumeLimitPercent
import com.lightningstudio.watchrss.data.settings.previousMediaPlaybackStartVolumeLimitPercent
import com.lightningstudio.watchrss.data.settings.RssInlineImagePrefetchMode
import com.lightningstudio.watchrss.ui.components.WatchSwitch
import com.lightningstudio.watchrss.ui.components.WatchSurface
import com.lightningstudio.watchrss.ui.settings.WatchSettingsPillRow
import com.lightningstudio.watchrss.ui.input.InstallDigitalCrownScrollHandler
import com.lightningstudio.watchrss.ui.settings.MainSettingsCatalog
import com.lightningstudio.watchrss.ui.testing.SettingsTestTags
import com.lightningstudio.watchrss.ui.util.isSystemShareSettingSupported
import com.lightningstudio.watchrss.ui.util.showAppToast
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
    mediaVolumeControlEnabled: StateFlow<Boolean>,
    mediaVolumeGuardEnabled: StateFlow<Boolean>,
    mediaPlaybackStartVolumeLimitPercent: StateFlow<Int?>,
    rssInlineImagePrefetchMode: StateFlow<RssInlineImagePrefetchMode>,
    llmEnabledFlow: StateFlow<Boolean>,
    llmAutoSummarize: StateFlow<Boolean>,
    llmShowTokenUsage: StateFlow<Boolean>,
    llmPromptPreset: StateFlow<Int>,
    showPerformanceTools: Boolean,
    onSelectCacheLimit: (Long) -> Unit,
    onToggleReadingTheme: () -> Unit,
    onToggleShareMode: () -> Unit,
    onSelectFontSize: (Int) -> Unit,
    onToggleMediaVolumeControl: () -> Unit,
    onToggleMediaVolumeGuard: () -> Unit,
    onSelectMediaPlaybackStartVolumeLimit: (Int?) -> Unit,
    onSelectRssInlineImagePrefetchMode: (RssInlineImagePrefetchMode) -> Unit,
    onToggleLlmEnabled: () -> Unit,
    onToggleLlmAutoSummarize: () -> Unit,
    onToggleLlmShowTokenUsage: () -> Unit,
    onOpenOobe: () -> Unit,
    onOpenPerfLargeList: () -> Unit,
    onOpenPerfLargeArticle: () -> Unit,
    onOpenDouyinCookieInput: () -> Unit,
    onOpenLlmConnectivity: () -> Unit,
    onOpenLlmPromptPreset: () -> Unit,
    onOpenLlmTokenUsage: () -> Unit,
    onBeianClick: () -> Unit,
    onOpenAdvanced: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val cacheLimit by cacheLimitMb.collectAsState()
    val usage by cacheUsageMb.collectAsState()
    val themeDark by readingThemeDark.collectAsState()
    val useSystemShare by shareUseSystem.collectAsState()
    val fontSizeSp by readingFontSizeSp.collectAsState()
    val mediaVolumeControl by mediaVolumeControlEnabled.collectAsState()
    val mediaVolumeGuard by mediaVolumeGuardEnabled.collectAsState()
    val mediaPlaybackStartVolumeLimit by mediaPlaybackStartVolumeLimitPercent.collectAsState()
    val imagePrefetchMode by rssInlineImagePrefetchMode.collectAsState()
    val llmEnabled by llmEnabledFlow.collectAsState()
    val llmAuto by llmAutoSummarize.collectAsState()
    val llmTokenUsage by llmShowTokenUsage.collectAsState()
    val llmPreset by llmPromptPreset.collectAsState()
    val showSystemShareSetting = remember(context) {
        isSystemShareSettingSupported(context)
    }
    var currentPage by rememberSaveable { mutableStateOf(SettingsPage.Main) }

    BackHandler(enabled = currentPage == SettingsPage.Advanced) {
        currentPage = SettingsPage.Main
    }

    when (currentPage) {
        SettingsPage.Main -> MainSettingsPage(
            readingThemeDark = themeDark,
            readingFontSizeSp = fontSizeSp,
            mediaVolumeControlEnabled = mediaVolumeControl,
            mediaVolumeGuardEnabled = mediaVolumeGuard,
            mediaPlaybackStartVolumeLimitPercent = mediaPlaybackStartVolumeLimit,
            llmEnabled = llmEnabled,
            llmAutoSummarize = llmAuto,
            llmShowTokenUsage = llmTokenUsage,
            llmPromptPreset = llmPreset,
            showPerformanceTools = showPerformanceTools,
            onToggleReadingTheme = onToggleReadingTheme,
            onSelectFontSize = onSelectFontSize,
            onToggleMediaVolumeControl = onToggleMediaVolumeControl,
            onToggleMediaVolumeGuard = onToggleMediaVolumeGuard,
            onSelectMediaPlaybackStartVolumeLimit = onSelectMediaPlaybackStartVolumeLimit,
            onToggleLlmEnabled = onToggleLlmEnabled,
            onToggleLlmAutoSummarize = onToggleLlmAutoSummarize,
            onToggleLlmShowTokenUsage = onToggleLlmShowTokenUsage,
            onOpenAdvanced = {
                if (onOpenAdvanced != null) {
                    onOpenAdvanced()
                } else {
                    currentPage = SettingsPage.Advanced
                }
            },
            onOpenOobe = onOpenOobe,
            onOpenPerfLargeList = onOpenPerfLargeList,
            onOpenPerfLargeArticle = onOpenPerfLargeArticle,
            onOpenDouyinCookieInput = onOpenDouyinCookieInput,
            onOpenLlmConnectivity = onOpenLlmConnectivity,
            onOpenLlmPromptPreset = onOpenLlmPromptPreset,
            onOpenLlmTokenUsage = onOpenLlmTokenUsage,
            onBeianClick = onBeianClick
        )
        SettingsPage.Advanced -> AdvancedSettingsPage(
            cacheLimit = cacheLimit,
            cacheUsage = usage,
            shareUseSystem = useSystemShare,
            rssInlineImagePrefetchMode = imagePrefetchMode,
            showSystemShareSetting = showSystemShareSetting,
            onSelectCacheLimit = onSelectCacheLimit,
            onToggleShareMode = onToggleShareMode,
            onSelectRssInlineImagePrefetchMode = onSelectRssInlineImagePrefetchMode
        )
    }
}

@Composable
fun AdvancedSettingsScreen(
    cacheLimitMb: StateFlow<Long>,
    cacheUsageMb: StateFlow<Long>,
    shareUseSystem: StateFlow<Boolean>,
    rssInlineImagePrefetchMode: StateFlow<RssInlineImagePrefetchMode>,
    onSelectCacheLimit: (Long) -> Unit,
    onToggleShareMode: () -> Unit,
    onSelectRssInlineImagePrefetchMode: (RssInlineImagePrefetchMode) -> Unit
) {
    val context = LocalContext.current
    val cacheLimit by cacheLimitMb.collectAsState()
    val usage by cacheUsageMb.collectAsState()
    val useSystemShare by shareUseSystem.collectAsState()
    val imagePrefetchMode by rssInlineImagePrefetchMode.collectAsState()
    val showSystemShareSetting = remember(context) {
        isSystemShareSettingSupported(context)
    }

    AdvancedSettingsPage(
        cacheLimit = cacheLimit,
        cacheUsage = usage,
        shareUseSystem = useSystemShare,
        rssInlineImagePrefetchMode = imagePrefetchMode,
        showSystemShareSetting = showSystemShareSetting,
        onSelectCacheLimit = onSelectCacheLimit,
        onToggleShareMode = onToggleShareMode,
        onSelectRssInlineImagePrefetchMode = onSelectRssInlineImagePrefetchMode
    )
}

@Composable
private fun MainSettingsPage(
    readingThemeDark: Boolean,
    readingFontSizeSp: Int,
    mediaVolumeControlEnabled: Boolean,
    mediaVolumeGuardEnabled: Boolean,
    mediaPlaybackStartVolumeLimitPercent: Int?,
    llmEnabled: Boolean,
    llmAutoSummarize: Boolean,
    llmShowTokenUsage: Boolean,
    llmPromptPreset: Int,
    showPerformanceTools: Boolean,
    onToggleReadingTheme: () -> Unit,
    onSelectFontSize: (Int) -> Unit,
    onToggleMediaVolumeControl: () -> Unit,
    onToggleMediaVolumeGuard: () -> Unit,
    onSelectMediaPlaybackStartVolumeLimit: (Int?) -> Unit,
    onToggleLlmEnabled: () -> Unit,
    onToggleLlmAutoSummarize: () -> Unit,
    onToggleLlmShowTokenUsage: () -> Unit,
    onOpenAdvanced: () -> Unit,
    onOpenOobe: () -> Unit,
    onOpenPerfLargeList: () -> Unit,
    onOpenPerfLargeArticle: () -> Unit,
    onOpenDouyinCookieInput: () -> Unit,
    onOpenLlmConnectivity: () -> Unit,
    onOpenLlmPromptPreset: () -> Unit,
    onOpenLlmTokenUsage: () -> Unit,
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
    val compactStepperSpacing = com.lightningstudio.watchrss.ui.theme.WatchDimens.hey_distance_4dp
    val stepperValueWidth = watchDimensionResource(R.dimen.watch_action_button_height)
    val playbackStartVolumeValueWidth = stepperValueWidth + compactStepperSpacing
    val valueIndent = com.lightningstudio.watchrss.ui.theme.WatchDimens.hey_distance_10dp
    val pillHeight = com.lightningstudio.watchrss.ui.theme.WatchDimens.hey_multiple_item_height
    val scrollState = rememberScrollState()
    val context = LocalContext.current
    val readingThemeInfo = remember { MainSettingsCatalog.readingTheme }
    val fontSizeInfo = remember { MainSettingsCatalog.fontSize }
    val mediaVolumeControlInfo = remember { MainSettingsCatalog.mediaVolumeControl }
    val mediaVolumeGuardInfo = remember { MainSettingsCatalog.mediaVolumeGuard }
    val mediaPlaybackStartVolumeLimitInfo = remember { MainSettingsCatalog.mediaPlaybackStartVolumeLimit }
    val mediaVolumeGuardUnavailableMessage = "仅在启用滚轮调节音量后可使用音量调节防干扰"
    val showVolumeGuardUnavailableToast = {
        showAppToast(
            context,
            mediaVolumeGuardUnavailableMessage,
            Toast.LENGTH_SHORT
        )
    }

    InstallDigitalCrownScrollHandler(scrollState)

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

            WatchSettingsPillRow(label = readingThemeInfo.title, endPaddingMultiplier = 1.5f) {
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

            WatchSettingsPillRow(label = fontSizeInfo.title) {
                RoundIconButtonIcon(
                    icon = Icons.Outlined.Remove,
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
                    icon = Icons.Outlined.Add,
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

            WatchSettingsPillRow(label = mediaVolumeControlInfo.title, endPaddingMultiplier = 1.5f) {
                WatchSwitch(
                    checked = mediaVolumeControlEnabled,
                    modifier = Modifier.testTag(SettingsTestTags.MEDIA_VOLUME_CONTROL_SWITCH),
                    onCheckedChange = { onToggleMediaVolumeControl() }
                )
            }
            Text(
                text = mediaVolumeControlInfo.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = valueIndent, top = valueSpacing)
            )

            Spacer(modifier = Modifier.height(entrySpacing))

            WatchSettingsPillRow(
                label = mediaVolumeGuardInfo.title,
                testTag = SettingsTestTags.MEDIA_VOLUME_GUARD_ROW,
                onClick = if (mediaVolumeControlEnabled) {
                    null
                } else {
                    showVolumeGuardUnavailableToast
                },
                endPaddingMultiplier = 1.5f
            ) {
                if (mediaVolumeControlEnabled) {
                    WatchSwitch(
                        checked = mediaVolumeGuardEnabled,
                        modifier = Modifier.testTag(SettingsTestTags.MEDIA_VOLUME_GUARD_SWITCH),
                        onCheckedChange = { onToggleMediaVolumeGuard() }
                    )
                } else {
                    WatchSwitch(
                        checked = mediaVolumeGuardEnabled,
                        modifier = Modifier
                            .testTag(SettingsTestTags.MEDIA_VOLUME_GUARD_SWITCH)
                            .alpha(0.5f),
                        onCheckedChange = { showVolumeGuardUnavailableToast() }
                    )
                }
            }
            Text(
                text = if (!mediaVolumeControlEnabled) {
                    "已随滚轮音量调节停用"
                } else if (mediaVolumeGuardEnabled) {
                    "已开启"
                } else {
                    "已关闭"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = valueIndent, top = valueSpacing)
            )
            Text(
                text = "滚轮连续上调时会先停在约21%，停一下再滚可继续升高",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = valueIndent, top = valueSpacing)
            )

            Spacer(modifier = Modifier.height(entrySpacing))

            WatchSettingsPillRow(label = mediaPlaybackStartVolumeLimitInfo.title) {
                RoundIconButtonIcon(
                    icon = Icons.Outlined.Remove,
                    contentDescription = "降低静音开播上限",
                    enabled = true,
                    testTag = SettingsTestTags.PLAYBACK_START_VOLUME_DECREASE_BUTTON,
                    onClick = {
                        onSelectMediaPlaybackStartVolumeLimit(
                            previousMediaPlaybackStartVolumeLimitPercent(
                                mediaPlaybackStartVolumeLimitPercent
                            )
                        )
                    }
                )
                Spacer(modifier = Modifier.width(compactStepperSpacing))
                StepperValue(
                    text = formatMediaPlaybackStartVolumeLimitPercent(
                        mediaPlaybackStartVolumeLimitPercent
                    ),
                    width = playbackStartVolumeValueWidth,
                    testTag = SettingsTestTags.PLAYBACK_START_VOLUME_VALUE
                )
                Spacer(modifier = Modifier.width(compactStepperSpacing))
                RoundIconButtonIcon(
                    icon = Icons.Outlined.Add,
                    contentDescription = "提高静音开播上限",
                    enabled = true,
                    testTag = SettingsTestTags.PLAYBACK_START_VOLUME_INCREASE_BUTTON,
                    onClick = {
                        onSelectMediaPlaybackStartVolumeLimit(
                            nextMediaPlaybackStartVolumeLimitPercent(
                                mediaPlaybackStartVolumeLimitPercent
                            )
                        )
                    }
                )
            }
            Text(
                text = mediaPlaybackStartVolumeLimitInfo.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = valueIndent, top = valueSpacing)
            )
            Text(
                text = if (mediaPlaybackStartVolumeLimitPercent == null) {
                    "当前不限制开播音量"
                } else {
                    "开播时高于 ${mediaPlaybackStartVolumeLimitPercent}% 才压低"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = valueIndent, top = valueSpacing)
            )

            Spacer(modifier = Modifier.height(entrySpacing))

            WatchSettingsPillRow(
                label = "高级",
                leadingIcon = Icons.Outlined.Settings,
                testTag = SettingsTestTags.ADVANCED_ENTRY,
                onClick = onOpenAdvanced
            )
            Text(
                text = "管理高级选项",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = valueIndent, top = valueSpacing)
            )

            Spacer(modifier = Modifier.height(entrySpacing))

            if (BuildConfig.DEBUG) {
                WatchSettingsPillRow(
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

                WatchSettingsPillRow(
                    label = "抖音登录 Cookie",
                    testTag = SettingsTestTags.DOUYIN_COOKIE_ENTRY,
                    onClick = onOpenDouyinCookieInput
                )
                Text(
                    text = "手动输入或替换抖音登录请求头 Cookie",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = valueIndent, top = valueSpacing)
                )

                Spacer(modifier = Modifier.height(entrySpacing))

                // AI 总结功能
                WatchSettingsPillRow(label = "AI 总结功能", endPaddingMultiplier = 1.5f) {
                    WatchSwitch(
                        checked = llmEnabled,
                        onCheckedChange = { onToggleLlmEnabled() }
                    )
                }
                Text(
                    text = "在 RSS 文章详情页显示 AI 总结按钮",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = valueIndent, top = valueSpacing)
                )

                Spacer(modifier = Modifier.height(entrySpacing))
                WatchSettingsPillRow(
                    label = "AI 连通性",
                    onClick = onOpenLlmConnectivity
                )
                Text(
                    text = "配置大模型服务商与 API Key",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = valueIndent, top = valueSpacing)
                )

                if (llmEnabled) {
                    Spacer(modifier = Modifier.height(entrySpacing))
                    WatchSettingsPillRow(label = "进入详情自动开始总结", endPaddingMultiplier = 1.5f) {
                        WatchSwitch(
                            checked = llmAutoSummarize,
                            onCheckedChange = { onToggleLlmAutoSummarize() }
                        )
                    }
                    Text(
                        text = if (llmAutoSummarize) "进入文章时自动在顶部显示总结" else "详情页底部显示总结按钮，手动触发",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = valueIndent, top = valueSpacing)
                    )

                    Spacer(modifier = Modifier.height(entrySpacing))
                    val presetLabel = com.lightningstudio.watchrss.ui.viewmodel.LlmPromptPresets.all
                        .getOrNull(llmPromptPreset - 1)?.label
                        ?: com.lightningstudio.watchrss.ui.viewmodel.LlmPromptPresets.all.first().label
                    WatchSettingsPillRow(label = "提示词预设", onClick = onOpenLlmPromptPreset) {
                        Text(
                            text = presetLabel,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = "选择 AI 总结时使用的提示词",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = valueIndent, top = valueSpacing)
                    )

                    Spacer(modifier = Modifier.height(entrySpacing))
                    WatchSettingsPillRow(label = "显示 Token 用量", endPaddingMultiplier = 1.5f) {
                        WatchSwitch(
                            checked = llmShowTokenUsage,
                            onCheckedChange = { onToggleLlmShowTokenUsage() }
                        )
                    }
                    Text(
                        text = "在总结页面显示本次请求消耗的 token 数量",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = valueIndent, top = valueSpacing)
                    )
                    Spacer(modifier = Modifier.height(entrySpacing))
                    WatchSettingsPillRow(label = "Token 消耗统计", onClick = onOpenLlmTokenUsage) {
                        RoundIconButton(
                            text = "进入",
                            enabled = true,
                            onClick = onOpenLlmTokenUsage
                        )
                    }
                    Text(
                        text = "查看 LLM 调用历史与累计 token 消耗",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = valueIndent, top = valueSpacing)
                    )
                }

                if (showPerformanceTools) {
                    Spacer(modifier = Modifier.height(entrySpacing))
                    Text(
                        text = "性能测试",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(start = valueIndent)
                    )
                    Spacer(modifier = Modifier.height(entrySpacing))
                    WatchSettingsPillRow(label = "超大列表") {
                        RoundIconButton(
                            text = "进入",
                            enabled = true,
                            onClick = onOpenPerfLargeList
                        )
                    }
                    Spacer(modifier = Modifier.height(entrySpacing))
                    WatchSettingsPillRow(label = "超大文章") {
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
    rssInlineImagePrefetchMode: RssInlineImagePrefetchMode,
    showSystemShareSetting: Boolean,
    onSelectCacheLimit: (Long) -> Unit,
    onToggleShareMode: () -> Unit,
    onSelectRssInlineImagePrefetchMode: (RssInlineImagePrefetchMode) -> Unit
) {
    val cacheOptions = remember { CACHE_LIMIT_OPTIONS_MB }
    val imagePrefetchOptions = remember { RssInlineImagePrefetchMode.values().toList() }
    val lowerCache = cacheOptions.lastOrNull { it < cacheLimit }
    val higherCache = cacheOptions.firstOrNull { it > cacheLimit }
    val imagePrefetchIndex = imagePrefetchOptions.indexOf(rssInlineImagePrefetchMode)
    val lowerImagePrefetchMode = imagePrefetchOptions.getOrNull(imagePrefetchIndex - 1)
    val higherImagePrefetchMode = imagePrefetchOptions.getOrNull(imagePrefetchIndex + 1)
    val safePadding = watchDimensionResource(R.dimen.watch_safe_padding)
    val sectionSpacing = com.lightningstudio.watchrss.ui.theme.WatchDimens.hey_content_horizontal_distance
    val entrySpacing = com.lightningstudio.watchrss.ui.theme.WatchDimens.hey_distance_8dp
    val valueSpacing = com.lightningstudio.watchrss.ui.theme.WatchDimens.hey_distance_4dp
    val stepperSpacing = com.lightningstudio.watchrss.ui.theme.WatchDimens.hey_distance_6dp
    val stepperValueWidth = watchDimensionResource(R.dimen.watch_action_button_height)
    val textStepperValueWidth = stepperValueWidth
    val valueIndent = com.lightningstudio.watchrss.ui.theme.WatchDimens.hey_distance_10dp
    val pillHeight = com.lightningstudio.watchrss.ui.theme.WatchDimens.hey_multiple_item_height
    val scrollState = rememberScrollState()

    InstallDigitalCrownScrollHandler(scrollState)

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

            WatchSettingsPillRow(label = "缓存上限") {
                RoundIconButtonIcon(
                    icon = Icons.Outlined.Remove,
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
                    icon = Icons.Outlined.Add,
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

            WatchSettingsPillRow(label = "媒体缓存") {
                RoundIconButtonIcon(
                    icon = Icons.Outlined.Remove,
                    contentDescription = "减少媒体缓存",
                    enabled = lowerImagePrefetchMode != null,
                    testTag = SettingsTestTags.IMAGE_PREFETCH_DECREASE_BUTTON,
                    onClick = { lowerImagePrefetchMode?.let(onSelectRssInlineImagePrefetchMode) }
                )
                Spacer(modifier = Modifier.width(stepperSpacing))
                StepperValue(
                    text = rssInlineImagePrefetchMode.label,
                    width = textStepperValueWidth,
                    testTag = SettingsTestTags.IMAGE_PREFETCH_VALUE
                )
                Spacer(modifier = Modifier.width(stepperSpacing))
                RoundIconButtonIcon(
                    icon = Icons.Outlined.Add,
                    contentDescription = "增加媒体缓存",
                    enabled = higherImagePrefetchMode != null,
                    testTag = SettingsTestTags.IMAGE_PREFETCH_INCREASE_BUTTON,
                    onClick = { higherImagePrefetchMode?.let(onSelectRssInlineImagePrefetchMode) }
                )
            }
            Text(
                text = rssInlineImagePrefetchMode.summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = valueIndent, top = valueSpacing)
            )
            Text(
                text = "用于原文媒体内容；默认推荐保留前 4 项",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = valueIndent, top = valueSpacing)
            )

            Spacer(modifier = Modifier.height(entrySpacing))

            if (showSystemShareSetting) {
                WatchSettingsPillRow(label = "使用系统分享方式", endPaddingMultiplier = 1.5f) {
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
            }

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
internal fun SettingsHeader(title: String) {
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
                indication = LocalIndication.current,
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
    icon: androidx.compose.ui.graphics.vector.ImageVector,
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
                indication = LocalIndication.current,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
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
