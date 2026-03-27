package com.lightningstudio.watchrss

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.lightningstudio.watchrss.debug.PerfEntryActivity
import com.lightningstudio.watchrss.phoneconnection.PhoneConnectionAbility
import com.lightningstudio.watchrss.ui.screen.rss.SettingsScreenHost
import com.lightningstudio.watchrss.ui.theme.WatchRSSTheme
import com.lightningstudio.watchrss.ui.viewmodel.AppViewModelFactory
import com.lightningstudio.watchrss.ui.viewmodel.SettingsViewModel

class SettingsActivity : BaseWatchActivity() {
    private val container by lazy { (application as WatchRssApplication).container }
    private val douyinRepository by lazy { container.douyinRepository }
    private val rssRepository by lazy { container.rssRepository }
    private val viewModel: SettingsViewModel by viewModels {
        AppViewModelFactory(container)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupSystemBars()

        setContent {
            WatchRSSTheme {
                SettingsScreenHost(
                    viewModel = viewModel,
                    douyinRepository = douyinRepository,
                    rssRepository = rssRepository,
                    showPerformanceTools = false,
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
                    onOpenLlmConnectivity = {
                        startActivity(LlmConnectivityActivity.createIntent(this))
                    },
                    onOpenLlmPhoneConfig = {
                        startActivity(
                            PhoneConnectionActivity.createIntent(
                                context = this,
                                preferredAbility = PhoneConnectionAbility.LLM_SUMMARY_CONFIG
                            )
                        )
                    },
                    onOpenLlmPromptPreset = {
                        startActivity(LlmPromptPresetActivity.createIntent(this))
                    },
                    onOpenReadAloudSettings = {
                        startActivity(ReadAloudApiSettingsActivity.createIntent(this))
                    },
                    onBeianClick = {
                        startActivity(BeianActivity.createIntent(this))
                    }
                )
            }
        }
    }
}
