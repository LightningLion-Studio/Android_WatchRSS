package com.lightningstudio.watchrss

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.lifecycleScope
import com.lightningstudio.watchrss.phoneconnection.PhoneConnectionAbility
import com.lightningstudio.watchrss.ui.screen.rss.DetailScreen
import com.lightningstudio.watchrss.ui.theme.WatchRSSTheme
import com.lightningstudio.watchrss.ui.viewmodel.AppViewModelFactory
import com.lightningstudio.watchrss.ui.viewmodel.DetailViewModel
import com.lightningstudio.watchrss.ui.viewmodel.LlmSummaryViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class DetailActivity : BaseWatchActivity() {
    private val container by lazy { (application as WatchRssApplication).container }
    private val viewModel: DetailViewModel by viewModels {
        AppViewModelFactory(container)
    }
    private val llmSummaryViewModel: LlmSummaryViewModel by viewModels {
        AppViewModelFactory(container)
    }

    private var fromWatchLater: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupSystemBars()
        fromWatchLater = intent.getBooleanExtra(EXTRA_FROM_WATCH_LATER, false)
        val itemId = intent.getLongExtra(EXTRA_ITEM_ID, 0L)
        if (itemId > 0L) {
            llmSummaryViewModel.prepare(itemId)
        }

        setContent {
            WatchRSSTheme {
                val llmSummaryState by llmSummaryViewModel.state.collectAsState()
                DetailScreen(
                    viewModel = viewModel,
                    llmSummaryState = llmSummaryState,
                    onOpenAiSummary = ::openAiSummary,
                    onBack = { itemId, reachedBottom, isWatchLater ->
                        handleBackPress(itemId, reachedBottom, isWatchLater)
                    }
                )
            }
        }
    }

    private fun handleBackPress(itemId: Long, reachedBottom: Boolean, isWatchLater: Boolean) {
        if (fromWatchLater && reachedBottom && isWatchLater && itemId > 0L) {
            val data = Intent().putExtra(EXTRA_REMOVE_WATCH_LATER_ID, itemId)
            setResult(RESULT_OK, data)
        }
        finish()
    }

    private fun openAiSummary() {
        val itemId = intent.getLongExtra(EXTRA_ITEM_ID, 0L)
        if (itemId <= 0L) return

        lifecycleScope.launch {
            val isConfigured = container.llmApiKeyStore.hasApiKey() &&
                container.settingsRepository.llmProvider.first().isNotBlank()
            startActivity(
                if (isConfigured) {
                    LlmSummaryActivity.createIntent(this@DetailActivity, itemId)
                } else {
                    PhoneConnectionActivity.createIntent(
                        context = this@DetailActivity,
                        preferredAbility = PhoneConnectionAbility.LLM_SUMMARY_CONFIG,
                        llmSummaryItemId = itemId
                    )
                }
            )
        }
    }

    override fun buildResumeIntent(): Intent? {
        val itemId = intent.getLongExtra(EXTRA_ITEM_ID, 0L)
        if (itemId <= 0L) return null
        return Intent(this, DetailActivity::class.java).apply {
            putExtra(EXTRA_ITEM_ID, itemId)
            putExtra(EXTRA_FROM_WATCH_LATER, fromWatchLater)
        }
    }

    companion object {
        const val EXTRA_ITEM_ID = "itemId"
        const val EXTRA_FROM_WATCH_LATER = "fromWatchLater"
        const val EXTRA_REMOVE_WATCH_LATER_ID = "removeWatchLaterId"
    }
}
