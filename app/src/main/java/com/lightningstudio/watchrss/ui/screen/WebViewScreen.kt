package com.lightningstudio.watchrss.ui.screen

import android.widget.FrameLayout
import android.webkit.WebView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.viewinterop.AndroidView
import com.lightningstudio.watchrss.ui.theme.WatchDimens
import com.lightningstudio.watchrss.ui.widget.ProgressRingView
import com.lightningstudio.watchrss.ui.util.getWebViewUnavailableMessage
import com.lightningstudio.watchrss.util.AppLogger

@Composable
fun WebViewScreen(
    backgroundColor: Color,
    errorMessage: String?,
    onWebViewReady: (WebView) -> Unit,
    onWebViewInitFailed: (String) -> Unit,
    onProgressRingReady: (ProgressRingView) -> Unit
) {
    val safePadding = WatchDimens.watch_safe_padding
    val safeVerticalPadding = WatchDimens.watch_safe_vertical_padding

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
    ) {
        if (!errorMessage.isNullOrBlank()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        start = safePadding,
                        top = safeVerticalPadding,
                        end = safePadding,
                        bottom = safeVerticalPadding
                    ),
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
        } else {
            AndroidView(
                factory = { context ->
                    try {
                        WebView(context).also(onWebViewReady)
                    } catch (throwable: Throwable) {
                        AppLogger.e("WebViewScreen", "Failed to initialize WebView", throwable)
                        val message = getWebViewUnavailableMessage(context)
                            ?: "当前设备无法初始化 WebView，无法打开此页面"
                        FrameLayout(context).apply {
                            post { onWebViewInitFailed(message) }
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        start = safePadding,
                        top = safeVerticalPadding,
                        end = safePadding,
                        bottom = safeVerticalPadding
                    )
            )
            AndroidView(
                factory = { context ->
                    ProgressRingView(context).also(onProgressRingReady)
                },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
