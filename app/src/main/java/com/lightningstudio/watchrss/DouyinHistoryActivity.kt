package com.lightningstudio.watchrss

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.lightningstudio.watchrss.data.douyin.DouyinStreamItem
import com.lightningstudio.watchrss.data.douyin.DouyinWatchHistoryEntry
import com.lightningstudio.watchrss.data.douyin.DouyinWatchHistoryStore
import com.lightningstudio.watchrss.ui.screen.douyin.DouyinHistoryScreen
import com.lightningstudio.watchrss.ui.theme.WatchRSSTheme

class DouyinHistoryActivity : BaseWatchActivity() {
    private val watchHistoryStore by lazy { DouyinWatchHistoryStore(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupSystemBars()

        setContent {
            WatchRSSTheme {
                val baseDensity = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(2f, baseDensity.fontScale)) {
                    var historyItems by remember {
                        mutableStateOf(watchHistoryStore.readHistory())
                    }

                    DouyinHistoryScreen(
                        items = historyItems,
                        onItemClick = { entry ->
                            if (!allowNavigation()) return@DouyinHistoryScreen
                            watchHistoryStore.markWatched(entry.toStreamItem())
                            historyItems = watchHistoryStore.readHistory()
                            startActivity(
                                DouyinDetailActivity.createIntent(
                                    context = this@DouyinHistoryActivity,
                                    awemeId = entry.awemeId,
                                    title = entry.title,
                                    author = entry.author,
                                    summary = if (entry.likeCount > 0) {
                                        "点赞 ${entry.likeCount}"
                                    } else {
                                        null
                                    },
                                    playUrl = entry.playUrl,
                                    coverUrl = entry.coverUrl
                                )
                            )
                        },
                        onClearHistory = {
                            watchHistoryStore.clear()
                            historyItems = emptyList()
                            com.lightningstudio.watchrss.ui.util.showAppToast(
                                this@DouyinHistoryActivity,
                                "已清空播放历史",
                                android.widget.Toast.LENGTH_SHORT
                            )
                        }
                    )
                }
            }
        }
    }

    override fun buildResumeIntent(): Intent = createIntent(this)

    companion object {
        fun createIntent(context: Context): Intent {
            return Intent(context, DouyinHistoryActivity::class.java)
        }
    }
}

private fun DouyinWatchHistoryEntry.toStreamItem(): DouyinStreamItem {
    return DouyinStreamItem(
        awemeId = awemeId,
        playUrl = playUrl,
        coverUrl = coverUrl,
        title = title,
        author = author,
        likeCount = likeCount,
        playUrlResolvedAtMs = watchedAt,
        sourceOrigin = com.lightningstudio.watchrss.data.douyin.DouyinSourceOrigin.BOOTSTRAP_CACHE
    )
}
