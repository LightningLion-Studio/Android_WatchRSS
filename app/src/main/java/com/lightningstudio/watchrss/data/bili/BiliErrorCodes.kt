package com.lightningstudio.watchrss.data.bili

import kotlin.math.abs

object BiliErrorCodes {
    const val REQUEST_FAILED = 9001
    const val MISSING_MID = 9002
    const val MISSING_FAVORITE_FOLDER = 9003
    const val QR_REQUEST_FAILED = 9004
    const val COOKIE_INVALID = 9005
    const val PLAY_PARAM_MISSING = 9006
    const val PLAY_URL_EMPTY = 9007
}

fun formatBiliError(code: Int, message: String? = null): String {
    val normalizedMessage = normalizeBiliErrorMessage(message)
    return when (code) {
        BiliErrorCodes.REQUEST_FAILED -> normalizedMessage ?: "网络请求失败"
        BiliErrorCodes.MISSING_MID -> "缺少用户信息"
        BiliErrorCodes.MISSING_FAVORITE_FOLDER -> "未找到收藏夹"
        BiliErrorCodes.QR_REQUEST_FAILED -> normalizedMessage ?: "获取登录二维码失败"
        BiliErrorCodes.COOKIE_INVALID -> normalizedMessage ?: "Cookie 无效"
        BiliErrorCodes.PLAY_PARAM_MISSING -> "缺少播放参数"
        BiliErrorCodes.PLAY_URL_EMPTY -> "暂无可播放地址"
        -101 -> normalizedMessage ?: "登录已失效"
        -111 -> normalizedMessage ?: "缺少 CSRF"
        -352 -> normalizedMessage ?: "触发风控校验"
        -401 -> normalizedMessage ?: "请求未认证"
        -412 -> normalizedMessage ?: "请求被风控拦截"
        86038 -> "二维码已过期"
        86039, 86090 -> "已扫码，请在手机上确认"
        86101 -> "等待扫码"
        else -> normalizedMessage ?: "加载失败(-${abs(code)})"
    }
}

private fun normalizeBiliErrorMessage(message: String?): String? {
    val raw = message?.trim().takeUnless { it.isNullOrEmpty() } ?: return null
    return when {
        raw == "missing_csrf" -> "缺少 CSRF"
        raw.startsWith("missing_cookie:") -> "Cookie 缺少 ${raw.substringAfter(':')}"
        raw == "cookie_incomplete" -> "Cookie 不完整"
        raw == "invalid_json" -> "响应解析失败"
        else -> raw
    }
}
