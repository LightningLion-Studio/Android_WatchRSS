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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.lightningstudio.watchrss.ui.components.WatchCircularProgressIndicator
import com.lightningstudio.watchrss.ui.screen.rss.ChannelDetailScreen
import com.lightningstudio.watchrss.ui.theme.WatchRSSTheme
import com.lightningstudio.watchrss.ui.util.isSystemShareSettingSupported
import com.lightningstudio.watchrss.ui.viewmodel.AppViewModelFactory
import com.lightningstudio.watchrss.ui.viewmodel.ChannelDetailViewModel

private const val CHANNEL_SHARE_QR_HINT =
    "请在扫码后选择分享给RSS阅读器\n或复制链接粘贴到RSS阅读器里"

class ChannelDetailActivity : BaseWatchActivity() {
    private val settingsRepository by lazy { (application as WatchRssApplication).container.settingsRepository }
    private val viewModel: ChannelDetailViewModel by viewModels {
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

        val channelId = intent.getLongExtra(EXTRA_CHANNEL_ID, 0L)

        setContent {
            WatchRSSTheme {
                val context = LocalContext.current
                val channel by viewModel.channel.collectAsState()
                val shareUseSystem by settingsRepository.shareUseSystem.collectAsState(initial = false)
                val useSystemShare = remember(context, shareUseSystem) {
                    shareUseSystem && isSystemShareSettingSupported(context)
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    ChannelDetailScreen(
                        channel = channel,
                        onOpenSettings = {
                            if (channelId <= 0L) return@ChannelDetailScreen
                            isNavigating = true
                            val intent = Intent(this@ChannelDetailActivity, ChannelSettingsActivity::class.java)
                            intent.putExtra(ChannelSettingsActivity.EXTRA_CHANNEL_ID, channelId)
                            startActivity(intent)
                        },
                        onSearch = {
                            if (channelId <= 0L) return@ChannelDetailScreen
                            isNavigating = true
                            val intent = RssSearchActivity.createIntent(this@ChannelDetailActivity, channelId)
                            startActivity(intent)
                        },
                        onMarkRead = viewModel::markRead,
                        onShare = {
                            val title = channel?.title
                            val link = channel?.url
                            if (useSystemShare) {
                                shareCurrent(context, title, link)
                            } else {
                                showShareQr(context, title, link)
                            }
                        }
                    )

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
    context.startActivity(
        ShareQrActivity.createIntent(
            context = context,
            title = safeTitle,
            link = safeLink,
            topHint = CHANNEL_SHARE_QR_HINT
        )
    )
}
