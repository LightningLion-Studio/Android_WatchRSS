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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.lightningstudio.watchrss.ui.screen.DeleteConfirmDialog
import com.lightningstudio.watchrss.ui.screen.bili.BiliSettingsScreen
import com.lightningstudio.watchrss.ui.theme.WatchRSSTheme
import com.lightningstudio.watchrss.ui.viewmodel.BiliSettingsViewModel
import com.lightningstudio.watchrss.ui.viewmodel.BiliViewModelFactory

class BiliSettingsActivity : BaseWatchActivity() {
    private val container by lazy { (application as WatchRssApplication).container }
    private val repository by lazy { container.biliRepository }
    private val rssRepository by lazy { container.rssRepository }
    private val viewModel: BiliSettingsViewModel by viewModels {
        BiliViewModelFactory(repository, rssRepository)
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
                    var showDeleteConfirm by remember { mutableStateOf(false) }

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

                    Box(modifier = Modifier.fillMaxSize()) {
                        BiliSettingsScreen(
                            isLoggedIn = uiState.isLoggedIn,
                            showOriginalContent = uiState.showOriginalContent,
                            originalContentEnabled = uiState.originalContentEnabled,
                            onToggleOriginalContent = viewModel::toggleOriginalContent,
                            deleteEnabled = uiState.deleteEnabled,
                            onDelete = {
                                if (uiState.deleteEnabled) {
                                    showDeleteConfirm = true
                                }
                            },
                            onLogout = viewModel::logout
                        )

                        if (showDeleteConfirm) {
                            DeleteConfirmDialog(
                                title = "删除频道",
                                message = "删除后将移除本地缓存",
                                onConfirm = {
                                    showDeleteConfirm = false
                                    viewModel.deleteChannel()
                                    finish()
                                },
                                onCancel = { showDeleteConfirm = false }
                            )
                        }
                    }
                }
            }
        }
    }

    companion object {
        fun createIntent(context: Context): Intent {
            return Intent(context, BiliSettingsActivity::class.java)
        }
    }
}
