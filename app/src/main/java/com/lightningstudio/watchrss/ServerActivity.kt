package com.lightningstudio.watchrss

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import com.lightningstudio.watchrss.ui.screen.ServerScreen
import com.lightningstudio.watchrss.ui.theme.WatchRSSTheme
import com.lightningstudio.watchrss.util.AppLogger
import com.lightningstudio.watchrss.util.LocalHttpServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ServerActivity : BaseWatchActivity() {
    private var server: LocalHttpServer? = null
    private var port by mutableStateOf(0)
    private var synced by mutableStateOf(false)
    private var serverType by mutableStateOf(ServerType.REMOTE_INPUT)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupSystemBars()

        val typeValue = intent.getStringExtra(EXTRA_SERVER_TYPE) ?: ServerType.REMOTE_INPUT.name
        serverType = ServerType.valueOf(typeValue)

        setContent {
            WatchRSSTheme {
                ServerScreen(
                    port = port,
                    synced = synced,
                    serverType = serverType,
                    onDismiss = { finish() }
                )
            }
        }

        startServer()
    }

    private fun startServer() {
        lifecycleScope.launch {
            try {
                val app = application as WatchRssApplication
                server = when (serverType) {
                    ServerType.REMOTE_INPUT -> {
                        LocalHttpServer.createRemoteInputServer(app.container) { url ->
                            handleRemoteInput(url)
                        }
                    }
                    ServerType.SYNC_FAVORITES -> {
                        LocalHttpServer.createSyncFavoritesServer(app.container) {
                            handleSyncComplete()
                        }
                    }
                    ServerType.SYNC_WATCH_LATER -> {
                        LocalHttpServer.createSyncWatchLaterServer(app.container) {
                            handleSyncComplete()
                        }
                    }
                }
                server?.start()
                port = server?.listeningPort ?: 0
            } catch (e: Exception) {
                AppLogger.e("ServerActivity", "Failed to start local server", e)
            }
        }
    }

    private fun handleRemoteInput(url: String) {
        lifecycleScope.launch {
            withContext(Dispatchers.Main) {
                setResult(RESULT_OK, intent.apply {
                    putExtra(EXTRA_REMOTE_URL, url)
                })
                finish()
            }
        }
    }

    private fun handleSyncComplete() {
        lifecycleScope.launch {
            withContext(Dispatchers.Main) {
                synced = true
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        server?.stop()
    }

    companion object {
        const val EXTRA_SERVER_TYPE = "server_type"
        const val EXTRA_REMOTE_URL = "remote_url"
    }

    enum class ServerType {
        REMOTE_INPUT,
        SYNC_FAVORITES,
        SYNC_WATCH_LATER
    }
}
