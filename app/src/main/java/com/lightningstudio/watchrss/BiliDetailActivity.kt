package com.lightningstudio.watchrss

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.lightningstudio.watchrss.ShareQrActivity
import com.lightningstudio.watchrss.ui.screen.bili.BiliDetailScreen
import com.lightningstudio.watchrss.ui.screen.bili.BiliRssDetailScreen
import com.lightningstudio.watchrss.ui.theme.WatchRSSTheme
import com.lightningstudio.watchrss.ui.viewmodel.BiliDetailViewModel
import com.lightningstudio.watchrss.ui.viewmodel.BiliViewModelFactory

class BiliDetailActivity : BaseWatchActivity() {
    private val repository by lazy { (application as WatchRssApplication).container.biliRepository }
    private val rssRepository by lazy { (application as WatchRssApplication).container.rssRepository }
    private val settingsRepository by lazy { (application as WatchRssApplication).container.settingsRepository }
    private val viewModel: BiliDetailViewModel by viewModels {
        BiliViewModelFactory(repository, rssRepository)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupSystemBars()
        val rssMode = intent.getBooleanExtra(EXTRA_RSS_MODE, false)

        setContent {
            WatchRSSTheme {
                val baseDensity = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(2f, baseDensity.fontScale)) {
                val context = LocalContext.current
                val uiState by viewModel.uiState.collectAsState()
                val shareUseSystem by settingsRepository.shareUseSystem.collectAsState(initial = false)
                val readingThemeDark by settingsRepository.readingThemeDark.collectAsState(initial = true)
                val readingFontSizeSp by settingsRepository.readingFontSizeSp.collectAsState(initial = 14)

                LaunchedEffect(uiState.message) {
                    val message = uiState.message
                    if (!message.isNullOrBlank()) {
                        com.lightningstudio.watchrss.ui.util.showAppToast(context, message, android.widget.Toast.LENGTH_SHORT)
                        viewModel.clearMessage()
                    }
                }

                val onPlay = {
                    val cid = viewModel.selectedCid()
                    val item = uiState.detail?.item
                    val page = viewModel.selectedPage()
                    context.startActivity(
                        BiliPlayerActivity.createIntent(
                            context = context,
                            aid = item?.aid,
                            bvid = item?.bvid,
                            cid = cid,
                            title = item?.title,
                            owner = item?.owner?.name,
                            pageTitle = page?.part
                        )
                    )
                }
                val onShare = {
                    val item = uiState.detail?.item
                    val link = repository.shareLink(item?.bvid, item?.aid)
                    if (shareUseSystem) {
                        shareCurrent(context, item?.title, link)
                    } else {
                        showShareQr(context, item?.title, link)
                    }
                }
                if (rssMode) {
                    BiliRssDetailScreen(
                        uiState = uiState,
                        readingThemeDark = readingThemeDark,
                        readingFontSizeSp = readingFontSizeSp,
                        onPlayClick = onPlay,
                        onFavorite = viewModel::favorite,
                        onShare = onShare
                    )
                } else {
                    BiliDetailScreen(
                        uiState = uiState,
                        onPlayClick = onPlay,
                        onSelectPage = viewModel::selectPage,
                        onLike = viewModel::like,
                        onCoin = viewModel::coin,
                        onFavorite = viewModel::favorite,
                        onShare = onShare,
                        onCommentClick = {
                            val item = uiState.detail?.item
                            val oid = item?.aid ?: 0L
                            val uploaderMid = item?.owner?.mid ?: 0L
                            context.startActivity(
                                BiliCommentActivity.createIntent(context, oid, uploaderMid)
                            )
                        },
                        showCommentEntry = false
                    )
                }
                }
            }
        }
    }

    companion object {
        private const val EXTRA_AID = "aid"
        private const val EXTRA_BVID = "bvid"
        private const val EXTRA_CID = "cid"
        private const val EXTRA_RSS_ITEM_ID = "rssItemId"
        private const val EXTRA_RSS_MODE = "rssMode"

        fun createIntent(
            context: Context,
            aid: Long?,
            bvid: String?,
            cid: Long?,
            rssItemId: Long? = null,
            rssMode: Boolean = false
        ): Intent {
            return Intent(context, BiliDetailActivity::class.java).apply {
                putExtra(EXTRA_AID, aid?.toString().orEmpty())
                putExtra(EXTRA_BVID, bvid.orEmpty())
                putExtra(EXTRA_CID, cid?.toString().orEmpty())
                putExtra(EXTRA_RSS_ITEM_ID, rssItemId?.toString().orEmpty())
                putExtra(EXTRA_RSS_MODE, rssMode)
            }
        }
    }
}

private fun shareCurrent(context: Context, title: String?, link: String?) {
    val safeTitle = title?.trim().orEmpty()
    val safeLink = link?.trim().orEmpty()
    if (safeTitle.isEmpty() && safeLink.isEmpty()) return
    val text = if (safeTitle.isNotEmpty() && safeLink.isNotEmpty()) {
        "$safeTitle\n$safeLink"
    } else if (safeTitle.isNotEmpty()) {
        safeTitle
    } else {
        safeLink
    }
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(intent, "分享"))
}

private fun showShareQr(context: Context, title: String?, link: String?) {
    val safeTitle = title?.trim().orEmpty()
    val safeLink = link?.trim().orEmpty()
    if (safeLink.isEmpty()) {
        com.lightningstudio.watchrss.ui.util.showAppToast(context, "暂无可分享链接", android.widget.Toast.LENGTH_SHORT)
        return
    }
    context.startActivity(ShareQrActivity.createIntent(context, safeTitle, safeLink))
}
