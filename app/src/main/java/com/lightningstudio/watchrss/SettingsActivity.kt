package com.lightningstudio.watchrss

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.lightningstudio.watchrss.debug.PerfEntryActivity
import com.lightningstudio.watchrss.ui.screen.rss.SettingsScreen
import com.lightningstudio.watchrss.ui.theme.WatchRSSTheme
import com.lightningstudio.watchrss.ui.viewmodel.AppViewModelFactory
import com.lightningstudio.watchrss.ui.viewmodel.SettingsViewModel

class SettingsActivity : BaseWatchActivity() {
    private val viewModel: SettingsViewModel by viewModels {
        AppViewModelFactory((application as WatchRssApplication).container)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupSystemBars()

        setContent {
            WatchRSSTheme {
                SettingsScreen(
                    cacheLimitMb = viewModel.cacheLimitMb,
                    cacheUsageMb = viewModel.cacheUsageMb,
                    readingThemeDark = viewModel.readingThemeDark,
                    shareUseSystem = viewModel.shareUseSystem,
                    readingFontSizeSp = viewModel.readingFontSizeSp,
                    phoneConnectionEnabled = viewModel.phoneConnectionEnabled,
                    showPerformanceTools = false,
                    onSelectCacheLimit = viewModel::updateCacheLimitMb,
                    onToggleReadingTheme = viewModel::toggleReadingTheme,
                    onToggleShareMode = viewModel::toggleShareUseSystem,
                    onSelectFontSize = viewModel::updateReadingFontSizeSp,
                    onTogglePhoneConnection = viewModel::togglePhoneConnection,
                    onOpenOobe = {
                        startActivity(OobeActivity.createIntent(this, returnHomeOnFinish = false))
                    },
                    onOpenPerfLargeList = {
                        startActivity(
                            PerfEntryActivity.createIntent(
                                this,
                                PerfEntryActivity.TARGET_LARGE_LIST
                            )
                        )
                    },
                    onOpenPerfLargeArticle = {
                        startActivity(
                            PerfEntryActivity.createIntent(
                                this,
                                PerfEntryActivity.TARGET_LARGE_ARTICLE
                            )
                        )
                    },
                    onBeianClick = {
                        startActivity(BeianActivity.createIntent(this))
                    }
                )
            }
        }
    }
}
