package com.lightningstudio.watchrss

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import com.lightningstudio.watchrss.ui.screen.ShareQrScreen
import com.lightningstudio.watchrss.ui.theme.WatchRSSTheme

class ShareQrActivity : BaseWatchActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupSystemBars()

        val link = intent.getStringExtra(EXTRA_LINK).orEmpty().trim()

        if (link.isEmpty()) {
            com.lightningstudio.watchrss.ui.util.showAppToast(this, "暂无可分享链接", android.widget.Toast.LENGTH_SHORT)
            finish()
            return
        }

        setContent {
            WatchRSSTheme {
                ShareQrScreen(
                    link = link,
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

        fun createIntent(context: Context, title: String?, link: String): Intent {
            return Intent(context, ShareQrActivity::class.java).apply {
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_LINK, link)
            }
        }
    }
}
