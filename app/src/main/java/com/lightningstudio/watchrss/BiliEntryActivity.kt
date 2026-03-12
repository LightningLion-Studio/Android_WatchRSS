package com.lightningstudio.watchrss

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.lightningstudio.watchrss.ui.screen.bili.BiliEntryNavGraph
import com.lightningstudio.watchrss.ui.theme.WatchRSSTheme

class BiliEntryActivity : BaseWatchActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupSystemBars()

        setContent {
            WatchRSSTheme {
                val baseDensity = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(2f, baseDensity.fontScale)) {
                    val repository = (application as WatchRssApplication).container.biliRepository
                    val rssRepository = (application as WatchRssApplication).container.rssRepository
                    BiliEntryNavGraph(repository = repository, rssRepository = rssRepository)
                }
            }
        }
    }
}
