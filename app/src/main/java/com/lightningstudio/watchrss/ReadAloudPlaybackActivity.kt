package com.lightningstudio.watchrss

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.lightningstudio.watchrss.ui.screen.rss.ReadAloudPlaybackScreen
import com.lightningstudio.watchrss.ui.theme.WatchRSSTheme

class ReadAloudPlaybackActivity : BaseWatchActivity() {
    private val controller by lazy { (application as WatchRssApplication).container.readAloudController }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupSystemBars()
        setContent {
            WatchRSSTheme {
                val state by controller.uiState.collectAsState()
                ReadAloudPlaybackScreen(
                    state = state,
                    onTogglePlayPause = controller::togglePlayPause,
                    onPrevious = controller::playPrevious,
                    onNext = controller::playNext,
                    onStop = {
                        controller.stop()
                        finish()
                    },
                    onOpenCurrentArticle = state.currentItemId?.let { itemId ->
                        {
                            startActivity(
                                Intent(this, DetailActivity::class.java).apply {
                                    putExtra(DetailActivity.EXTRA_ITEM_ID, itemId)
                                }
                            )
                        }
                    }
                )
            }
        }
    }

    companion object {
        fun createIntent(context: Context): Intent {
            return Intent(context, ReadAloudPlaybackActivity::class.java)
        }
    }
}
