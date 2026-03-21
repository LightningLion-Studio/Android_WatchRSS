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
                val intent = Intent(this, ServerActivity::class.java).apply {
                    putExtra(ServerActivity.EXTRA_SERVER_TYPE, ServerActivity.ServerType.CONNECTION_HUB.name)
                    putExtra(ServerActivity.EXTRA_PREFERRED_ABILITY, preferredAbility?.name)
                    putExtra(ServerActivity.EXTRA_SCREEN_TITLE, resolveManualTitle())
                    putExtra(ServerActivity.EXTRA_SCREEN_HINT, MANUAL_WIFI_HINT)
                    putExtra(ServerActivity.EXTRA_RETURN_REMOTE_URL, returnRemoteUrl)
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
            null -> "连接手机"
        }
    }

    companion object {
        private const val EXTRA_PREFERRED_ABILITY = "preferred_ability"
        private const val EXTRA_RETURN_REMOTE_URL = "return_remote_url"
        private const val MANUAL_WIFI_HINT =
            "请先让手表与手机位于同一 WiFi，或让手表连接到手机热点，再使用手机版扫码"

        fun createIntent(
            context: Context,
            preferredAbility: PhoneConnectionAbility? = null,
            returnRemoteUrl: Boolean = false
        ): Intent {
            return Intent(context, PhoneConnectionActivity::class.java).apply {
                putExtra(EXTRA_PREFERRED_ABILITY, preferredAbility?.name)
                putExtra(EXTRA_RETURN_REMOTE_URL, returnRemoteUrl)
            }
        }
    }
}
