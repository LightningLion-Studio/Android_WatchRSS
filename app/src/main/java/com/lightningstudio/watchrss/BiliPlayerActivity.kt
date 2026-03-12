package com.lightningstudio.watchrss

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.lightningstudio.watchrss.ui.screen.bili.BiliPlayerScreen
import com.lightningstudio.watchrss.ui.theme.WatchRSSTheme
import com.lightningstudio.watchrss.ui.viewmodel.BiliPlayerViewModel
import com.lightningstudio.watchrss.ui.viewmodel.BiliViewModelFactory

class BiliPlayerActivity : BaseWatchActivity() {
    private val repository by lazy { (application as WatchRssApplication).container.biliRepository }
    private val viewModel: BiliPlayerViewModel by viewModels {
        BiliViewModelFactory(repository)
    }
    private var panOffsetX = 0f
    private var panRangeX = 0f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupSystemBars()

        val aid = intent.getStringExtra(EXTRA_AID)?.toLongOrNull()
        val bvid = intent.getStringExtra(EXTRA_BVID)
        val link = repository.shareLink(bvid, aid)

        setContent {
            WatchRSSTheme {
                val baseDensity = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(2f, baseDensity.fontScale)) {
                    val uiState by viewModel.uiState.collectAsState()
                    BiliPlayerScreen(
                        uiState = uiState,
                        onRetry = viewModel::loadPlayUrl,
                        onOpenWeb = {
                            val safeLink = link ?: return@BiliPlayerScreen
                            startActivity(WebViewActivity.createIntent(this, safeLink))
                        },
                        onPanStateChange = { offsetX, rangeX ->
                            panOffsetX = offsetX
                            panRangeX = rangeX
                        }
                    )
                }
            }
        }
    }

    override fun shouldDeferSwipeBack(dx: Float, dy: Float): Boolean {
        if (dx <= 0f) return false
        if (panRangeX <= 0f) return false
        return panOffsetX < panRangeX - 1f
    }

    companion object {
        private const val EXTRA_AID = "aid"
        private const val EXTRA_BVID = "bvid"
        private const val EXTRA_CID = "cid"
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_OWNER = "owner"
        private const val EXTRA_PAGE_TITLE = "pageTitle"

        fun createIntent(
            context: Context,
            aid: Long?,
            bvid: String?,
            cid: Long?,
            title: String? = null,
            owner: String? = null,
            pageTitle: String? = null
        ): Intent {
            return Intent(context, BiliPlayerActivity::class.java).apply {
                putExtra(EXTRA_AID, aid?.toString().orEmpty())
                putExtra(EXTRA_BVID, bvid.orEmpty())
                putExtra(EXTRA_CID, cid?.toString().orEmpty())
                putExtra(EXTRA_TITLE, title.orEmpty())
                putExtra(EXTRA_OWNER, owner.orEmpty())
                putExtra(EXTRA_PAGE_TITLE, pageTitle.orEmpty())
            }
        }
    }
}
