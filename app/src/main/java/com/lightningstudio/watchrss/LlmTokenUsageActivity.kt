package com.lightningstudio.watchrss

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.lightningstudio.watchrss.ui.screen.rss.LlmTokenUsageScreen
import com.lightningstudio.watchrss.ui.theme.WatchRSSTheme
import com.lightningstudio.watchrss.ui.viewmodel.AppViewModelFactory
import com.lightningstudio.watchrss.ui.viewmodel.LlmTokenUsageViewModel

class LlmTokenUsageActivity : BaseWatchActivity() {
    private val viewModel: LlmTokenUsageViewModel by viewModels {
        AppViewModelFactory((application as WatchRssApplication).container)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupSystemBars()

        setContent {
            WatchRSSTheme {
                LlmTokenUsageScreen(
                    viewModel = viewModel,
                    onNavigateBack = { finish() }
                )
            }
        }
    }

    companion object {
        fun createIntent(context: Context): Intent =
            Intent(context, LlmTokenUsageActivity::class.java)
    }
}
