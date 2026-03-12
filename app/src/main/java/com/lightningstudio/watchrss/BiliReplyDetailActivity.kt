package com.lightningstudio.watchrss

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.lightningstudio.watchrss.ui.screen.bili.BiliReplyDetailScreen
import com.lightningstudio.watchrss.ui.theme.WatchRSSTheme
import com.lightningstudio.watchrss.ui.viewmodel.BiliViewModelFactory

class BiliReplyDetailActivity : BaseWatchActivity() {
    private val repository by lazy { (application as WatchRssApplication).container.biliRepository }
    private val rssRepository by lazy { (application as WatchRssApplication).container.rssRepository }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupSystemBars()

        val oid = intent.getStringExtra(EXTRA_OID)?.toLongOrNull() ?: 0L
        val root = intent.getStringExtra(EXTRA_ROOT)?.toLongOrNull() ?: 0L
        val uploaderMid = intent.getStringExtra(EXTRA_UPLOADER_MID)?.toLongOrNull() ?: 0L

        setContent {
            WatchRSSTheme {
                val baseDensity = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(2f, baseDensity.fontScale)) {
                    val factory = BiliViewModelFactory(repository, rssRepository)

                    BiliReplyDetailScreen(
                        oid = oid,
                        root = root,
                        uploaderMid = uploaderMid,
                        factory = factory,
                        onNavigateBack = { finish() }
                    )
                }
            }
        }
    }

    companion object {
        private const val EXTRA_OID = "oid"
        private const val EXTRA_ROOT = "root"
        private const val EXTRA_UPLOADER_MID = "uploader_mid"

        fun createIntent(context: Context, oid: Long, root: Long, uploaderMid: Long): Intent {
            return Intent(context, BiliReplyDetailActivity::class.java).apply {
                putExtra(EXTRA_OID, oid.toString())
                putExtra(EXTRA_ROOT, root.toString())
                putExtra(EXTRA_UPLOADER_MID, uploaderMid.toString())
            }
        }
    }
}
