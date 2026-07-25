package com.lightningstudio.watchrss

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.lightningstudio.watchrss.ui.screen.rss.shareCurrent
import com.lightningstudio.watchrss.ui.screen.rss.showShareQr
import com.lightningstudio.watchrss.ui.screen.ActionDialogScreen
import com.lightningstudio.watchrss.ui.screen.ActionItem
import com.lightningstudio.watchrss.ui.screen.DeleteConfirmDialog
import com.lightningstudio.watchrss.ui.theme.WatchRSSTheme
import com.lightningstudio.watchrss.ui.util.isSystemShareSettingSupported
import com.lightningstudio.watchrss.ui.viewmodel.AppViewModelFactory
import com.lightningstudio.watchrss.ui.viewmodel.ItemActionsViewModel

class ItemActionsActivity : BaseWatchActivity() {
    private val container by lazy { (application as WatchRssApplication).container }
    private val settingsRepository by lazy { container.settingsRepository }
    private val viewModel: ItemActionsViewModel by viewModels {
        AppViewModelFactory(container)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupSystemBars()

        if (!viewModel.isValid()) {
            finish()
            return
        }

        val fallbackTitle = intent.getStringExtra(EXTRA_ITEM_TITLE)?.trim().orEmpty()

        setContent {
            WatchRSSTheme {
                val context = LocalContext.current
                val item by viewModel.item.collectAsState()
                val savedState by viewModel.savedState.collectAsState()
                val canDeleteItem by viewModel.canDeleteItem.collectAsState()
                val shareUseSystem by settingsRepository.shareUseSystem.collectAsState(initial = false)
                var showDeleteConfirm by remember { mutableStateOf(false) }
                val useSystemShare = remember(context, shareUseSystem) {
                    shareUseSystem && isSystemShareSettingSupported(context)
                }
                val favoriteLabel = if (savedState.isFavorite) "取消收藏" else "收藏"
                val laterLabel = if (savedState.isWatchLater) "取消稍后再看" else "稍后再看"
                val shareTitle = item?.title?.trim().orEmpty().ifBlank { fallbackTitle }
                val shareLink = item?.link?.trim().orEmpty().ifBlank { null }
                val shareEnabled = if (useSystemShare) {
                    shareTitle.isNotBlank() || shareLink != null
                } else {
                    shareLink != null
                }

                val items = buildList {
                    add(
                        ActionItem(
                            label = favoriteLabel,
                            onClick = {
                                viewModel.toggleFavorite()
                                finish()
                            }
                        )
                    )
                    add(
                        ActionItem(
                            label = laterLabel,
                            onClick = {
                                viewModel.toggleWatchLater()
                                finish()
                            }
                        )
                    )
                    add(
                        ActionItem(
                            label = "分享",
                            enabled = shareEnabled,
                            onClick = {
                                if (useSystemShare) {
                                    shareCurrent(context, shareTitle, shareLink)
                                } else {
                                    showShareQr(context, shareTitle, shareLink)
                                }
                                finish()
                            }
                        )
                    )
                    if (canDeleteItem) {
                        add(
                            ActionItem(
                                label = "删除",
                                onClick = { showDeleteConfirm = true }
                            )
                        )
                    }
                    add(
                        ActionItem(
                            label = "取消",
                            onClick = { finish() }
                        )
                    )
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    ActionDialogScreen(
                        items = items,
                        extraTopPadding = 4.dp
                    )
                    if (showDeleteConfirm) {
                        DeleteConfirmDialog(
                            title = "删除内容",
                            message = "删除后会从本机和下次同步中移除此内容",
                            onConfirm = {
                                showDeleteConfirm = false
                                viewModel.deleteItem()
                                finish()
                            },
                            onCancel = { showDeleteConfirm = false }
                        )
                    }
                }
            }
        }
    }

    companion object {
        const val EXTRA_ITEM_ID = "itemId"
        const val EXTRA_ITEM_TITLE = "itemTitle"
    }
}
