package com.lightningstudio.watchrss

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import com.lightningstudio.watchrss.data.settings.PRIVACY_POLICY_VERSION
import com.lightningstudio.watchrss.ui.screen.PrivacyPolicyConsentScreen
import com.lightningstudio.watchrss.ui.theme.WatchRSSTheme
import kotlinx.coroutines.launch

/**
 * 隐私政策变更后的再次同意页面。
 *
 * 当 [PRIVACY_POLICY_VERSION] 提升且用户尚未同意新版本时，启动页/主流程会跳转至此页面。
 * 用户必须明确点击"同意"并记录对应版本号后才能继续使用应用；点击"不同意"则退出应用。
 */
class PrivacyPolicyConsentActivity : BaseWatchActivity() {

    override fun isSwipeBackEnabled(): Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupSystemBars()

        val settingsRepository = (application as WatchRssApplication).container.settingsRepository
        setContent {
            WatchRSSTheme {
                PrivacyPolicyConsentScreen(
                    onOpenPrivacy = {
                        startActivity(
                            InfoActivity.createIntent(
                                context = this@PrivacyPolicyConsentActivity,
                                title = "隐私政策",
                                contentRawResId = R.raw.privacy_policy
                            )
                        )
                    },
                    onAgree = {
                        lifecycleScope.launch {
                            settingsRepository.setPrivacyPolicyAgreedVersion(PRIVACY_POLICY_VERSION)
                            finish()
                        }
                    },
                    onDisagree = {
                        finishAffinity()
                    }
                )
            }
        }
    }

    companion object {
        fun createIntent(context: Context): Intent {
            return Intent(context, PrivacyPolicyConsentActivity::class.java)
        }
    }
}
