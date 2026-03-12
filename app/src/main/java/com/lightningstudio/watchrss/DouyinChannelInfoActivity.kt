package com.lightningstudio.watchrss

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.lightningstudio.watchrss.data.rss.BuiltinChannelType
import com.lightningstudio.watchrss.ui.screen.douyin.DouyinChannelInfoScreen
import com.lightningstudio.watchrss.ui.theme.WatchRSSTheme
import kotlinx.coroutines.launch

class DouyinChannelInfoActivity : BaseWatchActivity() {
    private val container by lazy { (application as WatchRssApplication).container }
    private val repository by lazy { container.douyinRepository }
    private val rssRepository by lazy { container.rssRepository }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupSystemBars()

        setContent {
            WatchRSSTheme {
                val baseDensity = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(2f, baseDensity.fontScale)) {
                    val context = LocalContext.current
                    val scope = rememberCoroutineScope()
                    val channels by rssRepository.observeChannels().collectAsState(initial = emptyList())
                    val channel = channels.firstOrNull { it.url == BuiltinChannelType.DOUYIN.url }
                    val isLoggedIn by produceState(initialValue = false) {
                        value = repository.isLoggedIn()
                    }

                    LaunchedEffect(Unit) {
                        rssRepository.ensureBuiltinChannels()
                    }

                    DouyinChannelInfoScreen(
                        isLoggedIn = isLoggedIn,
                        lastRefreshAt = channel?.lastFetchedAt,
                        onLoginClick = { context.startActivity(DouyinLoginActivity.createIntent(context)) },
                        onOpenSettings = { context.startActivity(DouyinSettingsActivity.createIntent(context)) },
                        onSearchClick = { },
                        onShareClick = {
                            shareDouyinChannel(context)
                        },
                        onMarkReadClick = {
                            val channelId = channel?.id ?: return@DouyinChannelInfoScreen
                            scope.launch {
                                rssRepository.markChannelRead(channelId)
                                com.lightningstudio.watchrss.ui.util.showAppToast(context, "已标记为已读", android.widget.Toast.LENGTH_SHORT)
                            }
                        },
                        markReadEnabled = channel != null
                    )
                }
            }
        }
    }

    companion object {
        fun createIntent(context: Context): Intent {
            return Intent(context, DouyinChannelInfoActivity::class.java)
        }
    }
}

private fun shareDouyinChannel(context: Context) {
    val text = "抖音\nhttps://www.douyin.com"
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(intent, "分享"))
}
