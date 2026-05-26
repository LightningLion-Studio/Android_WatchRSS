package com.lightningstudio.watchrss

import android.content.Intent
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lightningstudio.watchrss.data.rss.BuiltinChannelType
import com.lightningstudio.watchrss.data.rss.ImportedContentIds
import com.lightningstudio.watchrss.ui.components.WatchCircularProgressIndicator
import com.lightningstudio.watchrss.ui.screen.ActionDialogScreen
import com.lightningstudio.watchrss.ui.screen.ActionItem
import com.lightningstudio.watchrss.ui.screen.DeleteConfirmDialog
import com.lightningstudio.watchrss.ui.theme.WatchRSSTheme
import com.lightningstudio.watchrss.ui.viewmodel.AppViewModelFactory
import com.lightningstudio.watchrss.ui.viewmodel.ChannelActionsViewModel

class ChannelActionsActivity : BaseWatchActivity() {
    private val viewModel: ChannelActionsViewModel by viewModels {
        AppViewModelFactory((application as WatchRssApplication).container)
    }
    private var isNavigating by mutableStateOf(false)

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            isNavigating = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupSystemBars()

        if (!viewModel.isValid()) {
            finish()
            return
        }

        val quick = intent.getBooleanExtra(EXTRA_QUICK, false)
        setContent {
            WatchRSSTheme {
                val channel by viewModel.channel.collectAsState()
                val isValid = channel != null
                val pinLabel = if (channel?.isPinned == true) "取消置顶" else "置顶"
                val isBuiltin = channel?.let { BuiltinChannelType.fromUrl(it.url) != null } ?: false
                val canMarkRead = channel?.let { it.unreadCount > 0 && !isBuiltin } ?: false
                val canClear = channel?.url
                    ?.let(ImportedContentIds::isDeletableLocalContentChannel) == true
                var showClearConfirm by remember { mutableStateOf(false) }
                var showDeleteConfirm by remember { mutableStateOf(false) }

                val items = buildList {
                    add(
                        ActionItem(
                            label = "移动到顶部",
                            enabled = isValid,
                            onClick = {
                                viewModel.moveToTop()
                                finish()
                            }
                        )
                    )
                    add(
                        ActionItem(
                            label = pinLabel,
                            enabled = isValid,
                            onClick = {
                                viewModel.togglePinned()
                                finish()
                            }
                        )
                    )
                    add(
                        ActionItem(
                            label = "标记已读",
                            enabled = isValid && canMarkRead,
                            onClick = {
                                viewModel.markRead()
                                finish()
                            }
                        )
                    )
                    if (canClear) {
                        add(
                            ActionItem(
                                label = "清空",
                                enabled = isValid,
                                onClick = { showClearConfirm = true }
                            )
                        )
                    }
                    if (!quick) {
                        add(
                            ActionItem(
                                label = "删除",
                                enabled = isValid,
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
                    if (showClearConfirm) {
                        DeleteConfirmDialog(
                            title = "清空内容",
                            message = "清空后将移除这个频道内的本地条目",
                            onConfirm = {
                                showClearConfirm = false
                                viewModel.clearLocalContent()
                                finish()
                            },
                            onCancel = { showClearConfirm = false }
                        )
                    }
                    if (showDeleteConfirm) {
                        DeleteConfirmDialog(
                            title = "删除频道",
                            message = "删除后将移除本地缓存",
                            onConfirm = {
                                showDeleteConfirm = false
                                viewModel.delete()
                                navigateHome()
                            },
                            onCancel = { showDeleteConfirm = false }
                        )
                    }

                    if (isNavigating) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            WatchCircularProgressIndicator()
                        }
                    }
                }
            }
        }
    }

    companion object {
        const val EXTRA_CHANNEL_ID = "channelId"
        const val EXTRA_QUICK = "quick"
    }

    private fun navigateHome() {
        isNavigating = true
        val intent = HomeFeedListActivity.createIntent(this)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        startActivity(intent)
        finish()
    }
}
