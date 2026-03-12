package com.lightningstudio.watchrss

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.lifecycleScope
import com.lightningstudio.watchrss.ui.screen.rss.AddRssScreen
import com.lightningstudio.watchrss.ui.theme.WatchRSSTheme
import com.lightningstudio.watchrss.ui.viewmodel.AddRssViewModel
import com.lightningstudio.watchrss.ui.viewmodel.AppViewModelFactory
import com.lightningstudio.watchrss.util.AppLogger
import com.lightningstudio.watchrss.util.LocalHttpServer
import com.lightningstudio.watchrss.util.NetworkUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AddRssActivity : BaseWatchActivity() {
    private val viewModel: AddRssViewModel by viewModels {
        AppViewModelFactory((application as WatchRssApplication).container)
    }

    private val settingsRepository by lazy {
        (application as WatchRssApplication).container.settingsRepository
    }

    private var server: LocalHttpServer? = null

    private val remoteInputLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                val url = result.data?.getStringExtra(ServerActivity.EXTRA_REMOTE_URL)
                if (!url.isNullOrBlank()) {
                    viewModel.updateUrl(url)
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupSystemBars()

        val presetUrl = intent.getStringExtra(EXTRA_URL)?.trim().orEmpty()
        if (presetUrl.isNotEmpty()) {
            viewModel.updateUrl(presetUrl)
        }

        setContent {
            WatchRSSTheme {
                val phoneConnectionEnabled by settingsRepository.phoneConnectionEnabled.collectAsState(initial = true)
                AddRssScreen(
                    uiState = viewModel.uiState,
                    showRemoteInputButton = phoneConnectionEnabled,
                    onUrlChange = viewModel::updateUrl,
                    onSubmit = viewModel::submit,
                    onConfirm = viewModel::confirmAdd,
                    onBack = { finish() },
                    onBackToInput = viewModel::backToInput,
                    onOpenExisting = { existing ->
                        openChannel(existing.url, existing.id)
                    },
                    onChannelAdded = { url, channelId ->
                        openChannel(url, channelId)
                    },
                    onConsumed = viewModel::consumeCreatedChannel,
                    onClearError = viewModel::clearError,
                    onRemoteInput = { startRemoteInput() }
                )
            }
        }
    }

    private fun startRemoteInput() {
        if (!allowNavigation()) return

        lifecycleScope.launch {
            try {
                val app = application as WatchRssApplication
                server = LocalHttpServer.createRemoteInputServer(app.container) { url ->
                    handleRemoteInput(url)
                }
                server?.start()
                val port = server?.listeningPort ?: 0

                if (port > 0) {
                    val ipAddress = withContext(Dispatchers.IO) {
                        NetworkUtils.getLocalIpAddress(this@AddRssActivity)
                    }
                    if (ipAddress != null) {
                        val serverAddress = "$ipAddress:$port"
                        viewModel.showQrCode(serverAddress)
                    } else {
                        AppLogger.e("AddRssActivity", "Failed to get local IP address")
                    }
                }
            } catch (e: Exception) {
                AppLogger.e("AddRssActivity", "Failed to start local server", e)
            }
        }
    }

    private fun handleRemoteInput(url: String) {
        lifecycleScope.launch {
            withContext(Dispatchers.Main) {
                viewModel.updateUrl(url)
                viewModel.backToInput()
                server?.stop()
                server = null
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        server?.stop()
    }

    private fun openChannel(url: String?, channelId: Long) {
        val builtin = com.lightningstudio.watchrss.data.rss.BuiltinChannelType.fromUrl(url)
            ?: builtinFromInputUrl(url)
        when (builtin) {
            com.lightningstudio.watchrss.data.rss.BuiltinChannelType.BILI -> {
                startActivity(Intent(this, BiliEntryActivity::class.java))
            }
            com.lightningstudio.watchrss.data.rss.BuiltinChannelType.DOUYIN -> {
                startActivity(Intent(this, DouyinEntryActivity::class.java))
            }
            null -> {
                val intent = Intent(this, FeedActivity::class.java)
                intent.putExtra(FeedActivity.EXTRA_CHANNEL_ID, channelId)
                startActivity(intent)
            }
        }
        finish()
    }

    private fun builtinFromInputUrl(url: String?): com.lightningstudio.watchrss.data.rss.BuiltinChannelType? {
        if (url.isNullOrBlank()) return null
        val host = runCatching { Uri.parse(url).host }.getOrNull()
        return com.lightningstudio.watchrss.data.rss.BuiltinChannelType.fromHost(host)
    }

    companion object {
        const val EXTRA_URL = "extra_url"
    }
}
