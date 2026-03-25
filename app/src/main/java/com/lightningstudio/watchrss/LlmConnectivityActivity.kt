package com.lightningstudio.watchrss

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.lightningstudio.watchrss.ui.screen.rss.LlmConnectivityScreen
import com.lightningstudio.watchrss.ui.theme.WatchRSSTheme
import com.lightningstudio.watchrss.ui.viewmodel.AppViewModelFactory
import com.lightningstudio.watchrss.ui.viewmodel.LlmConnectivityViewModel

class LlmConnectivityActivity : BaseWatchActivity() {
    private val viewModel: LlmConnectivityViewModel by viewModels {
        AppViewModelFactory((application as WatchRssApplication).container)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupSystemBars()

        setContent {
            WatchRSSTheme {
                LlmConnectivityScreen(viewModel = viewModel)
            }
        }
    }

    companion object {
        fun createIntent(context: Context) = Intent(context, LlmConnectivityActivity::class.java)
    }
}
