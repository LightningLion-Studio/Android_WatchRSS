package com.lightningstudio.watchrss

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.lightningstudio.watchrss.data.douyin.buildDouyinExternalSavedItem
import com.lightningstudio.watchrss.data.douyin.buildDouyinShareLink
import com.lightningstudio.watchrss.data.douyin.containsDouyinSavedItem
import com.lightningstudio.watchrss.data.rss.ExternalSavedItem
import com.lightningstudio.watchrss.data.rss.SaveType
import com.lightningstudio.watchrss.ui.components.ThirdPartyPlatformNotice
import com.lightningstudio.watchrss.ui.screen.ActionDialogScreen
import com.lightningstudio.watchrss.ui.screen.ActionItem
import com.lightningstudio.watchrss.ui.screen.rss.shareCurrent
import com.lightningstudio.watchrss.ui.screen.rss.showShareQr
import com.lightningstudio.watchrss.ui.theme.WatchRSSTheme
import com.lightningstudio.watchrss.ui.util.isSystemShareSettingSupported
import com.lightningstudio.watchrss.ui.util.showAppToast
import kotlinx.coroutines.launch

class DouyinVideoActionsActivity : BaseWatchActivity() {
    private val container by lazy { (application as WatchRssApplication).container }
    private val rssRepository by lazy { container.rssRepository }
    private val settingsRepository by lazy { container.settingsRepository }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupSystemBars()

        val awemeId = intent.getStringExtra(EXTRA_AWEME_ID).orEmpty()
        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        val author = intent.getStringExtra(EXTRA_AUTHOR).orEmpty()
        val playUrl = intent.getStringExtra(EXTRA_PLAY_URL).orEmpty()
        val coverUrl = intent.getStringExtra(EXTRA_COVER_URL).orEmpty()
        val likeCount = intent.getLongExtra(EXTRA_LIKE_COUNT, 0L)

        if (awemeId.isBlank() && playUrl.isBlank()) {
            finish()
            return
        }

        setContent {
            WatchRSSTheme {
                val context = LocalContext.current
                val actionScope = rememberCoroutineScope()
                val shareUseSystem by settingsRepository.shareUseSystem.collectAsState(initial = false)
                val favoriteItems by rssRepository.observeSavedItems(SaveType.FAVORITE)
                    .collectAsState(initial = emptyList())
                val watchLaterItems by rssRepository.observeSavedItems(SaveType.WATCH_LATER)
                    .collectAsState(initial = emptyList())
                val shareLink = remember(awemeId) { buildDouyinShareLink(awemeId) }
                val externalSavedItem = remember(awemeId, title, author, playUrl, coverUrl, likeCount) {
                    buildDouyinExternalSavedItem(
                        awemeId = awemeId,
                        title = title,
                        author = author,
                        playUrl = playUrl,
                        coverUrl = coverUrl,
                        likeCount = likeCount
                    )
                }
                val useSystemShare = remember(context, shareUseSystem) {
                    shareUseSystem && isSystemShareSettingSupported(context)
                }
                val isFavorite = remember(favoriteItems, awemeId, shareLink, playUrl) {
                    containsDouyinSavedItem(
                        items = favoriteItems,
                        awemeId = awemeId,
                        link = shareLink,
                        playUrl = playUrl
                    )
                }
                val isWatchLater = remember(watchLaterItems, awemeId, shareLink, playUrl) {
                    containsDouyinSavedItem(
                        items = watchLaterItems,
                        awemeId = awemeId,
                        link = shareLink,
                        playUrl = playUrl
                    )
                }

                val favoriteLabel = if (isFavorite) "取消收藏" else "收藏"
                val watchLaterLabel = if (isWatchLater) "取消稍后再看" else "稍后再看"
                val shareTitle = title.trim().ifBlank { "抖音视频" }
                val shareEnabled = if (useSystemShare) {
                    shareTitle.isNotBlank() || !shareLink.isNullOrBlank()
                } else {
                    !shareLink.isNullOrBlank()
                }

                val items = listOf(
                    ActionItem(
                        label = favoriteLabel,
                        enabled = externalSavedItem != null,
                        onClick = {
                            externalSavedItem?.let { target ->
                                actionScope.launch {
                                    toggleSaved(
                                        item = target,
                                        saveType = SaveType.FAVORITE,
                                        currentlySaved = isFavorite,
                                        successMessage = if (isFavorite) "已取消收藏" else "已收藏"
                                    )
                                }
                            }
                        }
                    ),
                    ActionItem(
                        label = watchLaterLabel,
                        enabled = externalSavedItem != null,
                        onClick = {
                            externalSavedItem?.let { target ->
                                actionScope.launch {
                                    toggleSaved(
                                        item = target,
                                        saveType = SaveType.WATCH_LATER,
                                        currentlySaved = isWatchLater,
                                        successMessage = if (isWatchLater) "已从稍后再看移除" else "已加入稍后再看"
                                    )
                                }
                            }
                        }
                    ),
                    ActionItem(
                        label = "分享",
                        enabled = shareEnabled,
                        onClick = {
                            if (useSystemShare) {
                                shareCurrent(context, shareTitle, shareLink)
                            } else {
                                showShareQr(context, shareTitle, shareLink)
                            }
                            finish()
                        }
                    ),
                    ActionItem(
                        label = "取消",
                        onClick = { finish() }
                    )
                )

                Column(modifier = Modifier.fillMaxSize()) {
                    ThirdPartyPlatformNotice(
                        platform = "抖音",
                        compact = true,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                    Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                        ActionDialogScreen(
                            items = items,
                            extraTopPadding = 4.dp
                        )
                    }
                }
            }
        }
    }

    private suspend fun toggleSaved(
        item: ExternalSavedItem,
        saveType: SaveType,
        currentlySaved: Boolean,
        successMessage: String
    ) {
        val result = rssRepository.syncExternalSavedItem(item, saveType, saved = !currentlySaved)
        if (result.isSuccess) {
            showAppToast(this, successMessage, Toast.LENGTH_SHORT)
            finish()
        } else {
            showAppToast(
                this,
                result.exceptionOrNull()?.message ?: "操作失败",
                Toast.LENGTH_SHORT
            )
        }
    }

    companion object {
        private const val EXTRA_AWEME_ID = "awemeId"
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_AUTHOR = "author"
        private const val EXTRA_PLAY_URL = "playUrl"
        private const val EXTRA_COVER_URL = "coverUrl"
        private const val EXTRA_LIKE_COUNT = "likeCount"

        fun createIntent(
            context: Context,
            awemeId: String,
            title: String?,
            author: String?,
            playUrl: String?,
            coverUrl: String?,
            likeCount: Long
        ): Intent {
            return Intent(context, DouyinVideoActionsActivity::class.java).apply {
                putExtra(EXTRA_AWEME_ID, awemeId)
                putExtra(EXTRA_TITLE, title.orEmpty())
                putExtra(EXTRA_AUTHOR, author.orEmpty())
                putExtra(EXTRA_PLAY_URL, playUrl.orEmpty())
                putExtra(EXTRA_COVER_URL, coverUrl.orEmpty())
                putExtra(EXTRA_LIKE_COUNT, likeCount)
            }
        }
    }
}
