package com.lightningstudio.watchrss

import android.content.Context
import android.content.Intent
import androidx.compose.ui.Modifier
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.lightningstudio.watchrss.data.douyin.DouyinSourceOrigin
import com.lightningstudio.watchrss.data.douyin.DouyinStreamItem
import com.lightningstudio.watchrss.ui.screen.douyin.DouyinImmersiveScreen
import com.lightningstudio.watchrss.ui.screen.douyin.rememberDouyinPlayerPoolSession
import com.lightningstudio.watchrss.ui.viewmodel.DouyinFeedUiState
import com.lightningstudio.watchrss.data.network.InternetAvailabilityStatus
import com.lightningstudio.watchrss.data.rss.BuiltinChannelType
import com.lightningstudio.watchrss.data.settings.DEFAULT_MEDIA_PLAYBACK_START_VOLUME_LIMIT_PERCENT
import com.lightningstudio.watchrss.data.settings.DEFAULT_MEDIA_VOLUME_CONTROL_ENABLED
import com.lightningstudio.watchrss.data.settings.DEFAULT_MEDIA_VOLUME_GUARD_ENABLED
import com.lightningstudio.watchrss.ui.components.ThirdPartyPlatformNotice
import com.lightningstudio.watchrss.ui.screen.PlatformEntryScreen
import com.lightningstudio.watchrss.ui.theme.WatchRSSTheme
import kotlinx.coroutines.flow.map

class DouyinPlayerActivity : BaseWatchActivity() {
    private val container by lazy { (application as WatchRssApplication).container }
    private val repository by lazy { container.douyinRepository }
    private val rssRepository by lazy { container.rssRepository }
    private val settingsRepository by lazy { container.settingsRepository }
    private var currentPageIndex: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupSystemBars()

        val items = parseItems(intent)
        val startIndex = intent.getIntExtra(EXTRA_INDEX, 0).coerceIn(0, (items.size - 1).coerceAtLeast(0))
        currentPageIndex = startIndex
        if (items.isNotEmpty()) {
            val first = items[startIndex]
            container.watchUsageTelemetry.recordVideoPlayed(
                source = "douyin",
                id = first.awemeId.orEmpty(),
                title = first.title
            )
        }

