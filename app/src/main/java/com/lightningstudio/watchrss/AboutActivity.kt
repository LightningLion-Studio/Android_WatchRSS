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
                        startActivity(InfoActivity.createIntent(this, "隐私政策", R.raw.privacy_policy))
                    },
                    onTermsClick = {
                        startActivity(InfoActivity.createIntent(this, "用户协议", R.raw.user_agreement))
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
