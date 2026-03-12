package com.lightningstudio.watchrss

import android.os.Bundle
import androidx.activity.compose.setContent
import com.lightningstudio.watchrss.ui.screen.ProjectInfoScreen
import com.lightningstudio.watchrss.ui.theme.WatchRSSTheme

class ProjectInfoActivity : BaseWatchActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupSystemBars()

        setContent {
            WatchRSSTheme {
                ProjectInfoScreen()
            }
        }
    }
}
