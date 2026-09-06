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
import androidx.compose.foundation.layout.weight
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.lightningstudio.watchrss.data.bili.buildBiliExternalSavedItem
import com.lightningstudio.watchrss.data.bili.buildBiliShareLink
import com.lightningstudio.watchrss.data.bili.formatBiliError
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

class BiliVideoActionsActivity : BaseWatchActivity() {
    private val container by lazy { (application as WatchRssApplication).container }
    private val repository by lazy { container.biliRepository }
    private val rssRepository by lazy { container.rssRepository }
    private val settingsRepository by lazy { container.settingsRepository }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupSystemBars()

        val aid = intent.getStringExtra(EXTRA_AID)?.toLongOrNull()
        val bvid = intent.getStringExtra(EXTRA_BVID)?.trim()?.takeIf { it.isNotEmpty() }
        val cid = intent.getStringExtra(EXTRA_CID)?.toLongOrNull()
        val title = intent.getStringExtra(EXTRA_TITLE)
        val owner = intent.getStringExtra(EXTRA_OWNER)
        val coverUrl = intent.getStringExtra(EXTRA_COVER_URL)

        if (aid == null && bvid.isNullOrBlank()) {
            finish()
            return
        }

        setContent {
            WatchRSSTheme {
                val context = LocalContext.current
                val actionScope = rememberCoroutineScope()
                val shareUseSystem by settingsRepository.shareUseSystem.collectAsState(initial = false)
                val shareLink = remember(aid, bvid) { buildBiliShareLink(bvid, aid) }
                val shareTitle = remember(title, aid, bvid) {
                    title?.trim().orEmpty().ifBlank {
                        bvid?.let { "BV号 $it" }
                            ?: aid?.let { "av$it" }
                            ?: "哔哩哔哩视频"
                    }
                }
                val externalSavedItem = remember(aid, bvid, cid, title, owner, coverUrl) {
                    buildBiliExternalSavedItem(
                        aid = aid,
                        bvid = bvid,
                        cid = cid,
                        title = title,
                        owner = owner,
                        coverUrl = coverUrl
                    )
                }
                val useSystemShare = remember(context, shareUseSystem) {
                    shareUseSystem && isSystemShareSettingSupported(context)
                }
                val shareEnabled = if (useSystemShare) {
                    shareTitle.isNotBlank() || !shareLink.isNullOrBlank()
                } else {
                    !shareLink.isNullOrBlank()
                }

                val items = listOf(
                    ActionItem(
                        label = "收藏",
                        enabled = aid != null,
                        onClick = {
                            actionScope.launch {
                                favorite(
                                    aid = aid,
                                    bvid = bvid,
                                    cid = cid,
                                    itemTitle = shareTitle,
                                    externalSavedItem = externalSavedItem
                                )
                            }
                        }
                    ),
                    ActionItem(
                        label = "稍后再看",
                        enabled = aid != null || !bvid.isNullOrBlank(),
                        onClick = {
                            actionScope.launch {
                                watchLater(
                                    aid = aid,
                                    bvid = bvid,
                                    cid = cid,
                                    itemTitle = shareTitle,
                                    externalSavedItem = externalSavedItem
                                )
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
                        platform = "哔哩哔哩",
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

    private suspend fun favorite(
        aid: Long?,
        bvid: String?,
        cid: Long?,
        itemTitle: String,
        externalSavedItem: com.lightningstudio.watchrss.data.rss.ExternalSavedItem
    ) {
        val safeAid = aid ?: run {
            showAppToast(this, "当前内容暂不支持收藏", Toast.LENGTH_SHORT)
            return
        }
        val result = repository.favorite(safeAid, add = true, bvid = bvid)
        if (result.isSuccess) {
            persistLocalSaved(
                externalSavedItem = externalSavedItem,
                saveType = SaveType.FAVORITE,
                aid = safeAid,
                bvid = bvid,
                cid = cid,
                successMessage = "已收藏"
            )
        } else {
            showAppToast(
                this,
                formatBiliError(result.code, result.message).ifBlank { "$itemTitle 收藏失败" },
                Toast.LENGTH_SHORT
            )
        }
    }

    private suspend fun watchLater(
        aid: Long?,
        bvid: String?,
        cid: Long?,
        itemTitle: String,
        externalSavedItem: com.lightningstudio.watchrss.data.rss.ExternalSavedItem
    ) {
        val result = repository.addToView(aid = aid, bvid = bvid)
        if (result.isSuccess) {
            persistLocalSaved(
                externalSavedItem = externalSavedItem,
                saveType = SaveType.WATCH_LATER,
                aid = aid,
                bvid = bvid,
                cid = cid,
                successMessage = "已加入稍后再看"
            )
        } else {
            showAppToast(
                this,
                formatBiliError(result.code, result.message).ifBlank { "$itemTitle 加入稍后再看失败" },
                Toast.LENGTH_SHORT
            )
        }
    }

    private suspend fun persistLocalSaved(
        externalSavedItem: com.lightningstudio.watchrss.data.rss.ExternalSavedItem,
        saveType: SaveType,
        aid: Long?,
        bvid: String?,
        cid: Long?,
        successMessage: String
    ) {
        rssRepository.syncExternalSavedItem(externalSavedItem, saveType, saved = true)
        repository.cachePreviewClip(aid = aid, bvid = bvid, cid = cid)
        showAppToast(this, successMessage, Toast.LENGTH_SHORT)
        finish()
    }

    companion object {
        private const val EXTRA_AID = "aid"
        private const val EXTRA_BVID = "bvid"
        private const val EXTRA_CID = "cid"
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_OWNER = "owner"
        private const val EXTRA_COVER_URL = "coverUrl"

        fun createIntent(
            context: Context,
            aid: Long?,
            bvid: String?,
            cid: Long?,
            title: String?,
            owner: String?,
            coverUrl: String?
        ): Intent {
            return Intent(context, BiliVideoActionsActivity::class.java).apply {
                putExtra(EXTRA_AID, aid?.toString().orEmpty())
                putExtra(EXTRA_BVID, bvid.orEmpty())
                putExtra(EXTRA_CID, cid?.toString().orEmpty())
                putExtra(EXTRA_TITLE, title.orEmpty())
                putExtra(EXTRA_OWNER, owner.orEmpty())
                putExtra(EXTRA_COVER_URL, coverUrl.orEmpty())
            }
        }
    }
}
