package com.lightningstudio.watchrss

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.lightningstudio.watchrss.ui.screen.rss.LlmPromptPresetScreen
import com.lightningstudio.watchrss.ui.theme.WatchRSSTheme
import com.lightningstudio.watchrss.ui.viewmodel.AppViewModelFactory
import com.lightningstudio.watchrss.ui.viewmodel.SettingsViewModel

class LlmPromptPresetActivity : BaseWatchActivity() {
    private val viewModel: SettingsViewModel by viewModels {
        AppViewModelFactory((application as WatchRssApplication).container)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupSystemBars()

        setContent {
            WatchRSSTheme {
                val currentPreset by viewModel.llmPromptPreset.collectAsState()
                LlmPromptPresetScreen(
                    currentPreset = currentPreset,
                    onSelectPreset = { preset ->
                        viewModel.updateLlmPromptPreset(preset)
                        finish()
                    }
                )
            }
        }
    }

    companion object {
        fun createIntent(context: Context) = Intent(context, LlmPromptPresetActivity::class.java)
    }
}
