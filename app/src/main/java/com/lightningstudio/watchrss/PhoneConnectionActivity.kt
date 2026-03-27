package com.lightningstudio.watchrss

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import com.lightningstudio.watchrss.phoneconnection.PhoneConnectionAbility
import com.lightningstudio.watchrss.phoneconnection.PhoneConnectionFeature
import com.lightningstudio.watchrss.phoneconnection.PhoneConnectionMode
import com.lightningstudio.watchrss.ui.screen.phoneconnection.PhoneConnectionScreen
import com.lightningstudio.watchrss.ui.theme.WatchRSSTheme

class PhoneConnectionActivity : BaseWatchActivity() {
    private var preferredAbility: PhoneConnectionAbility? = null
    private var returnRemoteUrl: Boolean = false
    private var llmSummaryItemId: Long = 0L
    private var readAloudItemId: Long = 0L

    private val manualServerLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode != Activity.RESULT_OK) return@registerForActivityResult
            setResult(result.resultCode, result.data)
            finish()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!PhoneConnectionFeature.isDebugBuild) {
            finish()
            return
        }
        setupSystemBars()

        preferredAbility = PhoneConnectionAbility.fromNameOrNull(intent.getStringExtra(EXTRA_PREFERRED_ABILITY))
        returnRemoteUrl = intent.getBooleanExtra(EXTRA_RETURN_REMOTE_URL, false)
        llmSummaryItemId = intent.getLongExtra(EXTRA_LLM_SUMMARY_ITEM_ID, 0L)
        readAloudItemId = intent.getLongExtra(EXTRA_READ_ALOUD_ITEM_ID, 0L)

        setContent {
            WatchRSSTheme {
                PhoneConnectionScreen(
                    preferredAbilityLabel = preferredAbility?.displayName,
                    onModeClick = ::handleModeClick
                )
            }
        }
    }

    private fun handleModeClick(mode: PhoneConnectionMode) {
        if (!allowNavigation()) return
        when (mode) {
            PhoneConnectionMode.MANUAL_WIFI -> {
                val serverType = when (preferredAbility) {
                    PhoneConnectionAbility.REMOTE_INPUT -> ServerActivity.ServerType.REMOTE_INPUT
                    PhoneConnectionAbility.SYNC_FAVORITES -> ServerActivity.ServerType.SYNC_FAVORITES
                    PhoneConnectionAbility.SYNC_WATCH_LATER -> ServerActivity.ServerType.SYNC_WATCH_LATER
                    PhoneConnectionAbility.LLM_SUMMARY_CONFIG -> ServerActivity.ServerType.LLM_CONFIG
                    PhoneConnectionAbility.READ_ALOUD_CONFIG -> ServerActivity.ServerType.READ_ALOUD_CONFIG
                    null -> ServerActivity.ServerType.REMOTE_INPUT
                }
                val intent = Intent(this, ServerActivity::class.java).apply {
                    putExtra(ServerActivity.EXTRA_SERVER_TYPE, serverType.name)
                    putExtra(ServerActivity.EXTRA_PREFERRED_ABILITY, preferredAbility?.name)
                    putExtra(ServerActivity.EXTRA_SCREEN_TITLE, resolveManualTitle())
                    putExtra(ServerActivity.EXTRA_SCREEN_HINT, MANUAL_WIFI_HINT)
                    putExtra(ServerActivity.EXTRA_RETURN_REMOTE_URL, returnRemoteUrl)
                    putExtra(ServerActivity.EXTRA_LLM_SUMMARY_ITEM_ID, llmSummaryItemId)
                    putExtra(ServerActivity.EXTRA_READ_ALOUD_ITEM_ID, readAloudItemId)
                }
                if (returnRemoteUrl) {
                    manualServerLauncher.launch(intent)
                } else {
                    startActivity(intent)
                }
            }

            PhoneConnectionMode.PURE_SOUND,
            PhoneConnectionMode.SOUND_GUIDED_WIFI -> {
                startActivity(
                    AcousticConnectionActivity.createIntent(
                        context = this,
                        mode = mode,
                        preferredAbility = preferredAbility,
                        returnRemoteUrl = returnRemoteUrl
                    )
                )
            }
        }
    }

    private fun resolveManualTitle(): String {
        return when (preferredAbility) {
            PhoneConnectionAbility.REMOTE_INPUT -> "从手机输入"
            PhoneConnectionAbility.SYNC_FAVORITES -> "同步收藏"
            PhoneConnectionAbility.SYNC_WATCH_LATER -> "同步稍后再看"
            PhoneConnectionAbility.LLM_SUMMARY_CONFIG -> "配置大模型"
            PhoneConnectionAbility.READ_ALOUD_CONFIG -> "配置朗读"
            null -> "连接手机"
        }
    }

    companion object {
        private const val EXTRA_PREFERRED_ABILITY = "preferred_ability"
        private const val EXTRA_RETURN_REMOTE_URL = "return_remote_url"
        private const val EXTRA_LLM_SUMMARY_ITEM_ID = "llm_summary_item_id"
        private const val EXTRA_READ_ALOUD_ITEM_ID = "read_aloud_item_id"
        private const val MANUAL_WIFI_HINT =
            "请先让手表与手机位于同一 WiFi，或让手表连接到手机热点，再使用手机版扫码"

        fun createIntent(
            context: Context,
            preferredAbility: PhoneConnectionAbility? = null,
            returnRemoteUrl: Boolean = false,
            llmSummaryItemId: Long = 0L,
            readAloudItemId: Long = 0L
        ): Intent {
            return Intent(context, PhoneConnectionActivity::class.java).apply {
                putExtra(EXTRA_PREFERRED_ABILITY, preferredAbility?.name)
                putExtra(EXTRA_RETURN_REMOTE_URL, returnRemoteUrl)
                putExtra(EXTRA_LLM_SUMMARY_ITEM_ID, llmSummaryItemId)
                putExtra(EXTRA_READ_ALOUD_ITEM_ID, readAloudItemId)
            }
        }
    }
}
