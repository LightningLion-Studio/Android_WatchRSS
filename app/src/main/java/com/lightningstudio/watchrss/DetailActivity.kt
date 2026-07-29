package com.lightningstudio.watchrss

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.lifecycleScope
import com.lightningstudio.watchrss.data.tts.ReadAloudStartAnchor
import com.lightningstudio.watchrss.ui.screen.rss.DetailScreen
import com.lightningstudio.watchrss.ui.reader.ProvideReaderPreset
import com.lightningstudio.watchrss.ui.theme.WatchRSSTheme
import com.lightningstudio.watchrss.ui.util.showAppToast
import com.lightningstudio.watchrss.ui.viewmodel.AppViewModelFactory
import com.lightningstudio.watchrss.ui.viewmodel.DetailViewModel
import com.lightningstudio.watchrss.ui.viewmodel.LlmSummaryViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.asStateFlow

class DetailActivity : BaseWatchActivity() {
    private val container by lazy { (application as WatchRssApplication).container }
    private val viewModel: DetailViewModel by viewModels {
        AppViewModelFactory(container)
    }
    private val llmSummaryViewModel: LlmSummaryViewModel by viewModels {
        AppViewModelFactory(container)
    }

    private val _isStartingActivity = MutableStateFlow(false)
    private val isStartingActivity = _isStartingActivity.asStateFlow()

    private var fromWatchLater: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupSystemBars()
        fromWatchLater = intent.getBooleanExtra(EXTRA_FROM_WATCH_LATER, false)
        val itemId = intent.getLongExtra(EXTRA_ITEM_ID, 0L)
        if (itemId > 0L) {
            llmSummaryViewModel.prepare(itemId)
        }
        if (intent.hasExtra(EXTRA_USE_ORIGINAL_CONTENT)) {
            viewModel.setOriginalContentEnabled(
                intent.getBooleanExtra(EXTRA_USE_ORIGINAL_CONTENT, false)
            )
        }

        setContent {
            WatchRSSTheme {
                ProvideReaderPreset(container.readerPresetRepository) {
                val context = LocalContext.current
                val llmSummaryState by llmSummaryViewModel.state.collectAsState()
                val message by viewModel.message.collectAsState()
                val readAloudState by container.readAloudController.uiState.collectAsState()
                LaunchedEffect(message) {
                    if (message != null) {
                        showAppToast(context, message, Toast.LENGTH_SHORT)
                        viewModel.clearMessage()
                    }
                }
                DetailScreen(
                    viewModel = viewModel,
                    llmSummaryState = llmSummaryState,
                    readAloudState = readAloudState,
                    isStartingActivity = isStartingActivity.collectAsState().value,
                    onOpenAiSummary = ::openAiSummary,
                    onOpenReadAloud = ::openReadAloud,
                    onOpenReadAloudControls = ::openReadAloudControls,
                    onBack = { itemId, reachedBottom, isWatchLater ->
                        handleBackPress(itemId, reachedBottom, isWatchLater)
                    }
                )
                }
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

    override fun shouldUsePlatformSwipeDismissFeature(): Boolean {
        // DetailScreen owns the synchronous reading-progress save in its BackHandler.
        // Platform swipe dismiss can finish this Activity without passing through it.
        return false
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            _isStartingActivity.value = false
        }
    }

    private fun openAiSummary() {
        val itemId = intent.getLongExtra(EXTRA_ITEM_ID, 0L)
        if (itemId <= 0L) return

        lifecycleScope.launch {
            val isConfigured = container.llmApiKeyStore.hasApiKey() &&
                container.settingsRepository.llmProvider.first().isNotBlank()
            _isStartingActivity.value = true
            startActivity(
                if (isConfigured) {
                    LlmSummaryActivity.createIntent(this@DetailActivity, itemId)
                } else {
                    LlmConnectivityActivity.createIntent(this@DetailActivity, itemId)
                }
            )
        }
    }

    private fun openReadAloud(startAnchor: ReadAloudStartAnchor?, preferOriginalContent: Boolean) {
        val itemId = intent.getLongExtra(EXTRA_ITEM_ID, 0L)
        if (itemId <= 0L) return

        container.readAloudController.startFromItem(itemId, startAnchor, preferOriginalContent)
        val message = if (startAnchor == null) {
            "已开始朗读，长按文章可打开朗读页面"
        } else {
            "已从当前屏幕开始朗读"
        }
        showAppToast(this, message, Toast.LENGTH_SHORT)
    }

    private fun openReadAloudControls(startAnchor: ReadAloudStartAnchor?, preferOriginalContent: Boolean) {
        val itemId = intent.getLongExtra(EXTRA_ITEM_ID, 0L)
        if (itemId <= 0L) return

        val readAloudState = container.readAloudController.uiState.value
        if (!readAloudState.visible || readAloudState.currentItemId != itemId) {
            container.readAloudController.startFromItem(itemId, startAnchor, preferOriginalContent)
        }
        _isStartingActivity.value = true
        startActivity(
            ReadAloudPlaybackActivity.createIntent(
                context = this@DetailActivity,
                returnToArticle = true
            )
        )
    }

    override fun buildResumeIntent(): Intent? {
        val itemId = intent.getLongExtra(EXTRA_ITEM_ID, 0L)
        if (itemId <= 0L) return null
        return Intent(this, DetailActivity::class.java).apply {
            putExtra(EXTRA_ITEM_ID, itemId)
            putExtra(EXTRA_FROM_WATCH_LATER, fromWatchLater)
            if (intent.hasExtra(EXTRA_USE_ORIGINAL_CONTENT)) {
                putExtra(
                    EXTRA_USE_ORIGINAL_CONTENT,
                    intent.getBooleanExtra(EXTRA_USE_ORIGINAL_CONTENT, false)
                )
            }
        }
    }

    companion object {
        const val EXTRA_ITEM_ID = "itemId"
        const val EXTRA_USE_ORIGINAL_CONTENT = "useOriginalContent"
        const val EXTRA_FROM_WATCH_LATER = "fromWatchLater"
        const val EXTRA_REMOVE_WATCH_LATER_ID = "removeWatchLaterId"
    }
}
