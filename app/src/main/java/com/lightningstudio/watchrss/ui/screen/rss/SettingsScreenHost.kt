package com.lightningstudio.watchrss.ui.screen.rss

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.lightningstudio.watchrss.data.douyin.DouyinRepositoryContract
import com.lightningstudio.watchrss.data.rss.BuiltinChannelType
import com.lightningstudio.watchrss.data.rss.RssRepository
import com.lightningstudio.watchrss.ui.screen.douyin.DouyinCookieInputDialog
import com.lightningstudio.watchrss.ui.viewmodel.SettingsViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Composable
fun SettingsScreenHost(
    viewModel: SettingsViewModel,
    douyinRepository: DouyinRepositoryContract,
    rssRepository: RssRepository,
    showPerformanceTools: Boolean,
    showDetailedTtsConfiguration: Boolean,
    hasPaidAiAccess: Boolean,
    onOpenAdvanced: () -> Unit,
    onOpenReaderPresets: () -> Unit,
    onOpenTtsSettings: () -> Unit,
    onOpenAutoScrollSettings: () -> Unit,
    onOpenRemoteInput: () -> Unit,
    onOpenOobe: () -> Unit,
    onOpenPerfLargeList: () -> Unit,
    onOpenPerfLargeArticle: () -> Unit,
    onOpenLlmPromptPreset: () -> Unit,
    onOpenLlmTokenUsage: () -> Unit,
    onBeianClick: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showManualCookieDialog by remember { mutableStateOf(false) }
    var manualCookieInput by remember { mutableStateOf("") }
    var isApplyingManualCookie by remember { mutableStateOf(false) }

    SettingsScreen(
        cacheLimitMb = viewModel.cacheLimitMb,
        cacheUsageMb = viewModel.cacheUsageMb,
        shareUseSystem = viewModel.shareUseSystem,
        readingFontSizeSp = viewModel.readingFontSizeSp,
        syncMediaKeepAliveEnabled = viewModel.syncMediaKeepAliveEnabled,
        mediaVolumeControlEnabled = viewModel.mediaVolumeControlEnabled,
        mediaVolumeGuardEnabled = viewModel.mediaVolumeGuardEnabled,
        mediaPlaybackStartVolumeLimitPercent = viewModel.mediaPlaybackStartVolumeLimitPercent,
        rssInlineImagePrefetchMode = viewModel.rssInlineImagePrefetchMode,
        llmEnabledFlow = viewModel.llmEnabled,
        llmAutoSummarize = viewModel.llmAutoSummarize,
        llmShowTokenUsage = viewModel.llmShowTokenUsage,
        llmPromptPreset = viewModel.llmPromptPreset,
        showPerformanceTools = showPerformanceTools,
        showDetailedTtsConfiguration = showDetailedTtsConfiguration,
        hasPaidAiAccess = hasPaidAiAccess,
        onSelectCacheLimit = viewModel::updateCacheLimitMb,
        onToggleShareMode = viewModel::toggleShareUseSystem,
        onSelectFontSize = viewModel::updateReadingFontSizeSp,
        onToggleSyncMediaKeepAlive = viewModel::toggleSyncMediaKeepAlive,
        onToggleMediaVolumeControl = viewModel::toggleMediaVolumeControl,
        onToggleMediaVolumeGuard = viewModel::toggleMediaVolumeGuard,
        onSelectMediaPlaybackStartVolumeLimit = viewModel::updateMediaPlaybackStartVolumeLimitPercent,
        onSelectRssInlineImagePrefetchMode = viewModel::updateRssInlineImagePrefetchMode,
        onToggleLlmEnabled = viewModel::toggleLlmEnabled,
        onToggleLlmAutoSummarize = viewModel::toggleLlmAutoSummarize,
        onToggleLlmShowTokenUsage = viewModel::toggleLlmShowTokenUsage,
        onOpenAdvanced = onOpenAdvanced,
        onOpenReaderPresets = onOpenReaderPresets,
        onOpenTtsSettings = onOpenTtsSettings,
        onOpenAutoScrollSettings = onOpenAutoScrollSettings,
        onOpenRemoteInput = onOpenRemoteInput,
        onOpenOobe = onOpenOobe,
        onOpenPerfLargeList = onOpenPerfLargeList,
        onOpenPerfLargeArticle = onOpenPerfLargeArticle,
        onOpenDouyinCookieInput = {
            manualCookieInput = ""
            showManualCookieDialog = true
        },
        onOpenLlmPromptPreset = onOpenLlmPromptPreset,
        onOpenLlmTokenUsage = onOpenLlmTokenUsage,
        onBeianClick = onBeianClick
    )

    if (showManualCookieDialog) {
        DouyinCookieInputDialog(
            value = manualCookieInput,
            isSaving = isApplyingManualCookie,
            confirmEnabled = manualCookieInput.isNotBlank() && !isApplyingManualCookie,
            onValueChange = { manualCookieInput = it },
            onConfirm = {
                val trimmed = manualCookieInput.trim()
                if (trimmed.isBlank()) {
                    com.lightningstudio.watchrss.ui.util.showAppToast(
                        context,
                        "缺少有效 Cookie",
                        android.widget.Toast.LENGTH_SHORT
                    )
                    return@DouyinCookieInputDialog
                }
                scope.launch {
                    isApplyingManualCookie = true
                    val result = douyinRepository.applyCookieHeader(trimmed)
                    if (result.isSuccess) {
                        val channelId = rssRepository
                            .observeChannels()
                            .first()
                            .firstOrNull { it.url == BuiltinChannelType.DOUYIN.url }
                            ?.id
                        if (channelId != null) {
                            rssRepository.refreshChannelInBackground(channelId, refreshAll = true)
                        }
                        showManualCookieDialog = false
                        manualCookieInput = ""
                        com.lightningstudio.watchrss.ui.util.showAppToast(
                            context,
                            "Cookie 已保存",
                            android.widget.Toast.LENGTH_SHORT
                        )
                    } else {
                        com.lightningstudio.watchrss.ui.util.showAppToast(
                            context,
                            result.exceptionOrNull()?.message
                                ?.takeIf { it.isNotBlank() }
                                ?: "Cookie 无效",
                            android.widget.Toast.LENGTH_SHORT
                        )
                    }
                    isApplyingManualCookie = false
                }
            },
            onCancel = {
                showManualCookieDialog = false
                manualCookieInput = ""
            }
        )
    }
}
