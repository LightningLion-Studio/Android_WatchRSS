package com.lightningstudio.watchrss

import android.os.Bundle
import androidx.activity.compose.setContent
import com.lightningstudio.watchrss.ui.screen.JoinGroupScreen
import com.lightningstudio.watchrss.ui.theme.WatchRSSTheme

class JoinGroupActivity : BaseWatchActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupSystemBars()

        setContent {
            WatchRSSTheme {
                JoinGroupScreen(
                    qrCodeUrl = "https://qm.qq.com/q/cJNTQuxfoW",
                    groupNumber = "1083518433"
                )
            }
        }
    }
}
