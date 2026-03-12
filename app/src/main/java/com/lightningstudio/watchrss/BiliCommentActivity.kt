package com.lightningstudio.watchrss

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.lightningstudio.watchrss.ui.screen.bili.BiliCommentScreen
import com.lightningstudio.watchrss.ui.theme.WatchRSSTheme
import com.lightningstudio.watchrss.ui.viewmodel.BiliViewModelFactory

class BiliCommentActivity : BaseWatchActivity() {
    private val repository by lazy { (application as WatchRssApplication).container.biliRepository }
    private val rssRepository by lazy { (application as WatchRssApplication).container.rssRepository }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupSystemBars()

        val oid = intent.getStringExtra(EXTRA_OID)?.toLongOrNull() ?: 0L
        val uploaderMid = intent.getStringExtra(EXTRA_UPLOADER_MID)?.toLongOrNull() ?: 0L

        setContent {
            WatchRSSTheme {
                val baseDensity = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(2f, baseDensity.fontScale)) {
                    val factory = BiliViewModelFactory(repository, rssRepository)

                    BiliCommentScreen(
                        oid = oid,
                        uploaderMid = uploaderMid,
                        factory = factory,
                        onNavigateBack = { finish() },
                        onReplyClick = { commentOid, root ->
                            startActivity(
                                BiliReplyDetailActivity.createIntent(this, commentOid, root, uploaderMid)
                            )
                        }
                    )
                }
            }
        }
    }

    companion object {
        private const val EXTRA_OID = "oid"
        private const val EXTRA_UPLOADER_MID = "uploader_mid"

        fun createIntent(context: Context, oid: Long, uploaderMid: Long): Intent {
            return Intent(context, BiliCommentActivity::class.java).apply {
                putExtra(EXTRA_OID, oid.toString())
                putExtra(EXTRA_UPLOADER_MID, uploaderMid.toString())
            }
        }
    }
}
