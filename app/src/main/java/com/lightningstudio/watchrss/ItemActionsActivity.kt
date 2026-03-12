package com.lightningstudio.watchrss

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.lightningstudio.watchrss.ui.screen.ActionDialogScreen
import com.lightningstudio.watchrss.ui.screen.ActionItem
import com.lightningstudio.watchrss.ui.theme.WatchRSSTheme
import com.lightningstudio.watchrss.ui.viewmodel.AppViewModelFactory
import com.lightningstudio.watchrss.ui.viewmodel.ItemActionsViewModel

class ItemActionsActivity : BaseWatchActivity() {
    private val viewModel: ItemActionsViewModel by viewModels {
        AppViewModelFactory((application as WatchRssApplication).container)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupSystemBars()

        if (!viewModel.isValid()) {
            finish()
            return
        }

        setContent {
            WatchRSSTheme {
                val savedState by viewModel.savedState.collectAsState()
                val favoriteLabel = if (savedState.isFavorite) "取消收藏" else "收藏"
                val laterLabel = if (savedState.isWatchLater) "取消稍后再看" else "稍后再看"

                val items = listOf(
                    ActionItem(
                        label = favoriteLabel,
                        onClick = {
                            viewModel.toggleFavorite()
                            finish()
                        }
                    ),
                    ActionItem(
                        label = laterLabel,
                        onClick = {
                            viewModel.toggleWatchLater()
                            finish()
                        }
                    ),
                    ActionItem(
                        label = "取消",
                        onClick = { finish() }
                    )
                )

                ActionDialogScreen(items = items)
            }
        }
    }

    companion object {
        const val EXTRA_ITEM_ID = "itemId"
        const val EXTRA_ITEM_TITLE = "itemTitle"
    }
}
