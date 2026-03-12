package com.lightningstudio.watchrss

import android.os.Bundle
import androidx.activity.compose.setContent
import com.lightningstudio.watchrss.ui.screen.CollaboratorsScreen
import com.lightningstudio.watchrss.ui.theme.WatchRSSTheme

class CollaboratorsActivity : BaseWatchActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupSystemBars()

        setContent {
            WatchRSSTheme {
                CollaboratorsScreen(circleMaskEnabled = BuildConfig.DEBUG)
            }
        }
    }
}
