package com.lightningstudio.watchrss

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import com.lightningstudio.watchrss.ui.screen.DEFAULT_SHARE_QR_WIDTH_RATIO
import com.lightningstudio.watchrss.ui.screen.ShareQrScreen
import com.lightningstudio.watchrss.ui.theme.WatchRSSTheme

class ShareQrActivity : BaseWatchActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupSystemBars()

        val link = intent.getStringExtra(EXTRA_LINK).orEmpty().trim()
        val topHint = intent.getStringExtra(EXTRA_TOP_HINT)?.trim().orEmpty().ifBlank { null }
        val qrWidthRatio = intent
            .getFloatExtra(EXTRA_QR_WIDTH_RATIO, DEFAULT_SHARE_QR_WIDTH_RATIO)
            .coerceIn(0.1f, 1f)

        if (link.isEmpty()) {
            com.lightningstudio.watchrss.ui.util.showAppToast(this, "暂无可分享链接", android.widget.Toast.LENGTH_SHORT)
            finish()
            return
        }

        setContent {
            WatchRSSTheme {
                ShareQrScreen(
                    link = link,
                    qrWidthRatio = qrWidthRatio,
                    topHint = topHint,
                    onQrError = {
                        com.lightningstudio.watchrss.ui.util.showAppToast(this, "二维码生成失败", android.widget.Toast.LENGTH_SHORT)
                        finish()
                    },
                    onBack = { finish() }
                )
            }
        }
    }

    companion object {
        private const val EXTRA_TITLE = "extra_title"
        private const val EXTRA_LINK = "extra_link"
        private const val EXTRA_TOP_HINT = "extra_top_hint"
        private const val EXTRA_QR_WIDTH_RATIO = "extra_qr_width_ratio"

        fun createIntent(
            context: Context,
            title: String?,
            link: String,
            topHint: String? = null,
            qrWidthRatio: Float = DEFAULT_SHARE_QR_WIDTH_RATIO
        ): Intent {
            return Intent(context, ShareQrActivity::class.java).apply {
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_LINK, link)
                putExtra(EXTRA_TOP_HINT, topHint)
                putExtra(EXTRA_QR_WIDTH_RATIO, qrWidthRatio)
            }
        }
    }
}
