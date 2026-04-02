package com.lightningstudio.watchrss

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.lightningstudio.watchrss.data.rss.BuiltinChannelType
import com.lightningstudio.watchrss.ui.components.WatchCircularProgressIndicator
import com.lightningstudio.watchrss.ui.screen.douyin.DouyinChannelInfoScreen
import com.lightningstudio.watchrss.ui.theme.WatchRSSTheme
import kotlinx.coroutines.launch

class DouyinChannelInfoActivity : BaseWatchActivity() {
    private val container by lazy { (application as WatchRssApplication).container }
    private val repository by lazy { container.douyinRepository }
    private val rssRepository by lazy { container.rssRepository }
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

                    Box(modifier = Modifier.fillMaxSize()) {
                        DouyinChannelInfoScreen(
                            isLoggedIn = isLoggedIn,
                            lastRefreshAt = channel?.lastFetchedAt,
                            onLoginClick = {
                                isNavigating = true
                                DouyinLoginActivity.open(context)
                            },
                            onOpenSettings = {
                                isNavigating = true
                                context.startActivity(DouyinSettingsActivity.createIntent(context))
                            },
                            onOpenHistory = {
                                isNavigating = true
                                context.startActivity(DouyinHistoryActivity.createIntent(context))
                            },
                            onMarkReadClick = {
                                val channelId = channel?.id ?: return@DouyinChannelInfoScreen
                                scope.launch {
                                    rssRepository.markChannelRead(channelId)
                                    com.lightningstudio.watchrss.ui.util.showAppToast(context, "已标记为已读", android.widget.Toast.LENGTH_SHORT)
                                }
                            },
                            markReadEnabled = false
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
    }

    companion object {
        fun createIntent(context: Context): Intent {
            return Intent(context, DouyinChannelInfoActivity::class.java)
        }
    }
}
