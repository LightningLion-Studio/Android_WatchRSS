package com.lightningstudio.watchrss

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.lightningstudio.watchrss.data.rss.BuiltinChannelType
import com.lightningstudio.watchrss.ui.screen.DeleteConfirmDialog
import com.lightningstudio.watchrss.ui.screen.rss.ChannelSettingsScreen
import com.lightningstudio.watchrss.ui.theme.WatchRSSTheme
import com.lightningstudio.watchrss.ui.viewmodel.AppViewModelFactory
import com.lightningstudio.watchrss.ui.viewmodel.ChannelDetailViewModel

class ChannelSettingsActivity : BaseWatchActivity() {
    private val viewModel: ChannelDetailViewModel by viewModels {
        AppViewModelFactory((application as WatchRssApplication).container)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupSystemBars()

        setContent {
            WatchRSSTheme {
                val channel by viewModel.channel.collectAsState()
                val isBuiltin = channel?.let { BuiltinChannelType.fromUrl(it.url) != null } ?: false
                val showOriginalContent = channel != null && !isBuiltin
                val originalContentEnabled = channel?.useOriginalContent ?: false
                val deleteEnabled = channel != null
                var showDeleteConfirm by remember { mutableStateOf(false) }

                Box(modifier = Modifier.fillMaxSize()) {
                    ChannelSettingsScreen(
                        showOriginalContent = showOriginalContent,
                        originalContentEnabled = originalContentEnabled,
                        onToggleOriginalContent = {
                            if (showOriginalContent) {
                                viewModel.setOriginalContentEnabled(!originalContentEnabled)
                            }
                        },
                        deleteEnabled = deleteEnabled,
                        onDelete = {
                            if (deleteEnabled) {
                                showDeleteConfirm = true
                            }
                        }
                    )

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
                }
            }
        }
    }

    companion object {
        const val EXTRA_CHANNEL_ID = ChannelDetailActivity.EXTRA_CHANNEL_ID
    }

    private fun navigateHome() {
        val intent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        startActivity(intent)
        finish()
    }
}
