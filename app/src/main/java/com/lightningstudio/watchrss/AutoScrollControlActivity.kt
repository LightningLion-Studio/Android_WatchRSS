package com.lightningstudio.watchrss

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.lifecycleScope
import com.lightningstudio.watchrss.data.settings.DEFAULT_READER_AUTO_SCROLL_ENABLED
import com.lightningstudio.watchrss.data.settings.DEFAULT_READER_AUTO_SCROLL_LINES_PER_SECOND
import com.lightningstudio.watchrss.ui.screen.rss.AutoScrollControlScreen
import com.lightningstudio.watchrss.ui.theme.WatchRSSTheme
import kotlinx.coroutines.launch

class AutoScrollControlActivity : BaseWatchActivity() {
    private val container by lazy { (application as WatchRssApplication).container }
    private val readerSessionActive by lazy {
        intent.getBooleanExtra(EXTRA_READER_SESSION_ACTIVE, false)
    }
    private val playingState = mutableStateOf(false)
    private val startedState = mutableStateOf(false)
    private val atBottomState = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupSystemBars()
        playingState.value = intent.getBooleanExtra(EXTRA_PLAYING, false)
        startedState.value = intent.getBooleanExtra(EXTRA_STARTED, false) || playingState.value
        atBottomState.value = intent.getBooleanExtra(EXTRA_AT_BOTTOM, false)

        setContent {
            WatchRSSTheme {
                val autoStartEnabled by container.settingsRepository.readerAutoScrollEnabled.collectAsState(
                    initial = DEFAULT_READER_AUTO_SCROLL_ENABLED
                )
                val linesPerSecond by container.settingsRepository.readerAutoScrollLinesPerSecond.collectAsState(
                    initial = DEFAULT_READER_AUTO_SCROLL_LINES_PER_SECOND
                )
                val tapToScrollEnabled by container.settingsRepository.readerTapToScrollEnabled.collectAsState(initial = false)
                AutoScrollControlScreen(
                    tapToScrollEnabled = tapToScrollEnabled,
                    onTapToScrollEnabledChange = { enabled ->
                        lifecycleScope.launch { container.settingsRepository.setReaderTapToScrollEnabled(enabled) }
                    },
                    autoStartEnabled = autoStartEnabled,
                    linesPerSecond = linesPerSecond,
                    readerSessionActive = readerSessionActive,
                    isPlaying = playingState.value,
                    hasStarted = startedState.value,
                    isAtBottom = atBottomState.value,
                    onPlayingChange = {
                        playingState.value = it
                        if (it) {
                            startedState.value = true
                            atBottomState.value = false
                        }
                    },
                    onAutoStartEnabledChange = { enabled ->
                        lifecycleScope.launch {
                            container.settingsRepository.setReaderAutoScrollEnabled(enabled)
                        }
                    },
                    onLinesPerSecondChange = { speed ->
                        lifecycleScope.launch {
                            container.settingsRepository.setReaderAutoScrollLinesPerSecond(speed)
                        }
                    },
                    onReturnToArticle = if (readerSessionActive) {
                        ::finish
                    } else {
                        null
                    }
                )
            }
        }
    }

    override fun finish() {
        if (readerSessionActive) {
            setResult(
                Activity.RESULT_OK,
                Intent()
                    .putExtra(EXTRA_PLAYING_RESULT, playingState.value)
                    .putExtra(EXTRA_STARTED_RESULT, startedState.value)
                    .putExtra(EXTRA_AT_BOTTOM_RESULT, atBottomState.value)
            )
        }
        super.finish()
    }

    companion object {
        const val EXTRA_PLAYING_RESULT = "autoScrollPlayingResult"
        const val EXTRA_STARTED_RESULT = "autoScrollStartedResult"
        const val EXTRA_AT_BOTTOM_RESULT = "autoScrollAtBottomResult"
        private const val EXTRA_READER_SESSION_ACTIVE = "readerSessionActive"
        private const val EXTRA_PLAYING = "autoScrollPlaying"
        private const val EXTRA_STARTED = "autoScrollStarted"
        private const val EXTRA_AT_BOTTOM = "autoScrollAtBottom"

        fun createSettingsIntent(context: Context): Intent =
            Intent(context, AutoScrollControlActivity::class.java)

        fun createReaderIntent(
            context: Context,
            isPlaying: Boolean,
            hasStarted: Boolean,
            isAtBottom: Boolean
        ): Intent =
            Intent(context, AutoScrollControlActivity::class.java).apply {
                putExtra(EXTRA_READER_SESSION_ACTIVE, true)
                putExtra(EXTRA_PLAYING, isPlaying)
                putExtra(EXTRA_STARTED, hasStarted)
                putExtra(EXTRA_AT_BOTTOM, isAtBottom)
            }
    }
}
