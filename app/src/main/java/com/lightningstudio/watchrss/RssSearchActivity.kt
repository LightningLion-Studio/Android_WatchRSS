package com.lightningstudio.watchrss

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.lightningstudio.watchrss.ui.screen.common.ReadAloudBubbleDock
import com.lightningstudio.watchrss.ui.screen.common.ReadAloudFloatingBubbleOverlay
import com.lightningstudio.watchrss.ui.screen.rss.RssSearchScreen
import com.lightningstudio.watchrss.ui.theme.WatchRSSTheme
import com.lightningstudio.watchrss.ui.viewmodel.AppViewModelFactory
import com.lightningstudio.watchrss.ui.viewmodel.RssSearchViewModel

class RssSearchActivity : BaseWatchActivity() {
    private val viewModel: RssSearchViewModel by viewModels {
        AppViewModelFactory((application as WatchRssApplication).container)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupSystemBars()

        setContent {
            WatchRSSTheme {
                val keyword by viewModel.keyword.collectAsState()
                val results by viewModel.results.collectAsState()
                val readAloudState by (application as WatchRssApplication)
                    .container
                    .readAloudController
                    .uiState
                    .collectAsState()

                Box(modifier = Modifier.fillMaxSize()) {
                    RssSearchScreen(
                        keyword = keyword,
                        results = results,
                        onKeywordChange = viewModel::updateKeyword,
                        onItemClick = { item ->
                            if (!allowNavigation()) return@RssSearchScreen
                            val intent = Intent(this@RssSearchActivity, DetailActivity::class.java)
                            intent.putExtra(DetailActivity.EXTRA_ITEM_ID, item.id)
                            startActivity(intent)
                        }
                    )
                    ReadAloudFloatingBubbleOverlay(
                        state = readAloudState,
                        defaultDock = ReadAloudBubbleDock.LEFT,
                        onClick = {
                            if (!allowNavigation()) return@ReadAloudFloatingBubbleOverlay
                            startActivity(ReadAloudPlaybackActivity.createIntent(this@RssSearchActivity))
                        }
                    )
                }
            }
        }
    }

    companion object {
        const val EXTRA_CHANNEL_ID = ChannelDetailActivity.EXTRA_CHANNEL_ID

        fun createIntent(context: Context, channelId: Long): Intent {
            return Intent(context, RssSearchActivity::class.java).apply {
                putExtra(EXTRA_CHANNEL_ID, channelId)
            }
        }
    }
}
