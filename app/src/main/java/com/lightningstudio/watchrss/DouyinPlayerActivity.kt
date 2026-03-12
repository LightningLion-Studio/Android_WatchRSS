package com.lightningstudio.watchrss

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.lightningstudio.watchrss.ui.screen.PlatformEntryScreen
import com.lightningstudio.watchrss.ui.screen.bili.BiliPlayerScreen
import com.lightningstudio.watchrss.ui.theme.WatchRSSTheme
import com.lightningstudio.watchrss.ui.viewmodel.BiliPlayerUiState

class DouyinPlayerActivity : BaseWatchActivity() {
    private val repository by lazy { (application as WatchRssApplication).container.douyinRepository }

    @OptIn(ExperimentalFoundationApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupSystemBars()

        val items = parseItems(intent)
        val startIndex = intent.getIntExtra(EXTRA_INDEX, 0).coerceIn(0, (items.size - 1).coerceAtLeast(0))

        setContent {
            WatchRSSTheme {
                val baseDensity = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(2f, baseDensity.fontScale)) {
                    if (items.isEmpty()) {
                        PlatformEntryScreen(title = "抖音", message = "暂无可播放内容")
                    } else {
                        val headers by produceState(initialValue = emptyMap<String, String>()) {
                            value = repository.buildPlayHeaders()
                        }

                        val pagerState = rememberPagerState(
                            initialPage = startIndex,
                            pageCount = { items.size }
                        )

                        VerticalPager(state = pagerState) { page ->
                            val item = items[page]
                            var retryKey by remember(page) { mutableStateOf(0) }
                            val uiState = remember(item, headers) {
                                val playUrl = item.playUrl?.takeIf { it.isNotBlank() }
                                BiliPlayerUiState(
                                    isLoading = false,
                                    playUrl = playUrl,
                                    headers = headers,
                                    message = if (playUrl.isNullOrBlank()) "播放地址为空" else null,
                                    title = item.title,
                                    owner = item.author
                                )
                            }

                            key(retryKey, item.playUrl) {
                                BiliPlayerScreen(
                                    uiState = uiState,
                                    onRetry = { retryKey += 1 },
                                    onOpenWeb = {
                                        val link = item.awemeId?.let { "https://www.douyin.com/video/$it" }
                                            ?: return@BiliPlayerScreen
                                        startActivity(WebViewActivity.createIntent(this@DouyinPlayerActivity, link))
                                    },
                                    onPanStateChange = { _, _ -> },
                                    allowPan = false
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    private fun parseItems(intent: Intent): List<DouyinPlaybackItem> {
        val awemeIds = intent.getStringArrayListExtra(EXTRA_AWEME_IDS).orEmpty()
        val playUrls = intent.getStringArrayListExtra(EXTRA_PLAY_URLS).orEmpty()
        val coverUrls = intent.getStringArrayListExtra(EXTRA_COVERS).orEmpty()
        val titles = intent.getStringArrayListExtra(EXTRA_TITLES).orEmpty()
        val authors = intent.getStringArrayListExtra(EXTRA_AUTHORS).orEmpty()
        val size = playUrls.size
        if (size == 0) return emptyList()

        return List(size) { index ->
            DouyinPlaybackItem(
                awemeId = awemeIds.getOrNull(index),
                playUrl = playUrls.getOrNull(index),
                coverUrl = coverUrls.getOrNull(index),
                title = titles.getOrNull(index),
                author = authors.getOrNull(index)
            )
        }
    }

    private data class DouyinPlaybackItem(
        val awemeId: String?,
        val playUrl: String?,
        val coverUrl: String?,
        val title: String?,
        val author: String?
    )

    companion object {
        private const val EXTRA_INDEX = "douyin_index"
        private const val EXTRA_AWEME_IDS = "douyin_aweme_ids"
        private const val EXTRA_PLAY_URLS = "douyin_play_urls"
        private const val EXTRA_COVERS = "douyin_covers"
        private const val EXTRA_TITLES = "douyin_titles"
        private const val EXTRA_AUTHORS = "douyin_authors"

        fun createIntent(
            context: Context,
            items: List<com.lightningstudio.watchrss.sdk.douyin.DouyinVideo>,
            startIndex: Int
        ): Intent {
            val awemeIds = ArrayList<String>(items.size)
            val playUrls = ArrayList<String>(items.size)
            val coverUrls = ArrayList<String>(items.size)
            val titles = ArrayList<String>(items.size)
            val authors = ArrayList<String>(items.size)

            items.forEach { item ->
                awemeIds.add(item.awemeId.orEmpty())
                playUrls.add(item.playUrl.orEmpty())
                coverUrls.add(item.coverUrl.orEmpty())
                titles.add(item.desc.orEmpty())
                authors.add(item.authorName.orEmpty())
            }

            return Intent(context, DouyinPlayerActivity::class.java).apply {
                putExtra(EXTRA_INDEX, startIndex)
                putStringArrayListExtra(EXTRA_AWEME_IDS, awemeIds)
                putStringArrayListExtra(EXTRA_PLAY_URLS, playUrls)
                putStringArrayListExtra(EXTRA_COVERS, coverUrls)
                putStringArrayListExtra(EXTRA_TITLES, titles)
                putStringArrayListExtra(EXTRA_AUTHORS, authors)
            }
        }
    }
}
