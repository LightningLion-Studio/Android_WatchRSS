package com.lightningstudio.watchrss

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.lightningstudio.watchrss.data.rss.BuiltinChannelType
import com.lightningstudio.watchrss.data.rss.RssChannel
import com.lightningstudio.watchrss.data.settings.CURRENT_OOBE_VERSION
import com.lightningstudio.watchrss.ui.screen.home.HomeComposeScreen
import com.lightningstudio.watchrss.ui.theme.WatchRSSTheme
import com.lightningstudio.watchrss.ui.viewmodel.AppViewModelFactory
import com.lightningstudio.watchrss.ui.viewmodel.HomeViewModel
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : BaseWatchActivity() {
    private val viewModel: HomeViewModel by viewModels {
        AppViewModelFactory((application as WatchRssApplication).container)
    }

    private var openSwipeKey by mutableStateOf<Long?>(null)
    private var draggingSwipeKey by mutableStateOf<Long?>(null)
    private var keepSplashOnScreen = true

    override fun onSwipeBackAttempt(dx: Float, dy: Float): Boolean {
        val hasOpen = openSwipeKey != null
        if (hasOpen) {
            openSwipeKey = null
        }
        return hasOpen
    }

    override fun onResume() {
        super.onResume()
        closeOpenSwipe()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen().setKeepOnScreenCondition { keepSplashOnScreen }
        super.onCreate(savedInstanceState)

        lifecycleScope.launch {
            val settingsRepository = (application as WatchRssApplication).container.settingsRepository
            val shouldShowOobe = settingsRepository.oobeSeenVersion.first() < CURRENT_OOBE_VERSION
            keepSplashOnScreen = false
            if (shouldShowOobe) {
                startActivity(OobeActivity.createIntent(this@MainActivity))
                finish()
                return@launch
            }
            setupSystemBars()
            renderHomeContent()
        }
    }

    private fun renderHomeContent() {
        setContent {
            WatchRSSTheme {
                val context = LocalContext.current
                val channels by viewModel.channels.collectAsState()
                val message by viewModel.message.collectAsState()

                LaunchedEffect(message) {
                    if (message != null) {
                        com.lightningstudio.watchrss.ui.util.showAppToast(context, message, android.widget.Toast.LENGTH_SHORT)
                        viewModel.clearMessage()
                    }
                }

                HomeComposeScreen(
                    channels = channels,
                    openSwipeId = openSwipeKey,
                    onOpenSwipe = { openSwipeKey = it },
                    onCloseSwipe = { openSwipeKey = null },
                    draggingSwipeId = draggingSwipeKey,
                    onDragStart = { draggingSwipeKey = it },
                    onDragEnd = { draggingSwipeKey = null },
                    onProfileClick = {
                        if (!allowNavigation()) return@HomeComposeScreen
                        startActivity(Intent(this, ProfileActivity::class.java))
                    },
                    onRecommendClick = {
                        if (closeOpenSwipe()) return@HomeComposeScreen
                        if (!allowNavigation()) return@HomeComposeScreen
                        startActivity(Intent(this, RssRecommendActivity::class.java))
                    },
                    onChannelClick = { channel ->
                        if (closeOpenSwipe()) return@HomeComposeScreen
                        if (!allowNavigation()) return@HomeComposeScreen
                        openChannel(channel)
                    },
                    onChannelLongClick = { channel ->
                        showChannelActions(channel, quick = false)
                    },
                    onAddRssClick = {
                        if (!allowNavigation()) return@HomeComposeScreen
                        startActivity(Intent(this, AddRssActivity::class.java))
                    },
                    onMoveTopClick = { channel ->
                        closeOpenSwipe()
                        viewModel.moveToTop(channel)
                    },
                    onMarkReadClick = { channel ->
                        closeOpenSwipe()
                        viewModel.markChannelRead(channel)
                    },
                    onBeianClick = {
                        if (!allowNavigation()) return@HomeComposeScreen
                        startActivity(BeianActivity.createIntent(this))
                    }
                )
            }
        }
    }

    private fun closeOpenSwipe(): Boolean {
        val hasOpen = openSwipeKey != null
        if (hasOpen) {
            openSwipeKey = null
        }
        return hasOpen
    }

    private fun showChannelActions(channel: RssChannel, quick: Boolean) {
        val intent = Intent(this, ChannelActionsActivity::class.java)
        intent.putExtra(ChannelActionsActivity.EXTRA_CHANNEL_ID, channel.id)
        intent.putExtra(ChannelActionsActivity.EXTRA_QUICK, quick)
        startActivity(intent)
    }

    private fun openChannel(channel: RssChannel) {
        when (BuiltinChannelType.fromUrl(channel.url)) {
            BuiltinChannelType.BILI -> startActivity(Intent(this, BiliEntryActivity::class.java))
            BuiltinChannelType.DOUYIN -> startActivity(Intent(this, DouyinEntryActivity::class.java))
            null -> {
                val intent = Intent(this, FeedActivity::class.java)
                intent.putExtra(FeedActivity.EXTRA_CHANNEL_ID, channel.id)
                startActivity(intent)
            }
        }
    }
}
