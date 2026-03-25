package com.lightningstudio.watchrss

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.lightningstudio.watchrss.ui.screen.rss.LlmSummaryScreen
import com.lightningstudio.watchrss.ui.theme.WatchRSSTheme
import com.lightningstudio.watchrss.ui.viewmodel.AppViewModelFactory
import com.lightningstudio.watchrss.ui.viewmodel.LlmSummaryViewModel

class LlmSummaryActivity : BaseWatchActivity() {
    private var itemId: Long = -1L

    private val viewModel: LlmSummaryViewModel by viewModels {
        AppViewModelFactory((application as WatchRssApplication).container)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupSystemBars()

        val container = (application as WatchRssApplication).container
        itemId = intent.getLongExtra(EXTRA_ITEM_ID, -1L)
        if (!container.llmApiKeyStore.hasApiKey()) {
            startActivity(LlmConnectivityActivity.createIntent(this, itemId))
            finish()
            return
        }

        if (itemId > 0L) viewModel.prepareAndStart(itemId)

        val settingsRepository = container.settingsRepository
        setContent {
            WatchRSSTheme {
                val showTokenUsage by settingsRepository.llmShowTokenUsage.collectAsState(initial = false)
                LlmSummaryScreen(
                    viewModel = viewModel,
                    showTokenUsage = showTokenUsage,
                    onOpenConnectivityCheck = {
                        startActivity(LlmConnectivityActivity.createIntent(this, itemId))
                    }
                )
            }
        }
    }

    companion object {
        const val EXTRA_ITEM_ID = "item_id"
        fun createIntent(context: Context, itemId: Long): Intent =
            Intent(context, LlmSummaryActivity::class.java)
                .putExtra(EXTRA_ITEM_ID, itemId)
    }
}
