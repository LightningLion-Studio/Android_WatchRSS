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
import com.lightningstudio.watchrss.phoneconnection.LocalHttpServer
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
    private var llmSummaryItemId by mutableStateOf(0L)
    private var readAloudItemId by mutableStateOf(0L)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupSystemBars()

        val typeValue = intent.getStringExtra(EXTRA_SERVER_TYPE) ?: ServerType.REMOTE_INPUT.name
        serverType = ServerType.valueOf(typeValue)
        preferredAbility = PhoneConnectionAbility.fromNameOrNull(intent.getStringExtra(EXTRA_PREFERRED_ABILITY))
        screenTitle = intent.getStringExtra(EXTRA_SCREEN_TITLE).orEmpty()
        screenHint = intent.getStringExtra(EXTRA_SCREEN_HINT).orEmpty()
        returnRemoteInputToCaller = intent.getBooleanExtra(EXTRA_RETURN_REMOTE_URL, false)
        llmSummaryItemId = intent.getLongExtra(EXTRA_LLM_SUMMARY_ITEM_ID, 0L)
        readAloudItemId = intent.getLongExtra(EXTRA_READ_ALOUD_ITEM_ID, 0L)

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
                    ServerType.LLM_CONFIG -> {
                        LocalHttpServer.createLlmConfigServer(app.container) {
                            handleLlmConfigComplete()
                        }
                    }
                    ServerType.READ_ALOUD_CONFIG -> {
                        LocalHttpServer.createReadAloudConfigServer(app.container) {
                            handleReadAloudConfigComplete()
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

    private fun handleLlmConfigComplete() {
        lifecycleScope.launch {
            withContext(Dispatchers.Main) {
                if (llmSummaryItemId > 0L) {
                    startActivity(LlmConnectivityActivity.createIntent(this@ServerActivity, llmSummaryItemId))
                    finish()
                } else {
                    synced = true
                }
            }
        }
    }

    private fun handleReadAloudConfigComplete() {
        lifecycleScope.launch {
            withContext(Dispatchers.Main) {
                if (readAloudItemId > 0L) {
                    val controller = (application as WatchRssApplication).container.readAloudController
                    controller.startFromItem(readAloudItemId)
                    startActivity(ReadAloudPlaybackActivity.createIntent(this@ServerActivity))
                    finish()
                } else {
                    synced = true
                }
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
        const val EXTRA_LLM_SUMMARY_ITEM_ID = "llm_summary_item_id"
        const val EXTRA_READ_ALOUD_ITEM_ID = "read_aloud_item_id"
    }

    enum class ServerType {
        REMOTE_INPUT,
        SYNC_FAVORITES,
        SYNC_WATCH_LATER,
        LLM_CONFIG,
        READ_ALOUD_CONFIG
    }

    private fun resolveTitle(): String {
        if (screenTitle.isNotBlank()) return screenTitle
        return when (serverType) {
            ServerType.REMOTE_INPUT -> "从手机输入"
            ServerType.SYNC_FAVORITES -> "同步收藏"
            ServerType.SYNC_WATCH_LATER -> "同步稍后再看"
            ServerType.LLM_CONFIG -> "配置大模型"
            ServerType.READ_ALOUD_CONFIG -> "配置朗读"
        }
    }

    private fun resolveHint(): String {
        if (screenHint.isNotBlank()) return screenHint
        return when (serverType) {
            ServerType.REMOTE_INPUT -> "请使用手机版扫描下方二维码"
            ServerType.SYNC_FAVORITES -> "请使用手机版扫描下方二维码"
            ServerType.SYNC_WATCH_LATER -> "请使用手机版扫描下方二维码"
            ServerType.LLM_CONFIG -> "请使用手机版扫描下方二维码"
            ServerType.READ_ALOUD_CONFIG -> "请使用手机版扫描下方二维码"
        }
    }
}
