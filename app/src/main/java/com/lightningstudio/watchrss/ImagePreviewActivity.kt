package com.lightningstudio.watchrss

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import com.lightningstudio.watchrss.ui.screen.rss.ImagePreviewScreen
import com.lightningstudio.watchrss.ui.theme.WatchRSSTheme

class ImagePreviewActivity : BaseWatchActivity() {
    private var panOffsetX = 0f
    private var panRangeX = 0f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupSystemBars()

        val url = intent.getStringExtra(EXTRA_URL).orEmpty().trim()
        val alt = intent.getStringExtra(EXTRA_ALT).orEmpty().trim()

        if (url.isBlank()) {
            com.lightningstudio.watchrss.ui.util.showAppToast(this, "图片地址无效", android.widget.Toast.LENGTH_SHORT)
            finish()
            return
        }

        setContent {
            WatchRSSTheme {
                ImagePreviewScreen(
                    url = url,
                    alt = alt.ifBlank { null },
                    onPanStateChange = { offsetX, rangeX ->
                        panOffsetX = offsetX
                        panRangeX = rangeX
                    },
                    onExit = { finish() }
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
        private const val EXTRA_URL = "extra_url"
        private const val EXTRA_ALT = "extra_alt"

        fun createIntent(context: Context, url: String, alt: String?): Intent {
            return Intent(context, ImagePreviewActivity::class.java).apply {
                putExtra(EXTRA_URL, url)
                putExtra(EXTRA_ALT, alt.orEmpty())
            }
        }
    }
}
