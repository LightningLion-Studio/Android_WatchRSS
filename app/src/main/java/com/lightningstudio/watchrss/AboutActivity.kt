package com.lightningstudio.watchrss

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import com.lightningstudio.watchrss.ui.screen.AboutScreen
import com.lightningstudio.watchrss.ui.theme.WatchRSSTheme

class AboutActivity : BaseWatchActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupSystemBars()

        setContent {
            WatchRSSTheme {
                AboutScreen(
                    onIntroClick = {
                        openInfo("项目自介", getString(R.string.about_intro_content))
                    },
                    onPrivacyClick = {
                        startActivity(
                            InfoActivity.createRemoteIntent(
                                this,
                                "隐私政策",
                                InfoActivity.WATCH_PRIVACY_POLICY_PATH
                            )
                        )
                    },
                    onTermsClick = {
                        startActivity(
                            InfoActivity.createRemoteIntent(
                                this,
                                "用户协议",
                                InfoActivity.WATCH_USER_AGREEMENT_PATH
                            )
                        )
                    },
                    onLicensesClick = {
                        openInfo("开源许可与清单", getString(R.string.about_licenses_content))
                    },
                    onCollaboratorsClick = {
                        startActivity(Intent(this, CollaboratorsActivity::class.java))
                    }
                )
            }
        }
    }

    private fun openInfo(title: String, content: String) {
        startActivity(InfoActivity.createIntent(this, title, content))
    }
}
