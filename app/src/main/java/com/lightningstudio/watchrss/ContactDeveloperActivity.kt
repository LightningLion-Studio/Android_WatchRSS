package com.lightningstudio.watchrss

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import com.lightningstudio.watchrss.ui.screen.ContactDeveloperScreen
import com.lightningstudio.watchrss.ui.theme.WatchRSSTheme

class ContactDeveloperActivity : BaseWatchActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupSystemBars()

        setContent {
            WatchRSSTheme {
                ContactDeveloperScreen(
                    onJoinGroupClick = {
                        if (!allowNavigation()) return@ContactDeveloperScreen
                        startActivity(Intent(this, JoinGroupActivity::class.java))
                    },
                    onUploadLogClick = {
                        if (!allowNavigation()) return@ContactDeveloperScreen
                        startActivity(Intent(this, LogUploadPrivacyActivity::class.java))
                    }
                )
            }
        }
    }
}
