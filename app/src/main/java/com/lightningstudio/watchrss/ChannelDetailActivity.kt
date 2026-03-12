package com.lightningstudio.watchrss

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import com.lightningstudio.watchrss.ui.screen.rss.ChannelDetailScreen
import com.lightningstudio.watchrss.ui.theme.WatchRSSTheme
import com.lightningstudio.watchrss.ui.viewmodel.AppViewModelFactory
import com.lightningstudio.watchrss.ui.viewmodel.ChannelDetailViewModel

class ChannelDetailActivity : BaseWatchActivity() {
    private val settingsRepository by lazy { (application as WatchRssApplication).container.settingsRepository }
    private val viewModel: ChannelDetailViewModel by viewModels {
        AppViewModelFactory((application as WatchRssApplication).container)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupSystemBars()

        val channelId = intent.getLongExtra(EXTRA_CHANNEL_ID, 0L)

        setContent {
            WatchRSSTheme {
                val context = LocalContext.current
                val channel by viewModel.channel.collectAsState()
                val shareUseSystem by settingsRepository.shareUseSystem.collectAsState(initial = false)
                ChannelDetailScreen(
                    channel = channel,
                    onOpenSettings = {
                        if (channelId <= 0L) return@ChannelDetailScreen
                        val intent = Intent(this, ChannelSettingsActivity::class.java)
                        intent.putExtra(ChannelSettingsActivity.EXTRA_CHANNEL_ID, channelId)
                        startActivity(intent)
                    },
                    onSearch = {
                        if (channelId <= 0L) return@ChannelDetailScreen
                        val intent = RssSearchActivity.createIntent(this, channelId)
                        startActivity(intent)
                    },
                    onMarkRead = viewModel::markRead,
                    onShare = {
                        val title = channel?.title
                        val link = channel?.url
                        if (shareUseSystem) {
                            shareCurrent(context, title, link)
                        } else {
                            showShareQr(context, title, link)
                        }
                    }
                )
            }
        }
    }

    companion object {
        const val EXTRA_CHANNEL_ID = "channelId"
    }
}

private fun shareCurrent(context: Context, title: String?, link: String?) {
    val safeTitle = title?.trim().orEmpty()
    val safeLink = link?.trim().orEmpty()
    if (safeTitle.isEmpty() && safeLink.isEmpty()) return
    val text = if (safeTitle.isNotEmpty() && safeLink.isNotEmpty()) {
        "$safeTitle\n$safeLink"
    } else if (safeTitle.isNotEmpty()) {
        safeTitle
    } else {
        safeLink
    }
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(intent, "分享"))
}

private fun showShareQr(context: Context, title: String?, link: String?) {
    val safeTitle = title?.trim().orEmpty()
    val safeLink = link?.trim().orEmpty()
    if (safeLink.isEmpty()) {
        com.lightningstudio.watchrss.ui.util.showAppToast(context, "暂无可分享链接", android.widget.Toast.LENGTH_SHORT)
        return
    }
    context.startActivity(ShareQrActivity.createIntent(context, safeTitle, safeLink))
}
