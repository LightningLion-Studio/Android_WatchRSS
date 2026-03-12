package com.lightningstudio.watchrss

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.lightningstudio.watchrss.ui.screen.bili.BiliListScreen
import com.lightningstudio.watchrss.ui.theme.WatchRSSTheme
import com.lightningstudio.watchrss.ui.viewmodel.BiliListType
import com.lightningstudio.watchrss.ui.viewmodel.BiliListViewModel
import com.lightningstudio.watchrss.ui.viewmodel.BiliViewModelFactory

class BiliListActivity : BaseWatchActivity() {
    private val repository by lazy { (application as WatchRssApplication).container.biliRepository }
    private val viewModel: BiliListViewModel by viewModels {
        BiliViewModelFactory(repository)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupSystemBars()

        setContent {
            WatchRSSTheme {
                val baseDensity = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(2f, baseDensity.fontScale)) {
                val context = LocalContext.current
                val uiState by viewModel.uiState.collectAsState()

                LaunchedEffect(uiState.message) {
                    val message = uiState.message
                    if (!message.isNullOrBlank()) {
                        com.lightningstudio.watchrss.ui.util.showAppToast(context, message, android.widget.Toast.LENGTH_SHORT)
                        viewModel.clearMessage()
                    }
                }

                BiliListScreen(
                    uiState = uiState,
                    onRefresh = viewModel::refresh,
                    onLoadMore = viewModel::loadMore,
                    onItemClick = { item ->
                        context.startActivity(
                        BiliDetailActivity.createIntent(context, item.aid, item.bvid, item.cid)
                    )
                }
            )
                }
            }
        }
    }

    companion object {
        private const val EXTRA_TYPE = "type"

        fun createIntent(context: Context, type: BiliListType): Intent {
            return Intent(context, BiliListActivity::class.java).apply {
                putExtra(EXTRA_TYPE, type.id)
            }
        }
    }
}
