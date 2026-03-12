package com.lightningstudio.watchrss

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.lightningstudio.watchrss.data.rss.RssItem
import com.lightningstudio.watchrss.ui.screen.rss.DetailContent
import com.lightningstudio.watchrss.ui.theme.WatchRSSTheme
import com.lightningstudio.watchrss.ui.util.ContentBlock
import com.lightningstudio.watchrss.ui.util.TextStyle as ContentTextStyle
import kotlin.math.absoluteValue

class DouyinDetailActivity : BaseWatchActivity() {
    private val settingsRepository by lazy { (application as WatchRssApplication).container.settingsRepository }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupSystemBars()

        val awemeId = intent.getStringExtra(EXTRA_AWEME_ID).orEmpty()
        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        val author = intent.getStringExtra(EXTRA_AUTHOR).orEmpty()
        val summary = intent.getStringExtra(EXTRA_SUMMARY).orEmpty()
        val playUrl = intent.getStringExtra(EXTRA_PLAY_URL).orEmpty()
        val coverUrl = intent.getStringExtra(EXTRA_COVER_URL).orEmpty()

        setContent {
            WatchRSSTheme {
                val baseDensity = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(2f, baseDensity.fontScale)) {
                    val readingThemeDark by settingsRepository.readingThemeDark.collectAsState(initial = true)
                    val readingFontSizeSp by settingsRepository.readingFontSizeSp.collectAsState(initial = 14)
                    val shareUseSystem by settingsRepository.shareUseSystem.collectAsState(initial = false)
                    var isFavorite by remember(awemeId) { mutableStateOf(false) }

                    val item = remember(awemeId, title, author, summary, coverUrl, playUrl) {
                        buildDouyinRssItem(
                            awemeId = awemeId,
                            title = title,
                            author = author,
                            summary = summary,
                            coverUrl = coverUrl,
                            playUrl = playUrl
                        )
                    }
                    val contentBlocks = remember(author, summary, playUrl, coverUrl) {
                        buildDouyinDetailBlocks(
                            author = author,
                            summary = summary,
                            playUrl = playUrl,
                            coverUrl = coverUrl
                        )
                    }

                    DetailContent(
                        item = item,
                        showOriginalLoadingNotice = false,
                        contentBlocks = contentBlocks,
                        offlineMedia = emptyMap(),
                        hasOfflineFailures = false,
                        isFavorite = isFavorite,
                        isWatchLater = false,
                        readingThemeDark = readingThemeDark,
                        readingFontSizeSp = readingFontSizeSp,
                        shareUseSystem = shareUseSystem,
                        onToggleFavorite = {
                            isFavorite = !isFavorite
                            com.lightningstudio.watchrss.ui.util.showAppToast(
                                this@DouyinDetailActivity,
                                if (isFavorite) "已收藏" else "已取消收藏",
                                android.widget.Toast.LENGTH_SHORT
                            )
                        },
                        onRetryOfflineMedia = {},
                        onSaveReadingProgress = {},
                        onBack = { _, _, _ -> finish() }
                    )
                }
            }
        }
    }

    companion object {
        private const val EXTRA_AWEME_ID = "awemeId"
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_AUTHOR = "author"
        private const val EXTRA_SUMMARY = "summary"
        private const val EXTRA_PLAY_URL = "playUrl"
        private const val EXTRA_COVER_URL = "coverUrl"

        fun createIntent(
            context: Context,
            awemeId: String?,
            title: String?,
            author: String?,
            summary: String?,
            playUrl: String?,
            coverUrl: String?
        ): Intent {
            return Intent(context, DouyinDetailActivity::class.java).apply {
                putExtra(EXTRA_AWEME_ID, awemeId.orEmpty())
                putExtra(EXTRA_TITLE, title.orEmpty())
                putExtra(EXTRA_AUTHOR, author.orEmpty())
                putExtra(EXTRA_SUMMARY, summary.orEmpty())
                putExtra(EXTRA_PLAY_URL, playUrl.orEmpty())
                putExtra(EXTRA_COVER_URL, coverUrl.orEmpty())
            }
        }
    }
}

private fun buildDouyinRssItem(
    awemeId: String,
    title: String,
    author: String,
    summary: String,
    coverUrl: String,
    playUrl: String
): RssItem {
    val safeTitle = title.ifBlank { "抖音视频" }
    val safeAwemeId = awemeId.trim()
    val link = if (safeAwemeId.isNotEmpty()) {
        "https://www.douyin.com/video/$safeAwemeId"
    } else {
        "https://www.douyin.com"
    }
    val itemId = if (safeAwemeId.isNotEmpty()) {
        safeAwemeId.hashCode().toLong().absoluteValue
    } else {
        safeTitle.hashCode().toLong().absoluteValue
    }

    val description = when {
        summary.isNotBlank() -> summary
        author.isNotBlank() -> "作者：$author"
        else -> null
    }

    return RssItem(
        id = itemId,
        channelId = 0L,
        title = safeTitle,
        description = description,
        content = null,
        link = link,
        pubDate = null,
        imageUrl = coverUrl.ifBlank { null },
        audioUrl = null,
        videoUrl = playUrl.ifBlank { null },
        summary = summary.ifBlank { null },
        previewImageUrl = coverUrl.ifBlank { null },
        isRead = true,
        isLiked = false,
        readingProgress = 0f,
        fetchedAt = System.currentTimeMillis()
    )
}

private fun buildDouyinDetailBlocks(
    author: String,
    summary: String,
    playUrl: String,
    coverUrl: String
): List<ContentBlock> {
    val blocks = mutableListOf<ContentBlock>()
    val safeAuthor = author.ifBlank { "未知作者" }

    blocks += ContentBlock.Text("作者：$safeAuthor", ContentTextStyle.SUBTITLE)

    if (summary.isNotBlank()) {
        blocks += ContentBlock.Text(summary, ContentTextStyle.BODY)
    } else {
        blocks += ContentBlock.Text("这是一条来自抖音的信息流内容。", ContentTextStyle.BODY)
    }

    if (playUrl.isNotBlank()) {
        blocks += ContentBlock.Video(
            url = playUrl,
            poster = coverUrl.ifBlank { null }
        )
        blocks += ContentBlock.Text("点击视频卡片即可播放。", ContentTextStyle.QUOTE)
    } else {
        blocks += ContentBlock.Text("当前内容暂无可用视频地址。", ContentTextStyle.QUOTE)
    }

    return blocks
}
