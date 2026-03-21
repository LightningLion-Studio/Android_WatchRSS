package com.lightningstudio.watchrss

import android.os.Bundle
import android.content.Intent
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import com.lightningstudio.watchrss.phoneconnection.PhoneConnectionAbility
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
    private var screenTitle by mutableStateOf("")
    private var screenHint by mutableStateOf("")
    private var returnRemoteInputToCaller by mutableStateOf(false)
    private var preferredAbility: PhoneConnectionAbility? by mutableStateOf(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupSystemBars()

        val typeValue = intent.getStringExtra(EXTRA_SERVER_TYPE) ?: ServerType.REMOTE_INPUT.name
        serverType = ServerType.valueOf(typeValue)
        preferredAbility = PhoneConnectionAbility.fromNameOrNull(intent.getStringExtra(EXTRA_PREFERRED_ABILITY))
        screenTitle = intent.getStringExtra(EXTRA_SCREEN_TITLE).orEmpty()
        screenHint = intent.getStringExtra(EXTRA_SCREEN_HINT).orEmpty()
        returnRemoteInputToCaller = intent.getBooleanExtra(EXTRA_RETURN_REMOTE_URL, false)

        setContent {
            WatchRSSTheme {
                ServerScreen(
                    port = port,
                    synced = synced,
                    title = resolveTitle(),
                    hint = resolveHint(),
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
                    ServerType.CONNECTION_HUB -> {
                        LocalHttpServer.createConnectionHubServer(
                            container = app.container,
                            preferredAbility = preferredAbility,
                            onRemoteInput = { url -> handleRemoteInput(url) },
                            onSyncComplete = { handleSyncComplete() }
                        )
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
                if (returnRemoteInputToCaller) {
                    setResult(RESULT_OK, intent.apply {
                        putExtra(EXTRA_REMOTE_URL, url)
                    })
                } else {
                    startActivity(
                        Intent(this@ServerActivity, AddRssActivity::class.java).apply {
                            putExtra(AddRssActivity.EXTRA_URL, url)
                        }
                    )
                }
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
        const val EXTRA_PREFERRED_ABILITY = "preferred_ability"
        const val EXTRA_SCREEN_TITLE = "screen_title"
        const val EXTRA_SCREEN_HINT = "screen_hint"
        const val EXTRA_RETURN_REMOTE_URL = "return_remote_url"
    }

    enum class ServerType {
        REMOTE_INPUT,
        SYNC_FAVORITES,
        SYNC_WATCH_LATER,
        CONNECTION_HUB
    }

    private fun resolveTitle(): String {
        if (screenTitle.isNotBlank()) return screenTitle
        return when (serverType) {
            ServerType.REMOTE_INPUT -> "从手机输入"
            ServerType.SYNC_FAVORITES -> "同步收藏"
            ServerType.SYNC_WATCH_LATER -> "同步稍后再看"
            ServerType.CONNECTION_HUB -> "连接手机"
        }
    }

    private fun resolveHint(): String {
        if (screenHint.isNotBlank()) return screenHint
        return when (serverType) {
            ServerType.REMOTE_INPUT -> "请使用手机版扫描下方二维码"
            ServerType.SYNC_FAVORITES -> "请使用手机版扫描下方二维码"
            ServerType.SYNC_WATCH_LATER -> "请使用手机版扫描下方二维码"
            ServerType.CONNECTION_HUB -> "请先让手表与手机位于同一 WiFi，或让手表连接到手机热点，再使用手机版扫码"
        }
    }
}
