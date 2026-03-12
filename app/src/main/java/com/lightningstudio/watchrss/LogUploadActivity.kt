package com.lightningstudio.watchrss

import android.os.Build
import android.os.Bundle
import android.view.View
import android.webkit.WebView
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.lightningstudio.watchrss.ui.screen.LogUploadScreen
import com.lightningstudio.watchrss.ui.theme.WatchRSSTheme
import com.lightningstudio.watchrss.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LogUploadActivity : BaseWatchActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupSystemBars()

        setContent {
            WatchRSSTheme {
                var logText by remember { mutableStateOf<String?>(null) }
                var isLoading by remember { mutableStateOf(true) }
                var errorMessage by remember { mutableStateOf<String?>(null) }

                LaunchedEffect(Unit) {
                    withContext(Dispatchers.IO) {
                        try {
                            val logs = AppLogger.readLogs()
                            withContext(Dispatchers.Main) {
                                if (logs == null || logs.isEmpty()) {
                                    errorMessage = "暂无日志"
                                } else {
                                    logText = logs
                                }
                                isLoading = false
                            }
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) {
                                errorMessage = "加载日志失败: ${e.message}"
                                isLoading = false
                            }
                        }
                    }
                }

                LogUploadScreen(
                    logText = logText,
                    isLoading = isLoading,
                    errorMessage = errorMessage,
                    onWebViewCreated = { webView ->
                        setupWebView(webView, logText)
                    }
                )
            }
        }
    }

    private fun setupWebView(webView: WebView, logText: String?) {
        // 为了避免闪白
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            // 对于 API 21+，需要先允许背景设为透明
            webView.setLayerType(View.LAYER_TYPE_HARDWARE, null)
            // 设置背景色为透明
            webView.setBackgroundColor(android.graphics.Color.TRANSPARENT)
        }

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
        }

        // 在页面加载前注入日志文本
        webView.webViewClient = object : android.webkit.WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                super.onPageStarted(view, url, favicon)
                if (logText != null && view != null) {
                    // 转义日志文本中的特殊字符
                    val escapedLog = logText
                        .replace("\\", "\\\\")
                        .replace("\"", "\\\"")
                        .replace("\n", "\\n")
                        .replace("\r", "\\r")

                    // 注入日志文本到window.logText
                    view.evaluateJavascript(
                        "window.logText = \"$escapedLog\";",
                        null
                    )
                }
            }
        }

        // 加载本地HTML文件
        webView.loadUrl("file:///android_asset/log_upload/index.html")
    }
}

