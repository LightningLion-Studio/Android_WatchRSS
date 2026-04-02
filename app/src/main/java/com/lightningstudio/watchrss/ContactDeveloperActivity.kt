package com.lightningstudio.watchrss

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.lightningstudio.watchrss.ui.components.WatchCircularProgressIndicator
import com.lightningstudio.watchrss.ui.screen.ContactDeveloperScreen
import com.lightningstudio.watchrss.ui.theme.WatchRSSTheme

class ContactDeveloperActivity : BaseWatchActivity() {
    private var isNavigating by mutableStateOf(false)

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            isNavigating = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupSystemBars()

        setContent {
            WatchRSSTheme {
                Box(modifier = Modifier.fillMaxSize()) {
                    ContactDeveloperScreen(
                        onJoinGroupClick = {
                            if (!allowNavigation()) return@ContactDeveloperScreen
                            isNavigating = true
                            startActivity(Intent(this@ContactDeveloperActivity, JoinGroupActivity::class.java))
                        },
                        onUploadLogClick = {
                            if (!allowNavigation()) return@ContactDeveloperScreen
                            isNavigating = true
                            startActivity(Intent(this@ContactDeveloperActivity, LogUploadPrivacyActivity::class.java))
                        }
                    )

                    if (isNavigating) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            WatchCircularProgressIndicator()
                        }
                    }
                }
            }
        }
    }
}
