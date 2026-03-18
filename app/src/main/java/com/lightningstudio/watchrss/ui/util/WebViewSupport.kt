package com.lightningstudio.watchrss.ui.util

import android.content.Context
import android.widget.Toast
import androidx.webkit.WebViewCompat
import com.lightningstudio.watchrss.util.AppLogger

private const val WEB_VIEW_SUPPORT_TAG = "WebViewSupport"
private const val WEB_VIEW_UNAVAILABLE_MESSAGE =
    "系统未安装或已禁用 WebView 组件，无法打开此页面，请先安装或启用 Android System WebView 后重试"

fun getWebViewUnavailableMessage(context: Context): String? {
    return try {
        val provider = WebViewCompat.getCurrentWebViewPackage(context)
        if (provider == null) {
            AppLogger.w(WEB_VIEW_SUPPORT_TAG, "No WebView provider available on device")
            WEB_VIEW_UNAVAILABLE_MESSAGE
        } else {
            null
        }
    } catch (throwable: Throwable) {
        AppLogger.e(WEB_VIEW_SUPPORT_TAG, "Failed to resolve WebView provider", throwable)
        WEB_VIEW_UNAVAILABLE_MESSAGE
    }
}

fun warnWebViewUnavailable(
    context: Context,
    message: String = WEB_VIEW_UNAVAILABLE_MESSAGE
) {
    showAppToast(context, message, Toast.LENGTH_LONG)
}
