package com.lightningstudio.watchrss.ui.screen

import android.widget.FrameLayout
import android.webkit.WebView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.lightningstudio.watchrss.ui.theme.watchDimensionResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.lightningstudio.watchrss.R
import com.lightningstudio.watchrss.ui.components.WatchCircularProgressIndicator
import com.lightningstudio.watchrss.ui.components.WatchSurface
import com.lightningstudio.watchrss.ui.util.getWebViewUnavailableMessage
import com.lightningstudio.watchrss.util.AppLogger

@Composable
fun LogUploadScreen(
    logText: String?,
    isLoading: Boolean,
    errorMessage: String?,
    onWebViewCreated: (WebView) -> Unit,
    onWebViewInitFailed: (String) -> Unit
) {
    val safePadding = watchDimensionResource(R.dimen.watch_safe_padding)
    val sectionSpacing = 12.dp

    WatchSurface(pureBlack = true) {
        when {
            isLoading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        WatchCircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(sectionSpacing))
                        Text(
                            text = "加载日志中...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
            errorMessage != null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(safePadding),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = errorMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            logText != null -> {
                AndroidView(
                    factory = { context ->
                        try {
                            WebView(context).apply {
                                onWebViewCreated(this)
                            }
                        } catch (throwable: Throwable) {
                            AppLogger.e("LogUploadScreen", "Failed to initialize WebView", throwable)
                            val message = getWebViewUnavailableMessage(context)
                                ?: "当前设备无法初始化 WebView，无法显示日志页面"
                            FrameLayout(context).apply {
                                post { onWebViewInitFailed(message) }
                            }
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}
