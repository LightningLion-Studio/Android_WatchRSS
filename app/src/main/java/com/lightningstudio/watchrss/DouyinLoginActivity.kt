package com.lightningstudio.watchrss

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.lightningstudio.watchrss.data.network.DefaultInternetAvailabilityMonitor
import com.lightningstudio.watchrss.data.network.InternetAvailabilityStatus
import com.lightningstudio.watchrss.ui.screen.douyin.DouyinLoginBannedScreen
import com.lightningstudio.watchrss.ui.screen.douyin.DouyinLoginCheckingScreen
import com.lightningstudio.watchrss.ui.components.ThirdPartyPlatformNotice
import com.lightningstudio.watchrss.ui.screen.douyin.DouyinLoginScreen
import com.lightningstudio.watchrss.ui.theme.WatchRSSTheme
import com.lightningstudio.watchrss.ui.util.getWebViewUnavailableMessage
import com.lightningstudio.watchrss.ui.util.warnWebViewUnavailable

class DouyinLoginActivity : BaseWatchActivity() {
    private val internetAvailabilityMonitor by lazy {
        DefaultInternetAvailabilityMonitor(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupSystemBars()

        val initialWebViewError = getWebViewUnavailableMessage(this)
        setContent {
            WatchRSSTheme {
                val baseDensity = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(2f, baseDensity.fontScale)) {
                    val internetAvailabilityStatus by internetAvailabilityMonitor.internetAvailability.collectAsState(
                        initial = InternetAvailabilityStatus.Checking
                    )
                    val warningMessage = remember { mutableStateOf(initialWebViewError) }

                    LaunchedEffect(internetAvailabilityStatus, warningMessage.value) {
                        if (internetAvailabilityStatus != InternetAvailabilityStatus.Available) {
                            return@LaunchedEffect
                        }
                        val message = warningMessage.value ?: return@LaunchedEffect
                        warnWebViewUnavailable(this@DouyinLoginActivity, message)
                        warningMessage.value = null
                    }

                    when (internetAvailabilityStatus) {
                        InternetAvailabilityStatus.Checking -> {
                            DouyinLoginCheckingScreen(onBack = { finish() })
                        }

                        InternetAvailabilityStatus.Unavailable -> {
                            DouyinLoginBannedScreen(onBack = { finish() })
                        }

                        InternetAvailabilityStatus.Bluetooth -> {
                            DouyinLoginBannedScreen(
                                status = InternetAvailabilityStatus.Bluetooth,
                                onBack = { finish() }
                            )
                        }

                        InternetAvailabilityStatus.Available -> {
                            Column(modifier = Modifier.fillMaxSize()) {
                                ThirdPartyPlatformNotice(
                                    platform = "抖音",
                                    compact = true,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                                Box(modifier = Modifier.weight(1f).fillMaxSize()) {
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
