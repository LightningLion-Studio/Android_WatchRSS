package com.lightningstudio.watchrss

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import com.lightningstudio.watchrss.data.rss.RssRecommendations
import com.lightningstudio.watchrss.ui.screen.rss.RssRecommendScreen
import com.lightningstudio.watchrss.ui.theme.WatchRSSTheme

class RssRecommendActivity : BaseWatchActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupSystemBars()
        setContent {
            WatchRSSTheme {
                RssRecommendScreen(
                    groups = RssRecommendations.groups,
                    onGroupClick = { group ->
                        if (!allowNavigation()) return@RssRecommendScreen
                        val intent = Intent(this, RssRecommendGroupActivity::class.java)
                        intent.putExtra(RssRecommendGroupActivity.EXTRA_GROUP_ID, group.id)
                        startActivity(intent)
                    }
                )
            }
        }
    }
}
