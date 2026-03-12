package com.lightningstudio.watchrss

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import com.lightningstudio.watchrss.ui.screen.LogUploadPrivacyScreen
import com.lightningstudio.watchrss.ui.theme.WatchRSSTheme

class LogUploadPrivacyActivity : BaseWatchActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupSystemBars()

        setContent {
            WatchRSSTheme {
                LogUploadPrivacyScreen(
                    onStartUploadClick = {
                        if (!allowNavigation()) return@LogUploadPrivacyScreen
                        startActivity(Intent(this, LogUploadActivity::class.java))
                    }
                )
            }
        }
    }
}
