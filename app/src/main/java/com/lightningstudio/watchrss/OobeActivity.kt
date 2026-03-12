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
import com.lightningstudio.watchrss.ui.theme.WatchRSSTheme
import com.lightningstudio.watchrss.ui.viewmodel.AppViewModelFactory
import com.lightningstudio.watchrss.ui.viewmodel.OobeEvent
import com.lightningstudio.watchrss.ui.viewmodel.OobeViewModel
import kotlinx.coroutines.launch

class OobeActivity : BaseWatchActivity() {
    private val viewModel: OobeViewModel by viewModels {
        AppViewModelFactory((application as WatchRssApplication).container)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupSystemBars()

        val returnHomeOnFinish = intent.getBooleanExtra(EXTRA_RETURN_HOME_ON_FINISH, true)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.events.collect { event ->
                    if (event == OobeEvent.Finish) {
                        if (returnHomeOnFinish) {
                            startActivity(Intent(this@OobeActivity, MainActivity::class.java))
                        }
                        finish()
                    }
                }
            }
        }

        setContent {
            WatchRSSTheme {
                val uiState by viewModel.uiState.collectAsState()
                OobeScreen(
                    uiState = uiState,
                    onSetIntroPage = viewModel::setIntroPage,
                    onContinueFromIntro = viewModel::completeOobe,
                    onOpenUserAgreement = {
                        startActivity(
                            InfoActivity.createIntent(
                                context = this,
                                title = "用户协议",
                                content = getString(R.string.about_user_agreement_content)
                            )
                        )
                    },
                    onOpenPrivacy = {
                        startActivity(
                            InfoActivity.createIntent(
                                context = this,
                                title = "隐私政策",
                                content = getString(R.string.about_privacy_content)
                            )
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