        setContent {
            WatchRSSTheme {
                val baseDensity = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(2f, baseDensity.fontScale)) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        ThirdPartyPlatformNotice(
                            platform = "抖音",
                            compact = true,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                        Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                            val volumeGuardEnabled by settingsRepository.mediaVolumeGuardEnabled.collectAsState(
                                initial = DEFAULT_MEDIA_VOLUME_GUARD_ENABLED
                            )
                            val volumeControlEnabled by settingsRepository.mediaVolumeControlEnabled.collectAsState(
                                initial = DEFAULT_MEDIA_VOLUME_CONTROL_ENABLED
                            )
                            val playbackStartVolumeLimitPercent by settingsRepository.mediaPlaybackStartVolumeLimitPercent.collectAsState(
                                initial = DEFAULT_MEDIA_PLAYBACK_START_VOLUME_LIMIT_PERCENT
                            )
                            val internetAvailabilityStatus by container.internetAvailabilityMonitor.internetAvailability.collectAsState(
                                initial = InternetAvailabilityStatus.Checking
                            )
                            val continuePlaybackInBackground by remember(rssRepository) {
                                rssRepository.observeChannels().map { channels ->
                                    channels.firstOrNull { it.url == BuiltinChannelType.DOUYIN.url }
                                        ?.continuePlaybackInBackground ?: false
                                }
                            }.collectAsState(initial = false)
                            if (items.isEmpty()) {
                                PlatformEntryScreen(title = "腕上RSS · 抖音频道", message = "暂无可播放内容")
                            } else {
                                var headersReady by remember { mutableStateOf(false) }
                                val headers by produceState(initialValue = emptyMap<String, String>()) {
                                    value = repository.buildPlayHeaders()
                                    headersReady = true
                                }

                                var currentPage by remember { mutableStateOf(startIndex + 1) }
                                var retryGeneration by remember { mutableStateOf(0) }
                                val streamItems = remember(items) {
                                    items.mapIndexed { index, item ->
                                        DouyinStreamItem(
                                            awemeId = item.awemeId?.takeIf(String::isNotBlank) ?: "saved-$index",
                                            playUrl = item.playUrl.orEmpty(),
                                            coverUrl = item.coverUrl,
                                            title = item.title,
                                            author = item.author,
                                            likeCount = 0L,
                                            playUrlResolvedAtMs = 0L,
                                            sourceOrigin = DouyinSourceOrigin.BOOTSTRAP_CACHE
                                        )
                                    }
                                }
                                if (headersReady) key(retryGeneration) {
                                    val session = rememberDouyinPlayerPoolSession(
                                        headers,
                                        enabled = true,
                                        namespace = "saved:${System.identityHashCode(this@DouyinPlayerActivity)}:$retryGeneration"
                                    )
                                    if (session != null) {
                                        DouyinImmersiveScreen(
                                            playerSession = session,
                                            uiState = DouyinFeedUiState(
                                                isLoggedIn = true,
                                                items = streamItems,
                                                hasMore = false,
                                                currentPage = currentPage,
                                                playHeaders = headers,
                                                showTitlePage = false
                                            ),
                                            digitalCrownVolumeEnabled = volumeControlEnabled,
                                            volumeGuardEnabled = volumeGuardEnabled,
                                            playbackStartVolumeLimitPercent = playbackStartVolumeLimitPercent,
                                            continuePlaybackInBackground = continuePlaybackInBackground,
                                            internetAvailabilityStatus = internetAvailabilityStatus,
                                            onRefresh = { retryGeneration += 1 },
                                            onPageSettled = { page ->
                                                if (page > 0) {
                                                    currentPage = page
                                                    currentPageIndex = page - 1
                                                }
                                            },
                                            onEnterFlow = { },
                                            onItemLongPress = { item ->
                                                startActivity(DouyinVideoActionsActivity.createIntent(
                                                    context = this@DouyinPlayerActivity,
                                                    awemeId = item.awemeId,
                                                    title = item.title,
                                                    author = item.author,
                                                    playUrl = item.playUrl,
                                                    coverUrl = item.coverUrl,
                                                    likeCount = item.likeCount
                                                ))
                                            },
                                            // Saved URLs use the screen's bounded local reprepare;
                                            // only an explicit refresh starts a new playback session.
                                            onRequestPlaybackRefresh = { _, _, _ -> },
                                            onDiscardPlaybackItem = { },
                                            onMessageShown = { },
                                            onHeaderClick = { },
                                            onPlaybackActiveChanged = { active ->
                                                setVideoPlaybackBatteryTracking("douyin", active)
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun buildResumeIntent(): Intent? {
        val playUrls = ArrayList(intent.getStringArrayListExtra(EXTRA_PLAY_URLS).orEmpty())
        if (playUrls.isEmpty()) return null
        return Intent(this, DouyinPlayerActivity::class.java).apply {
            putExtra(EXTRA_INDEX, currentPageIndex.coerceIn(0, (playUrls.size - 1).coerceAtLeast(0)))
            putStringArrayListExtra(EXTRA_AWEME_IDS, ArrayList(intent.getStringArrayListExtra(EXTRA_AWEME_IDS).orEmpty()))
            putStringArrayListExtra(EXTRA_PLAY_URLS, playUrls)
            putStringArrayListExtra(EXTRA_COVERS, ArrayList(intent.getStringArrayListExtra(EXTRA_COVERS).orEmpty()))
            putStringArrayListExtra(EXTRA_TITLES, ArrayList(intent.getStringArrayListExtra(EXTRA_TITLES).orEmpty()))
            putStringArrayListExtra(EXTRA_AUTHORS, ArrayList(intent.getStringArrayListExtra(EXTRA_AUTHORS).orEmpty()))
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
