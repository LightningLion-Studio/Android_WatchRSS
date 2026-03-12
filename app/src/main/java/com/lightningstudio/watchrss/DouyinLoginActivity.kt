package com.lightningstudio.watchrss

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.lightningstudio.watchrss.ui.screen.douyin.DouyinLoginScreen
import com.lightningstudio.watchrss.ui.theme.WatchRSSTheme

class DouyinLoginActivity : BaseWatchActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupSystemBars()

        setContent {
            WatchRSSTheme {
                val baseDensity = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(2f, baseDensity.fontScale)) {
                    DouyinLoginScreen(
                        onLoginComplete = { cookies ->
                            val resultIntent = Intent().apply {
                                putExtra(EXTRA_COOKIES, cookies)
                            }
                            setResult(RESULT_OK, resultIntent)
                            finish()
                        },
                        onBack = { finish() }
                    )
                }
            }
        }
    }

    companion object {
        const val EXTRA_COOKIES = "extra_cookies"

        fun createIntent(context: Context): Intent {
            return Intent(context, DouyinLoginActivity::class.java)
        }
    }
}
