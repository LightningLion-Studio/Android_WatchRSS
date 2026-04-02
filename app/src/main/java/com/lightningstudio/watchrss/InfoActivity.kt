package com.lightningstudio.watchrss

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.annotation.RawRes
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.lightningstudio.watchrss.ui.components.WatchCircularProgressIndicator
import com.lightningstudio.watchrss.ui.screen.InfoScreen
import com.lightningstudio.watchrss.ui.theme.WatchRSSTheme

class InfoActivity : BaseWatchActivity() {
    private val settingsRepository by lazy { (application as WatchRssApplication).container.settingsRepository }
    private var isNavigating by mutableStateOf(false)

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            isNavigating = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupSystemBars()

        val title = intent.getStringExtra(EXTRA_TITLE) ?: ""
        val content = resolveContent()

        setContent {
            WatchRSSTheme {
                val baseDensity = LocalDensity.current
                val readingThemeDark by settingsRepository.readingThemeDark.collectAsState(initial = true)
                val readingFontSizeSp by settingsRepository.readingFontSizeSp.collectAsState(initial = 14)

                CompositionLocalProvider(LocalDensity provides Density(2f, baseDensity.fontScale)) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        InfoScreen(
                            title = title,
                            content = content,
                            readingThemeDark = readingThemeDark,
                            readingFontSizeSp = readingFontSizeSp,
                            onBeianClick = {
                                isNavigating = true
                                startActivity(BeianActivity.createIntent(this@InfoActivity))
                            }
                        )

                        if (isNavigating) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                WatchCircularProgressIndicator()
                            }
                        }
                    }
                }
            }
        }
    }

    companion object {
        const val EXTRA_TITLE = "title"
        const val EXTRA_CONTENT = "content"
        const val EXTRA_CONTENT_RAW_RES_ID = "content_raw_res_id"

        fun createIntent(context: Context, title: String, content: String): Intent {
            return Intent(context, InfoActivity::class.java).apply {
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_CONTENT, content)
            }
        }

        fun createIntent(context: Context, title: String, @RawRes contentRawResId: Int): Intent {
            return Intent(context, InfoActivity::class.java).apply {
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_CONTENT_RAW_RES_ID, contentRawResId)
            }
        }
    }

    private fun resolveContent(): String {
        val contentRawResId = intent.getIntExtra(EXTRA_CONTENT_RAW_RES_ID, 0)
        if (contentRawResId != 0) {
            return resources.openRawResource(contentRawResId).bufferedReader().use { it.readText() }
        }
        return intent.getStringExtra(EXTRA_CONTENT) ?: ""
    }
}
