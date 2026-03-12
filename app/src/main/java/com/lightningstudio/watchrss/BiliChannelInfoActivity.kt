package com.lightningstudio.watchrss

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.lightningstudio.watchrss.ui.screen.bili.BiliChannelInfoScreen
import com.lightningstudio.watchrss.ui.theme.WatchRSSTheme
import com.lightningstudio.watchrss.ui.viewmodel.BiliFeedViewModel
import com.lightningstudio.watchrss.ui.viewmodel.BiliListType
import com.lightningstudio.watchrss.ui.viewmodel.BiliViewModelFactory

class BiliChannelInfoActivity : BaseWatchActivity() {
    private val repository by lazy { (application as WatchRssApplication).container.biliRepository }
    private val viewModel: BiliFeedViewModel by viewModels {
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
                val lifecycleOwner = LocalLifecycleOwner.current

                LaunchedEffect(Unit) {
                    viewModel.refreshLoginState()
                }
                DisposableEffect(lifecycleOwner) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_RESUME) {
                            viewModel.refreshLoginState()
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
                }
                LaunchedEffect(uiState.message) {
                    val message = uiState.message
                    if (!message.isNullOrBlank()) {
                        com.lightningstudio.watchrss.ui.util.showAppToast(context, message, android.widget.Toast.LENGTH_SHORT)
                        viewModel.clearMessage()
                    }
                }

                BiliChannelInfoScreen(
                    isLoggedIn = uiState.isLoggedIn,
                    lastRefreshAt = uiState.lastRefreshAt,
                    onLoginClick = { context.startActivity(BiliLoginActivity.createIntent(context)) },
                    onSearchClick = {
                        context.startActivity(BiliSearchActivity.createIntent(context))
                    },
                    onOpenWatchLater = {
                        context.startActivity(BiliListActivity.createIntent(context, BiliListType.WATCH_LATER))
                    },
                    onOpenHistory = {
                        context.startActivity(BiliListActivity.createIntent(context, BiliListType.HISTORY))
                    },
                    onOpenFavorites = {
                        context.startActivity(BiliListActivity.createIntent(context, BiliListType.FAVORITE))
                    },
                    onOpenSettings = {
                        context.startActivity(BiliSettingsActivity.createIntent(context))
                    }
                )
                }
            }
        }
    }

    companion object {
        fun createIntent(context: Context): Intent {
            return Intent(context, BiliChannelInfoActivity::class.java)
        }
    }
}
