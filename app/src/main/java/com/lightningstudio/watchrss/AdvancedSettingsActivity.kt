package com.lightningstudio.watchrss

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.lightningstudio.watchrss.ui.screen.rss.AdvancedSettingsScreen
import com.lightningstudio.watchrss.ui.theme.WatchRSSTheme
import com.lightningstudio.watchrss.ui.viewmodel.AppViewModelFactory
import com.lightningstudio.watchrss.ui.viewmodel.SettingsViewModel

class AdvancedSettingsActivity : BaseWatchActivity() {
    private val container by lazy { (application as WatchRssApplication).container }
    private val viewModel: SettingsViewModel by viewModels {
        AppViewModelFactory(container)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupSystemBars()

        setContent {
            WatchRSSTheme {
                AdvancedSettingsScreen(
                    cacheLimitMb = viewModel.cacheLimitMb,
                    cacheUsageMb = viewModel.cacheUsageMb,
                    shareUseSystem = viewModel.shareUseSystem,
                    syncMediaKeepAliveEnabled = viewModel.syncMediaKeepAliveEnabled,
                    rssInlineImagePrefetchMode = viewModel.rssInlineImagePrefetchMode,
                    onSelectCacheLimit = viewModel::updateCacheLimitMb,
                    onToggleShareMode = viewModel::toggleShareUseSystem,
                    onToggleSyncMediaKeepAlive = viewModel::toggleSyncMediaKeepAlive,
                    onSelectRssInlineImagePrefetchMode = viewModel::updateRssInlineImagePrefetchMode
                )
            }
        }
    }
}
