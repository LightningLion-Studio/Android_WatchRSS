package com.lightningstudio.watchrss.ui.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

const val OFFLINE_USER_MESSAGE = "未连接网络"

private val EXPLICIT_OFFLINE_MARKERS = listOf(
    "未连接网络",
    "网络不可用",
    "无网络",
    "网络已断开",
    "无法找到服务器",
    "检查网络连接",
    "network unavailable",
    "network is unreachable",
    "unable to resolve host",
    "no address associated with hostname",
    "host lookup",
    "failed to connect",
    "connection refused"
)

private val GENERIC_NETWORK_FAILURE_MARKERS = listOf(
    "网络请求失败",
    "网络读写失败",
    "连接服务器失败",
    "连接超时",
    "请求超时",
    "安全连接失败",
    "获取登录二维码失败",
    "加载失败",
    "刷新失败",
    "请求失败",
    "timeout",
    "timed out",
    "network error",
    "io error",
    "ssl"
)

fun normalizeUserFacingMessage(
    context: Context,
    message: CharSequence?
): CharSequence? {
    val safeMessage = message?.toString()?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    return if (shouldUseOfflineMessage(context, safeMessage)) {
        OFFLINE_USER_MESSAGE
    } else {
        safeMessage
    }
}

fun offlineToastMessageOrNull(
    context: Context,
    message: CharSequence?
): String? {
    return normalizeUserFacingMessage(context, message)
        ?.toString()
        ?.takeIf { it == OFFLINE_USER_MESSAGE }
}

private fun shouldUseOfflineMessage(context: Context, message: String): Boolean {
    val normalized = message.lowercase()
    if (EXPLICIT_OFFLINE_MARKERS.any(normalized::contains)) {
        return true
    }
    return GENERIC_NETWORK_FAILURE_MARKERS.any(normalized::contains) &&
        !hasValidatedInternetConnection(context)
}

fun hasValidatedInternetConnection(context: Context): Boolean {
    val connectivityManager =
        context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
    val activeNetwork = connectivityManager.activeNetwork ?: return false
    val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
    return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
        capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
}
