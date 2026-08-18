package com.lightningstudio.watchrss

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.lightningstudio.watchrss.phoneconnection.PhoneConnectionAbility
import com.lightningstudio.watchrss.ui.screen.rss.TtsSettingsScreen
import com.lightningstudio.watchrss.ui.screen.rss.isDetailedTtsConfigurationVisible
import com.lightningstudio.watchrss.ui.theme.WatchRSSTheme
import com.lightningstudio.watchrss.ui.viewmodel.AppViewModelFactory
import com.lightningstudio.watchrss.ui.viewmodel.TtsSettingsViewModel

class TtsSettingsActivity : BaseWatchActivity() {
    private val container by lazy { (application as WatchRssApplication).container }
    private val viewModel: TtsSettingsViewModel by viewModels {
        AppViewModelFactory(container)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupSystemBars()

        setContent {
            WatchRSSTheme {
                TtsSettingsScreen(
                    viewModel = viewModel,
                    showDetailedConfiguration = isDetailedTtsConfigurationVisible(BuildConfig.BUILD_TYPE),
                    onOpenPhoneConfig = {
                        startActivity(
                            Intent(this, ServerActivity::class.java).apply {
                                putExtra(ServerActivity.EXTRA_SERVER_TYPE, ServerActivity.ServerType.TTS_CONFIG.name)
                                putExtra(ServerActivity.EXTRA_PREFERRED_ABILITY, PhoneConnectionAbility.TTS_CONFIG.name)
                            }
                        )
                    }
                )
            }
        }
    }

    companion object {
        fun createIntent(context: Context) = Intent(context, TtsSettingsActivity::class.java)
    }
}
