package com.lightningstudio.watchrss

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.FileProvider
import com.lightningstudio.watchrss.ui.screen.bili.BiliPlayerScreen
import com.lightningstudio.watchrss.ui.theme.WatchRSSTheme
import com.lightningstudio.watchrss.ui.viewmodel.RssPlayerViewModel
import java.io.File

class RssPlayerActivity : BaseWatchActivity() {
    private val viewModel: RssPlayerViewModel by viewModels()
    private var panOffsetX = 0f
    private var panRangeX = 0f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupSystemBars()

        setContent {
            WatchRSSTheme {
                val uiState by viewModel.uiState.collectAsState()
                BiliPlayerScreen(
                    uiState = uiState,
                    onRetry = viewModel::loadPlayUrl,
                    onOpenWeb = {
                        val link = viewModel.webUrl() ?: return@BiliPlayerScreen
                        if (link.startsWith("http", ignoreCase = true)) {
                            startActivity(WebViewActivity.createIntent(this, link))
                        } else {
                            openExternalLink(this, link)
                        }
                    },
                    onPanStateChange = { offsetX, rangeX ->
                        panOffsetX = offsetX
                        panRangeX = rangeX
                    }
                )
            }
        }
    }

    override fun shouldDeferSwipeBack(dx: Float, dy: Float): Boolean {
        if (dx <= 0f) return false
        if (panRangeX <= 0f) return false
        return panOffsetX < panRangeX - 1f
    }

    companion object {
        fun createIntent(
            context: Context,
            playUrl: String,
            webUrl: String? = null
        ): Intent {
            return Intent(context, RssPlayerActivity::class.java).apply {
                putExtra(RssPlayerViewModel.KEY_PLAY_URL, playUrl)
                putExtra(RssPlayerViewModel.KEY_WEB_URL, webUrl.orEmpty())
            }
        }
    }
}

private fun openExternalLink(context: Context, link: String) {
    val trimmed = link.trim()
    if (trimmed.isEmpty()) return
    val uri = if (trimmed.startsWith("/")) {
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", File(trimmed))
    } else {
        Uri.parse(trimmed)
    }
    val intent = Intent(Intent.ACTION_VIEW, uri)
    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(intent)
}
