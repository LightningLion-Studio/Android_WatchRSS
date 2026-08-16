package com.lightningstudio.watchrss

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import com.lightningstudio.watchrss.phoneconnection.PhoneConnectionAbility
import com.lightningstudio.watchrss.ui.screen.rss.LlmConnectivityScreen
import com.lightningstudio.watchrss.ui.theme.WatchRSSTheme
import com.lightningstudio.watchrss.ui.viewmodel.AppViewModelFactory
import com.lightningstudio.watchrss.ui.viewmodel.LlmConnectivityViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class LlmConnectivityActivity : BaseWatchActivity() {
    private val container by lazy { (application as WatchRssApplication).container }
    private val viewModel: LlmConnectivityViewModel by viewModels {
        AppViewModelFactory(container)
    }
    private var llmSummaryItemId: Long = 0L
    private var llmSummaryNavigationHandled: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupSystemBars()
        llmSummaryItemId = intent.getLongExtra(EXTRA_LLM_SUMMARY_ITEM_ID, 0L)

        setContent {
            WatchRSSTheme {
                LlmConnectivityScreen(
                    viewModel = viewModel,
                    onOpenPhoneConfig = {
                        startActivity(
                            Intent(this, ServerActivity::class.java).apply {
                                putExtra(
                                    ServerActivity.EXTRA_SERVER_TYPE,
                                    ServerActivity.ServerType.LLM_CONFIG.name
                                )
                                putExtra(
                                    ServerActivity.EXTRA_PREFERRED_ABILITY,
                                    PhoneConnectionAbility.LLM_SUMMARY_CONFIG.name
                                )
                            }
                        )
                    }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        maybeOpenLlmSummary()
    }

    private fun maybeOpenLlmSummary() {
        if (llmSummaryNavigationHandled || llmSummaryItemId <= 0L) return

        lifecycleScope.launch {
            val isConfigured = container.llmApiKeyStore.hasApiKey() &&
                container.settingsRepository.llmProvider.first().isNotBlank()
            if (!isConfigured) return@launch

            llmSummaryNavigationHandled = true
            startActivity(LlmSummaryActivity.createIntent(this@LlmConnectivityActivity, llmSummaryItemId))
            finish()
        }
    }

    companion object {
        private const val EXTRA_LLM_SUMMARY_ITEM_ID = "llm_summary_item_id"

        fun createIntent(context: Context, llmSummaryItemId: Long = 0L) =
            Intent(context, LlmConnectivityActivity::class.java).apply {
                putExtra(EXTRA_LLM_SUMMARY_ITEM_ID, llmSummaryItemId)
            }
    }
}
