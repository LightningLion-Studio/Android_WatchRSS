package com.lightningstudio.watchrss

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.lightningstudio.watchrss.ui.screen.OobeScreen
import com.lightningstudio.watchrss.data.reader.ReaderThemeMode
import com.lightningstudio.watchrss.ui.reader.readerChromeStyle
import com.lightningstudio.watchrss.ui.theme.WatchRSSTheme
import com.lightningstudio.watchrss.ui.viewmodel.AppViewModelFactory
import com.lightningstudio.watchrss.ui.viewmodel.OobeEvent
import com.lightningstudio.watchrss.ui.viewmodel.OobeViewModel
import kotlinx.coroutines.launch

class OobeActivity : BaseWatchActivity() {
    private val readerPresetRepository by lazy {
        (application as WatchRssApplication).container.readerPresetRepository
    }
    private val viewModel: OobeViewModel by viewModels {
        AppViewModelFactory((application as WatchRssApplication).container)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupSystemBars()
        (application as WatchRssApplication).usageTelemetry.recordReleaseOobeOpened()

        val returnHomeOnFinish = intent.getBooleanExtra(EXTRA_RETURN_HOME_ON_FINISH, true)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.events.collect { event ->
                    if (event == OobeEvent.Finish) {
                        if (returnHomeOnFinish) {
                            startActivity(
                                HomeFeedListActivity.createIntent(this@OobeActivity).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                                }
                            )
                        }
                        finish()
                    }
                }
            }
        }

        setContent {
            WatchRSSTheme {
                val uiState by viewModel.uiState.collectAsState()
                val activeReaderPreset by readerPresetRepository.activePreset.collectAsState()
                val readerChrome = activeReaderPreset.readerChromeStyle()
                OobeScreen(
                    uiState = uiState,
                    onSetIntroPage = viewModel::setIntroPage,
                    onContinueFromIntro = viewModel::completeOobe,
                    onOpenUserAgreement = {
                        startActivity(
                            InfoActivity.createRemoteIntent(
                                context = this,
                                title = "用户协议",
                                path = InfoActivity.WATCH_USER_AGREEMENT_PATH
                            )
                        )
                    },
                    onOpenPrivacy = {
                        startActivity(
                            InfoActivity.createRemoteIntent(
                                context = this,
                                title = "隐私政策",
                                path = InfoActivity.WATCH_PRIVACY_POLICY_PATH
                            )
                        )
                    },
                    readerDisplayDark = readerChrome.isDark,
                    onToggleReaderDisplayMode = {
                        readerPresetRepository.setThemeMode(
                            if (readerChrome.isDark) {
                                ReaderThemeMode.LIGHT
                            } else {
                                ReaderThemeMode.DARK
                            }
                        )
                    }
                )
            }
        }
    }

    override fun isSwipeBackEnabled(): Boolean = false

    companion object {
        private const val EXTRA_RETURN_HOME_ON_FINISH = "extra_return_home_on_finish"

        fun createIntent(context: Context, returnHomeOnFinish: Boolean = true): Intent {
            return Intent(context, OobeActivity::class.java).apply {
                putExtra(EXTRA_RETURN_HOME_ON_FINISH, returnHomeOnFinish)
            }
        }
    }
}
