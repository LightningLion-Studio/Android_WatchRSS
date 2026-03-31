package com.lightningstudio.watchrss

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.lifecycleScope
import com.lightningstudio.watchrss.phoneconnection.PhoneConnectionAbility
import com.lightningstudio.watchrss.ui.screen.rss.ReadAloudSettingsScreen
import com.lightningstudio.watchrss.ui.theme.WatchRSSTheme
import com.lightningstudio.watchrss.ui.viewmodel.AppViewModelFactory
import com.lightningstudio.watchrss.ui.viewmodel.ReadAloudSettingsViewModel
import kotlinx.coroutines.launch

class ReadAloudApiSettingsActivity : BaseWatchActivity() {
    private val container by lazy { (application as WatchRssApplication).container }
    private val viewModel: ReadAloudSettingsViewModel by viewModels {
        AppViewModelFactory(container)
    }

    private var pendingItemId: Long = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupSystemBars()
        pendingItemId = intent.getLongExtra(EXTRA_ITEM_ID, 0L)

        setContent {
            WatchRSSTheme {
                val state by viewModel.state.collectAsState()
                ReadAloudSettingsScreen(
                    state = state,
                    onCycleProvider = viewModel::cycleProvider,
                    onModelChange = viewModel::updateModel,
                    onVoiceChange = viewModel::updateVoice,
                    onBaseUrlChange = viewModel::updateBaseUrl,
                    onRegionChange = viewModel::updateRegion,
                    onAppIdChange = viewModel::updateAppId,
                    onResourceIdChange = viewModel::updateResourceId,
                    onApiKeyChange = viewModel::updateApiKey,
                    onSave = {
                        lifecycleScope.launch {
                            val result = viewModel.saveConfig()
                            if (result.isSuccess && pendingItemId > 0L) {
                                container.readAloudController.startFromItem(pendingItemId)
                                startActivity(ReadAloudPlaybackActivity.createIntent(this@ReadAloudApiSettingsActivity))
                                finish()
                            }
                        }
                    },
                    onRunTest = viewModel::runTest,
                    onOpenPhoneConfig = {
                        startActivity(
                            PhoneConnectionActivity.createIntent(
                                context = this@ReadAloudApiSettingsActivity,
                                preferredAbility = PhoneConnectionAbility.READ_ALOUD_CONFIG,
                                readAloudItemId = pendingItemId
                            )
                        )
                    }
                )
            }
        }
    }

    companion object {
        private const val EXTRA_ITEM_ID = "item_id"

        fun createIntent(context: Context, itemId: Long = 0L): Intent {
            return Intent(context, ReadAloudApiSettingsActivity::class.java).apply {
                putExtra(EXTRA_ITEM_ID, itemId)
            }
        }
    }
}
