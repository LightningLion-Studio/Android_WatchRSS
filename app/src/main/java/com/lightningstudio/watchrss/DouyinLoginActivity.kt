package com.lightningstudio.watchrss

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.lightningstudio.watchrss.ui.screen.douyin.DouyinLoginScreen
import com.lightningstudio.watchrss.ui.theme.WatchRSSTheme
import com.lightningstudio.watchrss.ui.util.getWebViewUnavailableMessage
import com.lightningstudio.watchrss.ui.util.warnWebViewUnavailable

class DouyinLoginActivity : BaseWatchActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupSystemBars()

        val initialWebViewError = getWebViewUnavailableMessage(this)
        setContent {
            WatchRSSTheme {
                val baseDensity = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(2f, baseDensity.fontScale)) {
                    val warningMessage = remember { mutableStateOf(initialWebViewError) }

                    LaunchedEffect(warningMessage.value) {
                        val message = warningMessage.value ?: return@LaunchedEffect
                        warnWebViewUnavailable(this@DouyinLoginActivity, message)
                        warningMessage.value = null
                    }

                    DouyinLoginScreen(
                        initialErrorMessage = initialWebViewError,
                        onWebViewInitFailed = { warningMessage.value = it },
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

        fun open(context: Context): Boolean {
            val unavailableMessage = getWebViewUnavailableMessage(context)
            if (unavailableMessage != null) {
                warnWebViewUnavailable(context, unavailableMessage)
                return false
            }
            context.startActivity(createIntent(context))
            return true
        }

        fun createIntent(context: Context): Intent {
            return Intent(context, DouyinLoginActivity::class.java)
        }
    }
}
