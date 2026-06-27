package com.lightningstudio.watchrss

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.lightningstudio.watchrss.data.settings.DEFAULT_MEDIA_VOLUME_CONTROL_ENABLED
import com.lightningstudio.watchrss.data.settings.DEFAULT_MEDIA_VOLUME_GUARD_ENABLED
import com.lightningstudio.watchrss.ui.screen.rss.ReadAloudPlaybackScreen
import com.lightningstudio.watchrss.ui.theme.WatchRSSTheme

class ReadAloudPlaybackActivity : BaseWatchActivity() {
    private val container by lazy { (application as WatchRssApplication).container }
    private val controller by lazy { container.readAloudController }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupSystemBars()
        val returnToArticle = intent.getBooleanExtra(EXTRA_RETURN_TO_ARTICLE, false)
        setContent {
            WatchRSSTheme {
                val state by controller.uiState.collectAsState()
                val volumeControlEnabled by container.settingsRepository.mediaVolumeControlEnabled.collectAsState(
                    initial = DEFAULT_MEDIA_VOLUME_CONTROL_ENABLED
                )
                val volumeGuardEnabled by container.settingsRepository.mediaVolumeGuardEnabled.collectAsState(
                    initial = DEFAULT_MEDIA_VOLUME_GUARD_ENABLED
                )
                ReadAloudPlaybackScreen(
                    state = state,
                    onTogglePlayPause = controller::togglePlayPause,
                    onPrevious = controller::playPrevious,
                    onNext = controller::playNext,
                    onStop = {
                        controller.stop()
                        finish()
                    },
                    onToggleAutoAdvance = controller::toggleAutoAdvance,
                    onDecreaseSpeechRate = controller::decreaseSpeechRate,
                    onIncreaseSpeechRate = controller::increaseSpeechRate,
                    digitalCrownVolumeEnabled = volumeControlEnabled,
                    volumeGuardEnabled = volumeGuardEnabled,
                    currentArticleActionText = if (returnToArticle) {
                        "返回文章"
                    } else {
                        "查看当前文章"
                    },
                    onOpenCurrentArticle = if (returnToArticle) {
                        { finish() }
                    } else {
                        state.currentItemId?.let { itemId ->
                            {
                                startActivity(
                                    Intent(this, DetailActivity::class.java).apply {
                                        putExtra(DetailActivity.EXTRA_ITEM_ID, itemId)
                                    }
                                )
                            }
                        }
                    }
                )
            }
        }
    }

    companion object {
        private const val EXTRA_RETURN_TO_ARTICLE = "returnToArticle"

        fun createIntent(context: Context, returnToArticle: Boolean = false): Intent {
            return Intent(context, ReadAloudPlaybackActivity::class.java).apply {
                putExtra(EXTRA_RETURN_TO_ARTICLE, returnToArticle)
            }
        }
    }
}
