package com.lightningstudio.watchrss

import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.activity.result.contract.ActivityResultContracts
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
    private val autoScrollHasStarted = mutableStateOf(false)
    private val novelMenuExpanded = mutableStateOf(false)

    override fun onSwipeBackAttempt(dx: Float, dy: Float): Boolean {
        if (!novelMenuExpanded.value) return false
        novelMenuExpanded.value = false
        return true
    }
    private val autoScrollControlLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != RESULT_OK) return@registerForActivityResult
        val data = result.data ?: return@registerForActivityResult
        if (!data.hasExtra(AutoScrollControlActivity.EXTRA_PLAYING_RESULT)) {
            return@registerForActivityResult
        }
        viewModel.setReaderAutoScrollPlaying(
            data.getBooleanExtra(AutoScrollControlActivity.EXTRA_PLAYING_RESULT, false)
        )
        autoScrollHasStarted.value = data.getBooleanExtra(
            AutoScrollControlActivity.EXTRA_STARTED_RESULT,
            autoScrollHasStarted.value
        )
    }

    private var fromWatchLater: Boolean = false
    private var readStartedAt: Long = 0L
    private var articleTitle: String? = null
    private var articleChannelId: String? = null
    private var articleChannelTitle: String? = null

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

        lifecycleScope.launch {
            viewModel.item.collect { current ->
                if (current != null && readStartedAt == 0L) {
                    readStartedAt = SystemClock.elapsedRealtime()
                    articleTitle = current.title
                    articleChannelId = current.channelId.toString()
                    articleChannelTitle = channelTitle(current.channelId)
                    container.watchUsageTelemetry.recordArticleReadStarted(
                        itemId = current.id,
                        title = current.title,
                        channelId = articleChannelId,
                        channelTitle = articleChannelTitle
                    )
                }
            }
        }
        setContent {
            WatchRSSTheme {
                ProvideReaderPreset(container.readerPresetRepository) {
                val context = LocalContext.current
                val llmSummaryState by llmSummaryViewModel.state.collectAsState()
                val message by viewModel.messageEvent.collectAsState()
                val readAloudState by container.readAloudController.uiState.collectAsState()
                LaunchedEffect(message?.id) {
                    val event = message
                    if (event != null) {
                        showAppToast(context, event.text, Toast.LENGTH_SHORT)
                        viewModel.clearMessage(event.id)
                    }
                }
                DetailScreen(
                    viewModel = viewModel,
                    novelMenuExpanded = novelMenuExpanded.value,
                    onNovelMenuExpandedChange = { novelMenuExpanded.value = it },
                    initialIsNovelMode = intent.getBooleanExtra(EXTRA_IS_NOVEL_MODE, false),
                    llmSummaryState = llmSummaryState,
                    readAloudState = readAloudState,
                    isStartingActivity = isStartingActivity.collectAsState().value,
                    onRequestAiSummary = ::requestAiSummary,
                    onOpenReadAloud = ::openReadAloud,
                    onOpenReadAloudControls = ::openReadAloudControls,
                    onOpenAutoScrollControls = ::openAutoScrollControls,
                    onOpenChapter = { nextId ->
                        startActivity(Intent(this@DetailActivity, DetailActivity::class.java)
                            .putExtra(EXTRA_ITEM_ID, nextId)
                            .putExtra(EXTRA_IS_NOVEL_MODE, intent.getBooleanExtra(EXTRA_IS_NOVEL_MODE, false)))
                        finish()
                    },
                    onBack = { itemId, reachedBottom, isWatchLater ->
                        handleBackPress(itemId, reachedBottom, isWatchLater)
                    }
                )
                }
            }
        }
    }

    private fun handleBackPress(itemId: Long, reachedBottom: Boolean, isWatchLater: Boolean) {
        if (itemId > 0L && readStartedAt > 0L) {
            container.watchUsageTelemetry.recordArticleReadFinished(
                itemId = itemId,
                title = articleTitle,
                channelId = articleChannelId,
                channelTitle = articleChannelTitle,
                reachedBottom = reachedBottom,
                durationMs = SystemClock.elapsedRealtime() - readStartedAt
            )
            readStartedAt = 0L
        }
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

    private fun requestAiSummary() {
        val itemId = intent.getLongExtra(EXTRA_ITEM_ID, 0L)
        if (itemId <= 0L) return
        if (llmSummaryViewModel.state.value.status is
            com.lightningstudio.watchrss.ui.viewmodel.SummaryStatus.Error
        ) {
            llmSummaryViewModel.retry()
        } else {
            llmSummaryViewModel.prepareAndStart(itemId)
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

    private fun openAutoScrollControls(isPlaying: Boolean, hasStarted: Boolean, isAtBottom: Boolean) {
        _isStartingActivity.value = true
        autoScrollControlLauncher.launch(
            AutoScrollControlActivity.createReaderIntent(
                context = this,
                isPlaying = isPlaying,
                hasStarted = hasStarted || autoScrollHasStarted.value,
                isAtBottom = isAtBottom
            )
        )
    }

    override fun buildResumeIntent(): Intent? {
        val itemId = intent.getLongExtra(EXTRA_ITEM_ID, 0L)
        if (itemId <= 0L) return null
        return Intent(this, DetailActivity::class.java).apply {
            putExtra(EXTRA_ITEM_ID, itemId)
            putExtra(EXTRA_IS_NOVEL_MODE, intent.getBooleanExtra(EXTRA_IS_NOVEL_MODE, false))
            putExtra(EXTRA_FROM_WATCH_LATER, fromWatchLater)
            if (intent.hasExtra(EXTRA_USE_ORIGINAL_CONTENT)) {
                putExtra(
                    EXTRA_USE_ORIGINAL_CONTENT,
                    intent.getBooleanExtra(EXTRA_USE_ORIGINAL_CONTENT, false)
                )
            }
        }
    }


    private suspend fun channelTitle(channelId: Long): String? {
        return container.rssRepository.observeChannel(channelId).first()?.title
    }
    companion object {
        const val EXTRA_ITEM_ID = "itemId"
        const val EXTRA_IS_NOVEL_MODE = "isNovelMode"
        const val EXTRA_USE_ORIGINAL_CONTENT = "useOriginalContent"
        const val EXTRA_FROM_WATCH_LATER = "fromWatchLater"
        const val EXTRA_REMOVE_WATCH_LATER_ID = "removeWatchLaterId"
    }
}
